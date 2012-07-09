package org.nuxeo.vocapia.service;

import static org.nuxeo.ecm.core.work.api.Work.State.FAILED;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.HttpClient;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.impl.blob.StreamingBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.streaming.FileSource;
import org.nuxeo.runtime.services.streaming.StreamSource;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class TranscriptionWork extends AbstractWork {

    public static final String HAS_SPEECH_TRANSCRIPTION = "HasSpeechTranscription";

    private static final Log log = LogFactory.getLog(TranscriptionWork.class);

    public static final String CATEGORY_SPEECH_TRANSCRIPTION = "speech_transcription";

    protected static final String DC_LANGUAGE = "dc:language";

    protected static final String TRANS_SECTIONS = "trans:sections";

    protected final DocumentLocation docLoc;

    protected final String blobPropertyPath;
    
    protected final Map<String, String> shortToLongLangCodes = new LinkedHashMap<String, String>();
    
    protected final Map<String, String> longToShortLangCodes = new LinkedHashMap<String, String>();

    public TranscriptionWork(DocumentLocation docLoc, String blobPropertyPath, HttpClient httpClient) {
        this.docLoc = docLoc;
        this.blobPropertyPath = blobPropertyPath;
        
        shortToLongLangCodes.put("ar", "ara");
        shortToLongLangCodes.put("en", "eng");
        shortToLongLangCodes.put("fr", "fre");
        updateLongToShortLangCodes();
    }

    protected void updateLongToShortLangCodes() {
        longToShortLangCodes.clear();
        for (Map.Entry<String, String> entry: shortToLongLangCodes.entrySet()) {
            longToShortLangCodes.put(entry.getValue(), entry.getKey());
        }
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
        if (isTransactional()) {
            TransactionHelper.commitOrRollbackTransaction();
        }

        // Convert the soundtrack of the source media as MP3 for submission to
        // the transcription service
        setStatus("soundtrack_extraction");
        Blob mp3 = extractSoundTrack(sourceMedia);
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
                detectedLanguage = detectLanguage(mp3);
                language = detectedLanguage;
                if (isSuspending()) {
                    return;
                }
            }
            // Perform the actual transcription
            setStatus("speech_transcription");
            transcription = performTranscription(mp3, language);
            if (isSuspending()) {
                return;
            }
        } finally {
            // Delete temporary extracted file
            if (mp3 instanceof StreamingBlob) {
                StreamSource source = ((StreamingBlob) mp3).getStreamSource();
                if (source instanceof FileSource) {
                    FileUtils.deleteQuietly(((FileSource) source).getFile());
                }
            } else if (mp3 instanceof FileBlob) {
                FileUtils.deleteQuietly(((FileBlob) mp3).getFile());
            }
        }

        // Save the results back on the document in a new, short-lived
        // transaction
        if (isTransactional()) {
            TransactionHelper.startTransaction();
        }
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

    protected String detectLanguage(Blob mp3) {
        // TODO
        return longToShortLangCodes.get("ara");
    }

    protected Transcription performTranscription(Blob mp3, String language) {
        return Transcription.emptyTranscription();
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
                    if (!doc.hasFacet(HAS_SPEECH_TRANSCRIPTION)) {
                        doc.addFacet(HAS_SPEECH_TRANSCRIPTION);
                    }
                    doc.setPropertyValue(TRANS_SECTIONS,
                            transcription.getSections());
                    session.saveDocument(doc);
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
