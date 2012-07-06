/**
 * 
 */

package org.nuxeo.vocapia.service;

import java.util.concurrent.TimeUnit;

import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;


/**
* Shedule the conversion of video and audio files and monitor progress.
 */
public class TranscriptionService extends DefaultComponent {

    @Override
    public void activate(ComponentContext context) throws Exception {
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        if (workManager != null) {
            workManager.shutdownQueue(
                    workManager.getCategoryQueueId(TranscriptionWork.CATEGORY_SPEECH_TRANSCRIPTION),
                    10, TimeUnit.SECONDS);
        }
    }
}
