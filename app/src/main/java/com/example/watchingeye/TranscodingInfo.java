package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class TranscodingInfo {

    @SerializedName("VideoCodec")
    private String videoCodec;

    @SerializedName("AudioCodec")
    private String audioCodec;

    @SerializedName("IsVideoDirect")
    private boolean isVideoDirect;

    @SerializedName("IsAudioDirect")
    private boolean isAudioDirect;

    public String getVideoCodec() { return videoCodec; }
    public String getAudioCodec() { return audioCodec; }
    public boolean isVideoDirect() { return isVideoDirect; }
    public boolean isAudioDirect() { return isAudioDirect; }
}