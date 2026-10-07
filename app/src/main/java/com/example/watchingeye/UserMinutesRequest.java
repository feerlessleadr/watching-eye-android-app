package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class UserMinutesRequest {
    @SerializedName("UserId")
    private String userId;

    @SerializedName("Minutes")
    private int minutes;

    public UserMinutesRequest(String userId, int minutes) {
        this.userId = userId;
        this.minutes = minutes;
    }

    public String getUserId() { return userId; }
    public int getMinutes() { return minutes; }
}