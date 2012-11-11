/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.vocapia.listener;

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;
import static org.nuxeo.ecm.platform.video.VideoConstants.VIDEO_CHANGED_PROPERTY;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.vocapia.service.TranscriptionService;

/**
 * Core event listener to compute / update the audio transcription of a Video
 * document
 * 
 * @author ogrisel
 */
public class TranscriptionListener implements PostCommitEventListener {

    public static final Log log = LogFactory.getLog(TranscriptionListener.class);

    @Override
    public void handleEvent(EventBundle events) throws ClientException {
        if (!events.containsEventName(DOCUMENT_CREATED)
                && !events.containsEventName(DOCUMENT_UPDATED)) {
            return;
        }

        for (Event event : events) {
            if (DOCUMENT_CREATED.equals(event.getName())
                    || DOCUMENT_UPDATED.equals(event.getName())) {
                handleEvent(event);
            }
        }
    }

    public void handleEvent(Event event) throws ClientException {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }
        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        if (ctx.hasProperty(VIDEO_CHANGED_PROPERTY)) {
            TranscriptionService transcriptionService = Framework.getLocalService(TranscriptionService.class);
            transcriptionService.launchTranscription(new DocumentLocationImpl(
                    doc));
        }
    }
}
