package org.nuxeo.vocapia.service;

import static org.nuxeo.ecm.core.work.api.Work.State.FAILED;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.UnmarshalException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.nuxeo.common.collections.ScopeType;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventCategories;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.streaming.FileSource;
import org.nuxeo.runtime.services.streaming.StreamSource;
import org.nuxeo.vocapia.service.xml.AudioDoc;

public class TranscriptionWork extends AbstractWork {

    public static final String EVENT_TRANSCRIPTION_COMPLETE = "TranscriptionComplete";

    public static final String HAS_SPEECH_TRANSCRIPTION = "HasSpeechTranscription";

    private static final Log log = LogFactory.getLog(TranscriptionWork.class);

    public static final String CATEGORY_SPEECH_TRANSCRIPTION = "speech_transcription";

    protected static final String DC_LANGUAGE = "dc:language";

    protected static final String TRANS_SECTIONS = "trans:sections";

    protected final DocumentLocation docLoc;

    protected final String blobPropertyPath;

    protected final HttpClient httpClient;

    protected final Map<String, String> shortToLongLangCodes;

    protected final Map<String, String> longToShortLangCodes;

    protected URI serviceUrl;

    protected String username;

    protected String password;

    public TranscriptionWork(DocumentLocation docLoc, String blobPropertyPath,
            HttpClient httpClient, URI serviceUrl, String username,
            String password, Map<String, String> shortToLongLangCodes,
            Map<String, String> longToShortLangCodes) {
        this.docLoc = docLoc;
        this.blobPropertyPath = blobPropertyPath;
        this.httpClient = httpClient;
        this.serviceUrl = serviceUrl;
        this.username = username;
        this.password = password;
        this.shortToLongLangCodes = shortToLongLangCodes;
        this.longToShortLangCodes = longToShortLangCodes;
    }

    @Override
    public String getTitle() {
        return String.format("Speech Transcription for: %s:%s:%s",
                docLoc.getServerName(), docLoc.getDocRef(), blobPropertyPath);
    }

    @Override
    public void work() throws Exception {
        setProgress(Progress.PROGRESS_INDETERMINATE);
        Object[] properties = getSourceDocumentLanguageAndMedia();
        String language = (String) properties[0];
        Blob sourceMedia = (Blob) properties[1];
        if (isSuspending()) {
            return;
        }
        if (sourceMedia == null) {
            log.warn(String.format(
                    "Speech transcription aborted: no media found for property '%s' on document '%s'",
                    blobPropertyPath, docLoc));
            state = FAILED;
            return;
        }

        // Release the current transaction as the following calls will be very
        // long and won't need access to any persistent transactional resources
        commitOrRollbackTransaction();

        Blob audioContent = sourceMedia;
        if (audioContent.getFilename() == null
                || !audioContent.getFilename().endsWith(".mp3")) {
            // Convert the soundtrack of the source media as MP3 for submission
            // to the transcription service
            setStatus("soundtrack_extraction");
            audioContent = extractSoundTrack(sourceMedia);
        }
        if (isSuspending()) {
            return;
        }
        String detectedLanguage = null;
        Transcription transcription = null;
        try {
            // If the user has not set the language manually, use the service to
            // detect it
            if (language == null || language.trim().isEmpty()) {
                setStatus("language_detection");
                detectedLanguage = detectLanguage(audioContent);
                language = detectedLanguage;
                if (isSuspending()) {
                    return;
                }
            }
            if (language == null || language.trim().isEmpty()) {
                log.warn("Could not detect the language for "
                        + sourceMedia.getFilename()
                        + ": skipping transcription.");
                state = FAILED;
                return;
            }
            // Perform the actual transcription
            setStatus("speech_transcription");
            transcription = performTranscription(audioContent, language);
            if (transcription == null) {
                log.warn("Could not find a transcription model for language '"
                        + language + "' for media: " + sourceMedia.getFilename());
            }
            if (isSuspending()) {
                return;
            }
        } finally {
            if (audioContent != sourceMedia) {
                FileUtils.deleteQuietly(getBackingFile(audioContent));
            }
        }

        // Save the results back on the document in a new, short-lived
        // transaction
        startTransaction();
        setStatus("saving_results");
        saveResults(detectedLanguage, transcription);
    }

    protected Object[] getSourceDocumentLanguageAndMedia()
            throws ClientException {
        final Object[] returnValues = new Object[2];
        final DocumentRef docRef = docLoc.getDocRef();
        String repositoryName = docLoc.getServerName();
        new UnrestrictedSessionRunner(repositoryName) {
            @Override
            public void run() throws ClientException {
                if (session.exists(docRef)) {
                    DocumentModel doc = session.getDocument(docRef);
                    returnValues[0] = doc.getPropertyValue(DC_LANGUAGE);
                    returnValues[1] = doc.getPropertyValue(blobPropertyPath);
                }
            }
        }.runUnrestricted();
        return returnValues;
    }

    protected Blob extractSoundTrack(Blob sourceMedia) throws IOException,
            ClientException {
        ConversionService conversionService = Framework.getLocalService(ConversionService.class);
        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        BlobHolder blobHolder = new SimpleBlobHolder(sourceMedia);
        BlobHolder result = conversionService.convert("extractSoundAsMp3",
                blobHolder, parameters);
        return result.getBlob().persist();
    }

    protected String detectLanguage(Blob mediaContent) {
        AudioDoc result = callService("vrbs_lid", null, mediaContent);
        String longLanguage = result.getLanguage();
        if (longLanguage == null) {
            log.warn("Failed to detect a language on: "
                    + mediaContent.getFilename());
            return null;
        }
        if (!longToShortLangCodes.containsKey(longLanguage)) {
            log.warn("Language detector detected an unsupported language: "
                    + longLanguage + " on " + mediaContent.getFilename());
            return null;
        }
        return longToShortLangCodes.get(longLanguage);
    }

    protected Transcription performTranscription(Blob mediaContent,
            String language) {
        String modelName = shortToLongLangCodes.get(language);
        if (modelName == null) {
            return null;
        }
        AudioDoc result = callService("vrbs_trans", modelName, mediaContent);
        return result.asTranscription();
    }

    protected AudioDoc callService(String method, String model,
            Blob audioContent) {
        String url = String.format("%s?method=%s&audiofile=soundtrack.mp3",
                serviceUrl, method);
        if (model != null) {
            url += String.format("&model=%s", model);
        }
        HttpPut request = new HttpPut(url);
        request.getParams().setBooleanParameter(
                "http.protocol.expect-continue", true);

        try {
            if (username != null && password != null) {
                String credentials = Base64.encodeBase64String((username + ":" + password).getBytes(Charset.forName("UTF-8")));
                // trim is necessary to remove the trailing CRLF appended by
                // Base64.encodeBase64String for some reason...
                request.setHeader("Authorization",
                        "Basic " + credentials.trim());
            }
            request.setHeader("Accept", "*/*");
            request.setHeader("Content-Type", audioContent.getMimeType());
            request.setEntity(new ByteArrayEntity(audioContent.getByteArray()));
            HttpResponse response = httpClient.execute(request);
            InputStream content = response.getEntity().getContent();
            String body = IOUtils.toString(content);
            content.close();
            if (response.getStatusLine().getStatusCode() == 200) {
                try {
                    return AudioDoc.readFrom(body);
                } catch (UnmarshalException e) {
                    String errorMsg = String.format(
                            "Invalid response from '%s': %s\n %s", url,
                            response.getStatusLine().toString(), body);
                    throw new IOException(errorMsg);
                }
            } else {
                String errorMsg = String.format(
                        "Unexpected response from '%s': %s\n %s", url,
                        response.getStatusLine().toString(), body);
                throw new IOException(errorMsg);
            }
        } catch (Exception e) {
            request.abort();
            throw new RuntimeException(String.format(
                    "Error connecting to '%s': %s", url, e.getMessage()), e);
        }
    }

    protected File getBackingFile(Blob audioContent) {
        // Delete temporary extracted file
        if (audioContent instanceof StreamingBlob) {
            StreamSource source = ((StreamingBlob) audioContent).getStreamSource();
            if (source instanceof FileSource) {
                return ((FileSource) source).getFile();
            }
        } else if (audioContent instanceof FileBlob) {
            return ((FileBlob) audioContent).getFile();
        }
        return null;
    }

    protected void saveResults(final String detectedLanguage,
            final Transcription transcription) throws ClientException {
        final DocumentRef docRef = docLoc.getDocRef();
        String repositoryName = docLoc.getServerName();
        new UnrestrictedSessionRunner(repositoryName) {
            @Override
            public void run() throws ClientException {
                if (session.exists(docRef)) {
                    DocumentModel doc = session.getDocument(docRef);
                    if (detectedLanguage != null) {
                        doc.setPropertyValue(DC_LANGUAGE, detectedLanguage);
                    }
                    if (transcription != null) {
                        if (!doc.hasFacet(HAS_SPEECH_TRANSCRIPTION)) {
                            doc.addFacet(HAS_SPEECH_TRANSCRIPTION);
                        }
                        doc.setPropertyValue(TRANS_SECTIONS,
                                transcription.getSections());

                        // Temporary fix to make it possible to do a semantic
                        // analysis of the transcription.
                        if (!doc.hasFacet(FacetNames.HAS_RELATED_TEXT)) {
                            doc.addFacet(FacetNames.HAS_RELATED_TEXT);
                        }
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> resources = doc.getProperty(
                                "relatedtext:relatedtextresources").getValue(
                                List.class);
                        boolean updated = false;
                        for (Map<String, String> relatedResource : resources) {
                            if (relatedResource.get("relatedtextid").equals(
                                    "transcription")) {
                                relatedResource.put("relatedtext",
                                        transcription.getText());
                                updated = true;
                                break;
                            }
                        }
                        if (!updated) {
                            Map<String, String> m = new HashMap<String, String>();
                            m.put("relatedtextid", "transcription");
                            m.put("relatedtext", transcription.getText());
                            resources.add(m);
                        }
                        doc.setPropertyValue(
                                "relatedtext:relatedtextresources",
                                (Serializable) resources);
                    }
                    String comment = "Automated transcription from language: "
                            + doc.getPropertyValue(DC_LANGUAGE);
                    doc.getContextData().putScopedValue(ScopeType.REQUEST,
                            "comment", comment);
                    session.saveDocument(doc);

                    // Notify transcription completion to make it possible to
                    // chain processing.
                    DocumentEventContext ctx = new DocumentEventContext(
                            session, getPrincipal(), doc);
                    ctx.setProperty(CoreEventConstants.REPOSITORY_NAME,
                            repositoryName);
                    ctx.setProperty(CoreEventConstants.SESSION_ID,
                            session.getSessionId());
                    ctx.setProperty("category",
                            DocumentEventCategories.EVENT_DOCUMENT_CATEGORY);
                    Event event = ctx.newEvent(EVENT_TRANSCRIPTION_COMPLETE);
                    EventService eventService = Framework.getLocalService(EventService.class);
                    eventService.fireEvent(event);
                }
            }
        }.runUnrestricted();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((blobPropertyPath == null) ? 0 : blobPropertyPath.hashCode());
        result = prime * result + ((docLoc == null) ? 0 : docLoc.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TranscriptionWork other = (TranscriptionWork) obj;
        if (blobPropertyPath == null) {
            if (other.blobPropertyPath != null)
                return false;
        } else if (!blobPropertyPath.equals(other.blobPropertyPath))
            return false;
        if (docLoc == null) {
            if (other.docLoc != null)
                return false;
        } else if (!docLoc.equals(other.docLoc))
            return false;
        return true;
    }

}
