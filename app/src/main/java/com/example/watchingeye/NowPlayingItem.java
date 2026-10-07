package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class NowPlayingItem {

    @SerializedName("Name")
    private String name;

    @SerializedName("Id")
    private String id;

    @SerializedName("Type")
    private String type;

    @SerializedName("RunTimeTicks")
    private Long runTimeTicks;

    public String getName() { return name; }
    public String getId() { return id; }
    public String getType() { return type; }
    public Long getRunTimeTicks() { return runTimeTicks; }
}