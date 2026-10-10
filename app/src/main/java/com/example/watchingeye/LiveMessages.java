package com.example.watchingeye;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the messages that arrive on the live (websocket) connection to Emby, and builds the ones the app sends.
 * Plain Java, so it is covered by ordinary unit tests.
 */
final class LiveMessages {

    enum Kind { STATUS, SESSIONS, KEEP_ALIVE_REQUEST, OTHER }

    /** One received message. Only the fields that belong to its kind are filled in. */
    static final class Message {
        Kind kind = Kind.OTHER;
        List<LimitedUserStatus> users = new ArrayList<>();
        List<SessionInfo> sessions = new ArrayList<>();
        /** Status push: set when the plugin refused the subscription (wrong or missing key, Public API off). */
        String error;
        /** Emby asks for a KeepAlive at least this often (seconds); 0 when not given. */
        int keepAliveSeconds;
    }

    private static final Gson GSON = new Gson();
    private static final Type USERS = new TypeToken<List<LimitedUserStatus>>() {}.getType();
    private static final Type SESSIONS = new TypeToken<List<SessionInfo>>() {}.getType();

    private LiveMessages() {}

    /** Sent right after connecting: start the plugin's status push (the key travels in the message, never in the URL). */
    static String subscribeStatus(String pluginKey) {
        JsonObject o = new JsonObject();
        o.addProperty("MessageType", "WatchingEyeStatusStart");
        o.addProperty("Data", "0,30000," + (pluginKey == null ? "" : pluginKey));
        return o.toString();
    }

    /** Sent right after connecting: Emby pushes the session list when it changes (at most every 1.5 s). */
    static String subscribeSessions() {
        JsonObject o = new JsonObject();
        o.addProperty("MessageType", "SessionsStart");
        o.addProperty("Data", "0,1500");
        return o.toString();
    }

    static String keepAlive() {
        return "{\"MessageType\":\"KeepAlive\"}";
    }

    /** Parses one text frame; anything unexpected becomes an {@link Kind#OTHER} message rather than an error. */
    static Message parse(String text) {
        Message m = new Message();
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) return m;
            JsonObject obj = root.getAsJsonObject();
            String type = obj.has("MessageType") && !obj.get("MessageType").isJsonNull() ? obj.get("MessageType").getAsString() : "";
            JsonElement data = obj.get("Data");
            switch (type) {
                case "WatchingEyeStatus":
                    m.kind = Kind.STATUS;
                    if (data != null && data.isJsonObject()) {
                        JsonObject d = data.getAsJsonObject();
                        if (d.has("Error") && !d.get("Error").isJsonNull()) {
                            m.error = d.get("Error").getAsString();
                        }
                        JsonElement users = d.get("Users");
                        if (users != null && users.isJsonArray()) {
                            List<LimitedUserStatus> list = GSON.fromJson(users, USERS);
                            if (list != null) m.users = list;
                        }
                    }
                    break;
                case "Sessions":
                    m.kind = Kind.SESSIONS;
                    if (data != null && data.isJsonArray()) {
                        JsonArray array = data.getAsJsonArray();
                        List<SessionInfo> list = GSON.fromJson(array, SESSIONS);
                        if (list != null) m.sessions = list;
                    }
                    break;
                case "ForceKeepAlive":
                    m.kind = Kind.KEEP_ALIVE_REQUEST;
                    if (data != null && data.isJsonPrimitive()) {
                        m.keepAliveSeconds = data.getAsInt();
                    }
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            m.kind = Kind.OTHER;
        }
        return m;
    }

    /** http(s)://host[:port][/base] to ws(s)://host[:port][/base]/embywebsocket?deviceId=... (the token goes in a header, not here). */
    static String webSocketUrl(String baseUrl, String deviceId) {
        String base = baseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.regionMatches(true, 0, "https://", 0, 8)) {
            base = "wss://" + base.substring(8);
        } else if (base.regionMatches(true, 0, "http://", 0, 7)) {
            base = "ws://" + base.substring(7);
        }
        return base + "/embywebsocket?deviceId=" + deviceId;
    }
}
