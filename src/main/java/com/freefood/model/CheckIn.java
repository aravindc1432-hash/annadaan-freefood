package com.freefood.model;

/**
 * Represents a person confirming/updating their arrival at a food event.
 */
public class CheckIn {
    private Long id;
    private Long eventId;
    private String checkerName;      // name of person checking in
    private String sessionId;        // browser session to prevent duplicates
    private double latitude;         // updated GPS from checker
    private double longitude;
    private String note;             // optional note from checker
    private String createdAt;

    public CheckIn() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getCheckerName() { return checkerName; }
    public void setCheckerName(String checkerName) { this.checkerName = checkerName; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
