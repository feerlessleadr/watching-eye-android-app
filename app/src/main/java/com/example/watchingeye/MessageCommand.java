package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class MessageCommand {

    @SerializedName("Header")
    private String header;

    @SerializedName("Text")
    private String text;

    @SerializedName("TimeoutMs")
    private Long timeoutMs;

    public MessageCommand(String header, String text, Long timeoutMs) {
        this.header = header;
        this.text = text;
        this.timeoutMs = timeoutMs;
    }

    public String getHeader() { return header; }
    public String getText() { return text; }
    public Long getTimeoutMs() { return timeoutMs; }
}