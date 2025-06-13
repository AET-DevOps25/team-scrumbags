package com.trace.transcription.model;

import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;

@Embeddable
public class TranscriptSegment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String index;
    private String text;
    private String start;
    private String end;
    private String speakerId;
    private String speakerName;

    public TranscriptSegment() {}

    public TranscriptSegment(String index, String text, String start, String end, String speakerId, String speakerName) {
        this.index = index;
        this.text = text;
        this.start = start;
        this.end = end;
        this.speakerId = speakerId;
        this.speakerName = speakerName;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getSpeakerId() {
        return speakerId;
    }

    public void setSpeakerId(String speakerId) {
        this.speakerId = speakerId;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }
}
