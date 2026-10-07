package com.example.watchingeye;

public class UserCardInfo {
    private final LimitedUserStatus userStatus;
    private final SessionInfo sessionInfo; // may be null

    public UserCardInfo(LimitedUserStatus userStatus, SessionInfo sessionInfo) {
        this.userStatus = userStatus;
        this.sessionInfo = sessionInfo;
    }

    public LimitedUserStatus getUserStatus() { return userStatus; }
    public SessionInfo getSessionInfo() { return sessionInfo; }

    public boolean isPlaying() {
        return sessionInfo != null
                && sessionInfo.getNowPlayingItem() != null;
    }
}