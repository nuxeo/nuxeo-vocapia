package org.nuxeo.vocapia.seam;

import java.io.Serializable;
import java.util.Map;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.core.Interpolator;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.invalidations.AutomaticDocumentBasedInvalidation;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.vocapia.service.TranscriptionService;
import org.nuxeo.vocapia.service.TranscriptionStatus;

/**
 * Launch and monitor progress of video / audio files transcriptions.
 */
@Name("transcriptionActions")
@Scope(ScopeType.EVENT)
@AutomaticDocumentBasedInvalidation
public class TranscriptionActions implements Serializable {

    private static final long serialVersionUID = 1L;

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    @In(create = true)
    protected NavigationContext navigationContext;

    @In(create = true)
    private Map<String, String> messages;

    
    public boolean canLaunchTranscription(DocumentModel doc) throws PropertyException, ClientException {
        String language = (String) doc.getPropertyValue("dc:language");
        if (language == null || language.trim().isEmpty()) {
            // Let the service detect the language of the document
            return true;
        }
        TranscriptionService service = Framework.getLocalService(TranscriptionService.class);
        return service.isSupportedLangage(language);
    }
    
    public void launchTranscription(DocumentModel doc) {
        TranscriptionService service = Framework.getLocalService(TranscriptionService.class);
        service.launchTranscription(new DocumentLocationImpl(doc));
    }

    public TranscriptionStatus getTranscriptionStatus(DocumentModel doc) {
        TranscriptionService service = Framework.getLocalService(TranscriptionService.class);
        if (service == null) {
            return null;
        }
        return service.getTranscriptionStatus(new DocumentLocationImpl(doc));
    }

    public String getStatusMessageFor(TranscriptionStatus status) {
        if (status == null) {
            return "";
        }
        String message = messages.get("status.transcription." + status.status);
        return Interpolator.instance().interpolate(message,
                status.position, status.queueSize);
    }

}
