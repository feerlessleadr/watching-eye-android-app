package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

/** What the plugin (1.9+) says a user is playing right now, so the app needs nothing from Emby's own session list. */
public class PluginNowPlaying {

    @SerializedName("SessionId")
    private String sessionId;

    @SerializedName("ItemId")
    private String itemId;

    @SerializedName("ItemName")
    private String itemName;

    @SerializedName("ItemType")
    private String itemType;

    @SerializedName("RunTimeTicks")
    private long runTimeTicks;

    @SerializedName("PositionTicks")
    private long positionTicks;

    @SerializedName("IsPaused")
    private boolean paused;

    @SerializedName("Client")
    private String client;

    @SerializedName("DeviceName")
    private String deviceName;

    public String getSessionId() { return sessionId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getItemType() { return itemType; }
    public long getRunTimeTicks() { return runTimeTicks; }
    public long getPositionTicks() { return positionTicks; }
    public boolean isPaused() { return paused; }
    public String getClient() { return client; }
    public String getDeviceName() { return deviceName; }
}
