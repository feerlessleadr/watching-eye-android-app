package com.example.watchingeye;

import okhttp3.HttpUrl;

/** Checks the server address typed in Settings, so a typo shows a message instead of crashing the app. */
final class ServerUrl {

    private ServerUrl() {}

    /** True for an http:// or https:// address with a host (and a valid port, if there is one). */
    static boolean isValid(String url) {
        if (url == null) return false;
        String text = url.trim();
        if (text.isEmpty()) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        HttpUrl parsed = HttpUrl.parse(text.endsWith("/") ? text : text + "/");
        return parsed != null && !parsed.host().isEmpty();
    }
}
