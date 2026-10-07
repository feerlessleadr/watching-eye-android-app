package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class UserIdRequest {
    @SerializedName("UserId")
    private String userId;

    public UserIdRequest(String userId) {
        this.userId = userId;
    }

    public String getUserId() { return userId; }
}