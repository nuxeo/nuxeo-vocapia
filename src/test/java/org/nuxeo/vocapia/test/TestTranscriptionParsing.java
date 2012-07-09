package org.nuxeo.vocapia.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.bind.JAXBException;

import org.junit.Test;
import org.nuxeo.vocapia.service.Transcription;
import org.nuxeo.vocapia.service.xml.AudioDoc;
import org.nuxeo.vocapia.service.xml.Segment;

public class TestTranscriptionParsing {

    @Test
    public void testArabicTranscription() throws JAXBException {
        InputStream stream = getClass().getResourceAsStream(
                "/afp_ar_news_1_trans.xml");
        AudioDoc parsedDoc = AudioDoc.readFrom(stream);
        ArrayList<Segment> segments = parsedDoc.getSegments();
        assertEquals(segments.size(), 7);
        assertEquals(segments.get(1).getStartTime(), Double.valueOf(3.37));
        assertEquals(segments.get(1).getEndTime(), Double.valueOf(18.66));
        assertEquals(segments.get(1).getSpeakerId(), "FS1");
        assertFalse(segments.get(1).getText().isEmpty());

        Transcription transcription = parsedDoc.asTranscription();
        assertEquals(transcription.getSections().size(), 7);
    }

    @Test
    public void testEnglishTranscription() throws JAXBException {
        InputStream stream = getClass().getResourceAsStream(
                "/fake_english_transcription.xml");
        AudioDoc parsedDoc = AudioDoc.readFrom(stream);
        ArrayList<Segment> segments = parsedDoc.getSegments();
        assertEquals(segments.size(), 1);
        assertEquals(segments.get(0).getStartTime(), Double.valueOf(3.37));
        assertEquals(segments.get(0).getEndTime(), Double.valueOf(18.66));
        assertEquals(segments.get(0).getSpeakerId(), "FS1");
        assertEquals(segments.get(0).getText(),
                "This is a test, with inadequate punctuation. Indeed.");

        Transcription transcription = parsedDoc.asTranscription();
        assertEquals(transcription.getSections().size(), 1);
    }

    
    @Test
    public void testLanguageIdOutput() throws JAXBException {
        InputStream stream = getClass().getResourceAsStream(
                "/afp_ar_news_1_lid.xml");
        AudioDoc parsedDoc = AudioDoc.readFrom(stream);
        String lang = parsedDoc.getLanguage();
        assertEquals(lang, "ara");
    }
}
