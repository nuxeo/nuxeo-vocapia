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

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "SpeechSegment")
public class Segment {

    protected String speakerId;

    protected Double startTime;

    protected Double endTime;

    protected String language;

    protected ArrayList<Word> words = new ArrayList<Word>();

    @XmlAttribute(name = "spkid")
    public String getSpeakerId() {
        return speakerId;
    }

    public void setSpeakerId(String speakerId) {
        this.speakerId = speakerId;
    }

    @XmlAttribute(name = "stime")
    public Double getStartTime() {
        return startTime;
    }

    public void setStartTime(Double startTime) {
        this.startTime = startTime;
    }

    @XmlAttribute(name = "etime")
    public Double getEndTime() {
        return endTime;
    }

    public void setEndTime(Double endTime) {
        this.endTime = endTime;
    }

    @XmlElement(name = "Word")
    public ArrayList<Word> getWords() {
        return words;
    }

    public void setWords(ArrayList<Word> occurrences) {
        this.words = occurrences;
    }

    public String getText() {
        StringBuffer sb = new StringBuffer();
        for (Word word: words) {
            String w = word.getText().trim();
            if (".".equals(w) || ",".equals(w) || ":".equals(w)) {
                // Do not put a leading space.
                sb.append(w);
            } else {
                sb.append(" ");
                sb.append(w);
            }
        }
        return sb.toString().trim();
    }

    public Double getDuration() {
        return endTime - startTime;
    }

    @XmlAttribute(name = "lang")
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
