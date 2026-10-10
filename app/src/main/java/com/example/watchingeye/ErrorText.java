package com.example.watchingeye;

/** Plain-language explanations for failed requests. No Android classes, so it is covered by ordinary unit tests. */
public final class ErrorText {

    /** The plugin accepts 1..43200 minutes (30 days) per request. */
    public static final int MAX_MINUTES = 43200;

    private ErrorText() {}

    /** Explanation for an HTTP error. The plugin's error responses carry no text, so the app explains them. */
    public static String describeFailure(String what, int code) {
        switch (code) {
            case 400:
                return what + ": the plugin rejected the request. Minutes must be 1-" + MAX_MINUTES
                        + ", and the user must be an enabled limited user (time-outs also need the limiter turned on).";
            case 401:
                return what + ": Emby did not accept the Emby API key.";
            case 403:
                return what + ": refused. Check that \"Enable Public API\" is ticked on the plugin page, that the WatchingEye key"
                        + " matches, and that the Emby key is an administrator key. Repeated wrong keys lock the plugin API for 5 minutes.";
            case 404:
                return what + ": not found. Is the Watching Eye plugin installed (version 1.8 or newer for undo), and is the server address right?";
            default:
                return what + " failed: HTTP " + code;
        }
    }

    /** Explanation for a connection failure, including the technical reason so a persistent problem can be diagnosed. */
    public static String describeConnectionFailure(Throwable t) {
        String reason = t.getClass().getSimpleName();
        if (t instanceof java.net.UnknownHostException) {
            return "Can't find the server (" + reason + "). Check the server address and your internet connection.";
        }
        if (t instanceof java.net.SocketTimeoutException || t instanceof java.io.InterruptedIOException) {
            return "The server is not answering (" + reason + ").";
        }
        if (t instanceof javax.net.ssl.SSLException) {
            return "Secure connection failed (" + reason + "). Check the server's certificate and https:// address.";
        }
        return "Can't reach the server (" + reason + ").";
    }
}
