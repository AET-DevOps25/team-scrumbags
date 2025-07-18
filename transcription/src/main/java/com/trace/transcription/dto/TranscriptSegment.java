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
    @Column(name = "text", columnDefinition="TEXT")
    private String text;

    @Column(name = "start_time")
    private String start;

    @Column(name = "end_time")
    private String end;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    public TranscriptSegment() {}

    public TranscriptSegment(String index, String text, String start, String end, String userId, String userName) {
        this.segmentIndex = index;
        this.text = text;
        this.start = start;
        this.end = end;
        this.userId = userId;
        this.userName = userName;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
