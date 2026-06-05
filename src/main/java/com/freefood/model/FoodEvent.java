package com.freefood.model;

public class FoodEvent {
    private Long id;
    private Long userId;             // who posted (null = anonymous)
    private String providerName;
    private String contactNumber;
    private String foodDescription;
    private String address;
    private String city;
    private String state;
    private double latitude;
    private double longitude;
    private String eventDate;
    private String startTime;
    private String endTime;
    private int quantity;
    private String notes;
    private String photoPath;        // relative path to uploaded photo
    private String createdAt;
    private boolean addedByWitness;  // true if someone else (not organiser) added it
    private String witnessName;      // name of person who reported it
    // computed
    private double distanceKm;
    private int goingCount;
    private int checkinCount;
    private boolean userIsGoing;     // for current session user

    public FoodEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getFoodDescription() { return foodDescription; }
    public void setFoodDescription(String foodDescription) { this.foodDescription = foodDescription; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getEventDate() { return eventDate; }
    public void setEventDate(String eventDate) { this.eventDate = eventDate; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public boolean isAddedByWitness() { return addedByWitness; }
    public void setAddedByWitness(boolean addedByWitness) { this.addedByWitness = addedByWitness; }
    public String getWitnessName() { return witnessName; }
    public void setWitnessName(String witnessName) { this.witnessName = witnessName; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public int getGoingCount() { return goingCount; }
    public void setGoingCount(int goingCount) { this.goingCount = goingCount; }
    public int getCheckinCount() { return checkinCount; }
    public void setCheckinCount(int checkinCount) { this.checkinCount = checkinCount; }
    public boolean isUserIsGoing() { return userIsGoing; }
    public void setUserIsGoing(boolean userIsGoing) { this.userIsGoing = userIsGoing; }
}
