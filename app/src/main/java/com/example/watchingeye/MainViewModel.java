package com.example.watchingeye;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

public class MainViewModel extends ViewModel {

    /** The plugin accepts 1..43200 minutes (30 days) per request. */
    public static final int MAX_MINUTES = 43200;

    /** After the public route refuses us (403) we stop calling it for this long, so a wrong key cannot trigger the plugin's lockout. */
    private static final long PUBLIC_BACKOFF_MS = 5L * 60L * 1000L;

    private final MutableLiveData<List<UserCardInfo>> userCards = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> actionResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    private final ExecutorService pollExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicBoolean hasLoadedOnce = new AtomicBoolean(false);

    private volatile long publicBlockedUntil = 0L;

    private String embyApiKey;
    private String watchingEyeApiKey;
    private String serverUrl;

    public LiveData<List<UserCardInfo>> getUserCards() { return userCards; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getActionResult() { return actionResult; }
    public LiveData<Boolean> getLoading() { return loading; }

    public void setCredentials(String serverUrl, String embyApiKey, String watchingEyeApiKey) {
        this.serverUrl = serverUrl;
        this.embyApiKey = embyApiKey;
        this.watchingEyeApiKey = watchingEyeApiKey;
        this.publicBlockedUntil = 0L;
        RetrofitClient.configure(serverUrl, embyApiKey);
    }

    public void startUpdates() {
        if (polling.getAndSet(true)) return;
        pollExecutor.execute(() -> {
            while (polling.get()) {
                fetchOnce();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    public void stopUpdates() {
        polling.set(false);
    }

    private WatchingEyeApiService weApi() {
        return WatchingEyeRetrofitClient.getService(serverUrl, watchingEyeApiKey, embyApiKey);
    }

    /** Plain-language explanation for a failed request. The plugin's error responses carry no text, so the app explains them. */
    static String describeFailure(String what, int code) {
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
                return what + ": not found. Is the Watching Eye plugin installed, and is the server address right?";
            default:
                return what + " failed: HTTP " + code;
        }
    }

    private void fetchOnce() {
        try {
            if (!hasLoadedOnce.get()) {
                loading.postValue(true);
            }

            WatchingEyeApiService weApi = weApi();

            List<LimitedUserStatus> limitedUsers = new ArrayList<>();

            // The administrator route works with an Emby administrator key alone.
            Response<List<LimitedUserStatus>> statusResp = weApi.getLimitedUsersStatus().execute();

            // Fall back to the public route only if it is not in a back-off period.
            if ((!statusResp.isSuccessful() || statusResp.body() == null)
                    && System.currentTimeMillis() >= publicBlockedUntil) {
                Response<List<LimitedUserStatus>> publicResp = weApi.getStatus().execute();
                if (publicResp.code() == 403) {
                    publicBlockedUntil = System.currentTimeMillis() + PUBLIC_BACKOFF_MS;
                }
                statusResp = publicResp;
            }

            if (statusResp.isSuccessful() && statusResp.body() != null) {
                limitedUsers = statusResp.body();
            } else if (!statusResp.isSuccessful()) {
                error.postValue(describeFailure("Loading status", statusResp.code()));
            }

            EmbyApi embyApi = RetrofitClient.getInstance();
            Response<List<SessionInfo>> sessionsResp = embyApi.getSessions("TranscodingInfo").execute();
            List<SessionInfo> sessions = new ArrayList<>();
            if (sessionsResp.isSuccessful() && sessionsResp.body() != null) {
                sessions = sessionsResp.body();
            }

            List<UserCardInfo> cards = new ArrayList<>();
            for (LimitedUserStatus user : limitedUsers) {
                SessionInfo match = null;
                for (SessionInfo s : sessions) {
                    if (s.getUserName() != null
                            && s.getUserName().equalsIgnoreCase(user.getUsername())
                            && s.getNowPlayingItem() != null) {
                        match = s;
                        break;
                    }
                }
                cards.add(new UserCardInfo(user, match));
            }

            userCards.postValue(cards);
            hasLoadedOnce.set(true);

        } catch (Exception e) {
            error.postValue("Error: " + e.getMessage());
        } finally {
            loading.postValue(false);
        }
    }

    public void extendTime(String userId, int minutes) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            error.postValue("Minutes must be between 1 and " + MAX_MINUTES + ".");
            return;
        }
        actionExecutor.execute(() -> {
            try {
                Response<?> resp = weApi().extendTime(new UserMinutesRequest(userId, minutes)).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("Extended by " + minutes + " minutes");
                    fetchOnce();
                } else {
                    error.postValue(describeFailure("Extend", resp.code()));
                }
            } catch (Exception e) {
                error.postValue("Extend error: " + e.getMessage());
            }
        });
    }

    public void timeOutUser(String userId, int minutes) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            error.postValue("Minutes must be between 1 and " + MAX_MINUTES + ".");
            return;
        }
        actionExecutor.execute(() -> {
            try {
                Response<?> resp = weApi().timeOutUser(new UserMinutesRequest(userId, minutes)).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("User timed out");
                    fetchOnce();
                } else {
                    error.postValue(describeFailure("Time-out", resp.code()));
                }
            } catch (Exception e) {
                error.postValue("Time-out error: " + e.getMessage());
            }
        });
    }

    public void clearTimeOut(String userId) {
        actionExecutor.execute(() -> {
            try {
                Response<?> resp = weApi().clearTimeOut(new UserIdRequest(userId)).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("Timeout cleared");
                    fetchOnce();
                } else {
                    error.postValue(describeFailure("Clear time-out", resp.code()));
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    public void resetUserTime(String userId) {
        actionExecutor.execute(() -> {
            try {
                Response<?> resp = weApi().resetUserTime(new UserIdRequest(userId)).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("Time reset");
                    fetchOnce();
                } else {
                    error.postValue(describeFailure("Reset", resp.code()));
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    public void stopStream(String sessionId) {
        actionExecutor.execute(() -> {
            try {
                EmbyApi embyApi = RetrofitClient.getInstance();
                Response<?> resp = embyApi.stopStream(sessionId).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("Stream stopped");
                    fetchOnce();
                } else {
                    error.postValue(describeFailure("Stop stream", resp.code()));
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        stopUpdates();
        pollExecutor.shutdownNow();
        actionExecutor.shutdownNow();
        super.onCleared();
    }
}
