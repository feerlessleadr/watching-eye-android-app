package com.example.watchingeye;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Signs in to Emby with an ordinary user account and returns that account's access token. The app keeps only the token: the
 * password is used for this one request and never stored. A token from a regular (non-administrator) account is much safer
 * on a phone than an administrator API key, because losing the phone does not hand over the whole server.
 */
final class EmbyLogin {

    static final class Result {
        final String accessToken;
        final String userName;
        final boolean administrator;

        Result(String accessToken, String userName, boolean administrator) {
            this.accessToken = accessToken;
            this.userName = userName;
            this.administrator = administrator;
        }
    }

    private EmbyLogin() {}

    /** The JSON body of the sign-in request. */
    static String requestBody(String userName, String password) {
        JsonObject o = new JsonObject();
        o.addProperty("Username", userName);
        o.addProperty("Pw", password);
        return o.toString();
    }

    /** Reads Emby's sign-in answer. Throws IllegalArgumentException when it has no access token. */
    static Result parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Unexpected answer from Emby.");
        JsonObject obj = root.getAsJsonObject();
        if (!obj.has("AccessToken") || obj.get("AccessToken").isJsonNull() || obj.get("AccessToken").getAsString().isEmpty()) {
            throw new IllegalArgumentException("Emby did not return an access token.");
        }
        String token = obj.get("AccessToken").getAsString();
        String name = "";
        boolean admin = false;
        if (obj.has("User") && obj.get("User").isJsonObject()) {
            JsonObject user = obj.getAsJsonObject("User");
            if (user.has("Name") && !user.get("Name").isJsonNull()) name = user.get("Name").getAsString();
            if (user.has("Policy") && user.get("Policy").isJsonObject()) {
                JsonObject policy = user.getAsJsonObject("Policy");
                admin = policy.has("IsAdministrator") && !policy.get("IsAdministrator").isJsonNull() && policy.get("IsAdministrator").getAsBoolean();
            }
        }
        return new Result(token, name, admin);
    }

    /** Plain-language reason for a failed sign-in. */
    static String describeFailure(int code) {
        switch (code) {
            case 401:
            case 403:
                return "Emby did not accept that user name and password.";
            case 404:
                return "Emby was not found at that address. Check the server address.";
            default:
                return "Sign-in failed: HTTP " + code + ".";
        }
    }

    /** Runs the sign-in (blocking: call from a background thread). */
    static Result signIn(OkHttpClient client, String baseUrl, String userName, String password) throws IOException {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        Request request = new Request.Builder()
                .url(base + "emby/Users/AuthenticateByName")
                .header("X-Emby-Authorization",
                        "MediaBrowser Client=\"Watching Eye\", Device=\"Android\", DeviceId=\"watchingeye-android\", Version=\"1\"")
                .post(RequestBody.create(requestBody(userName, password), MediaType.get("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException(describeFailure(response.code()));
            }
            try {
                return parse(response.body().string());
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        }
    }
}
