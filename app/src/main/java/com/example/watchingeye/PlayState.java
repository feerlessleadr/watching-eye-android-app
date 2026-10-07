package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class PlayState {

    @SerializedName("PositionTicks")
    private Long positionTicks;

    @SerializedName("IsPaused")
    private boolean isPaused;

    @SerializedName("CanSeek")
    private boolean canSeek;

    public Long getPositionTicks() { return positionTicks; }
    public boolean isPaused() { return isPaused; }
    public boolean isCanSeek() { return canSeek; }
}