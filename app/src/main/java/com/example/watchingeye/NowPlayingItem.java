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

    public NowPlayingItem() {}

    NowPlayingItem(String id, String name, String type, long runTimeTicks) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.runTimeTicks = runTimeTicks;
    }

    public String getName() { return name; }
    public String getId() { return id; }
    public String getType() { return type; }
    public Long getRunTimeTicks() { return runTimeTicks; }
}