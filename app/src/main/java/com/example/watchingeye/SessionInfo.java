package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class SessionInfo {

    @SerializedName("Id")
    private String id;

    @SerializedName("UserId")
    private String userId;

    @SerializedName("UserName")
    private String userName;

    @SerializedName("Client")
    private String client;

    @SerializedName("DeviceName")
    private String deviceName;

    @SerializedName("DeviceId")
    private String deviceId;

    @SerializedName("RemoteEndPoint")
    private String remoteEndPoint;

    @SerializedName("NowPlayingItem")
    private NowPlayingItem nowPlayingItem;

    @SerializedName("PlayState")
    private PlayState playState;

    @SerializedName("TranscodingInfo")
    private TranscodingInfo transcodingInfo;

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getClient() { return client; }
    public String getDeviceName() { return deviceName; }
    public String getDeviceId() { return deviceId; }
    public String getRemoteEndPoint() { return remoteEndPoint; }
    public NowPlayingItem getNowPlayingItem() { return nowPlayingItem; }
    public PlayState getPlayState() { return playState; }
    public TranscodingInfo getTranscodingInfo() { return transcodingInfo; }
}