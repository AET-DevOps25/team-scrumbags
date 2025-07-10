package com.trace.transcription.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;

import java.io.Serial;
import java.io.Serializable;

@Embeddable
public class TranscriptSegment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "segment_index")
    private String segmentIndex;

    @Lob
    @Column(name = "text")
    private String text;

    @Column(name = "start_time")
    private String start;

    @Column(name = "end_time")
    private String end;

    @Column(name = "speaker_id")
    private String speakerId;

    @Column(name = "speaker_name")
    private String speakerName;

    public TranscriptSegment() {}

    public TranscriptSegment(String index, String text, String start, String end, String speakerId, String speakerName) {
        this.segmentIndex = index;
        this.text = text;
        this.start = start;
        this.end = end;
        this.speakerId = speakerId;
        this.speakerName = speakerName;
    }

    public String getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(String segmentIndex) {
        this.segmentIndex = segmentIndex;
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
