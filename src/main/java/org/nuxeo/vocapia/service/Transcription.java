package org.nuxeo.vocapia.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.PropertyException;

/**
 * Data Transfer Object to manipulate the extracted transcriptions from the service.
 */
public class Transcription {

    public static final String TIMECODE_START = "timecode_start";

    public static final String TIMECODE_STOP = "timecode_stop";

    public static final String TEXT = "text";

    public static final String SPEAKER_ID = "speaker_id";

    protected ArrayList<Map<String, Object>> sections = new ArrayList<Map<String, Object>>();

    public Transcription() {
        // empty transcriptions for incremental building from service outcome
    }

    public Transcription appendSection(int timecodeStart, int timecodeStop,
            String text, String speakerId) {
        Map<String, Object> section = new HashMap<String, Object>();
        section.put(TIMECODE_START, Integer.valueOf(timecodeStart));
        section.put(TIMECODE_STOP, Integer.valueOf(timecodeStop));
        section.put(TEXT, text);
        section.put(SPEAKER_ID, speakerId);
        sections.add(section);
        return this;
    }

    public Transcription(ArrayList<Map<String, Object>> sections) {
        this.sections.addAll(sections);
    }

    public ArrayList<Map<String, Object>> getSections() {
        return sections;
    }

    public static Transcription emptyTranscription() {
        return new Transcription();
    }

    public static Transcription fromSections(
            ArrayList<Map<String, Object>> sections) {
        return new Transcription(sections);
    }

    public static Transcription fromTranscribedDocument(DocumentModel doc)
            throws PropertyException, ClientException {
        @SuppressWarnings("unchecked")
        ArrayList<Map<String, Object>> sections = (ArrayList<Map<String, Object>>) doc.getPropertyValue(TranscriptionWork.TRANS_SECTIONS);
        return new Transcription(sections);
    }

    public void updateDocument(DocumentModel doc) throws PropertyException,
            ClientException {
        doc.setPropertyValue(TranscriptionWork.TRANS_SECTIONS, sections);
    }

}
