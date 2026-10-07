package com.example.watchingeye;

public class CombinedSessionInfo {
    private final SessionInfo sessionInfo;
    private final LimitedUserStatus limitedUserStatus;

    public CombinedSessionInfo(SessionInfo sessionInfo, LimitedUserStatus limitedUserStatus) {
        this.sessionInfo = sessionInfo;
        this.limitedUserStatus = limitedUserStatus;
    }

    public SessionInfo getSessionInfo() { return sessionInfo; }
    public LimitedUserStatus getLimitedUserStatus() { return limitedUserStatus; }
}