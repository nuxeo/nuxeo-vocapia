/* Copyright 2011 Nuxeo and contributors.
 * 
 * This file is licensed to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nuxeo.vocapia.service.xml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.nuxeo.vocapia.service.Transcription;

@XmlRootElement(name = "AudioDoc")
public class AudioDoc {

    private ArrayList<Segment> segments = new ArrayList<Segment>();

    @XmlElementWrapper(name = "SegmentList")
    @XmlElement(name = "SpeechSegment")
    public ArrayList<Segment> getSegments() {
        return segments;
    }

    public void setSegments(ArrayList<Segment> segments) {
        this.segments = segments;
    }

    public static AudioDoc readFrom(String xmlPayload) throws JAXBException {
        return readFrom(new ByteArrayInputStream(
                xmlPayload.getBytes(Charset.forName("UTF-8"))));
    }

    public static AudioDoc readFrom(InputStream xmlStream) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(AudioDoc.class);
        Unmarshaller um = context.createUnmarshaller();
        return (AudioDoc) um.unmarshal(xmlStream);
    }

    public Transcription asTranscription() {
        Transcription transcription = Transcription.emptyTranscription();
        for (Segment segment : segments) {
            transcription.appendSection(segment.getStartTime(),
                    segment.getEndTime(), segment.getText(),
                    segment.getSpeakerId());
        }
        return transcription;
    }

    /**
     * @return the language code of the longest segment
     */
    public String getLanguage() {
        Segment longest = null;
        for (Segment segment: segments) {
            if (longest == null) {
                longest = segment;
            } else {
                if (longest.getDuration() < segment.getDuration()) {
                    longest = segment;
                }
            }
        }
        if (longest == null) {
            return null;
        }
        return longest.getLanguage();
    }
}
