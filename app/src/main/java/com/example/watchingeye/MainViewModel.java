package com.example.watchingeye;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

    /** Shown as a banner (not a pop-up) while the server cannot be reached; null when everything is fine. */
    private final MutableLiveData<String> connectionProblem = new MutableLiveData<>();

    /** How often to ask the server: quickly while somebody is watching, slowly otherwise, and backing off after failures. */
    private static final long POLL_PLAYING_MS = 10_000L;
    private static final long POLL_IDLE_MS = 30_000L;
    private static final long POLL_MAX_BACKOFF_MS = 60_000L;

    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean hasLoadedOnce = new AtomicBoolean(false);

    private ScheduledFuture<?> pollTask;
    private int generation = 0;          // bumped by every start/stop so a stale poll cycle never reschedules itself
    private int consecutiveFailures = 0;
    private long lastStartMs = 0L;
    private volatile boolean somebodyPlaying = false;

    private volatile long publicBlockedUntil = 0L;

    private String embyApiKey;
    private String watchingEyeApiKey;
    private String serverUrl;

    public LiveData<List<UserCardInfo>> getUserCards() { return userCards; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getActionResult() { return actionResult; }
    public LiveData<Boolean> getLoading() { return loading; }

    public synchronized void setCredentials(String serverUrl, String embyApiKey, String watchingEyeApiKey) {
        boolean changed = !java.util.Objects.equals(this.serverUrl, serverUrl)
                || !java.util.Objects.equals(this.embyApiKey, embyApiKey)
                || !java.util.Objects.equals(this.watchingEyeApiKey, watchingEyeApiKey);
        if (changed) lastStartMs = 0L; // new settings: the next start must not be ignored
        this.serverUrl = serverUrl;
        this.embyApiKey = embyApiKey;
        this.watchingEyeApiKey = watchingEyeApiKey;
        this.publicBlockedUntil = 0L;
        RetrofitClient.configure(serverUrl, embyApiKey);
    }

    public LiveData<String> getConnectionProblem() { return connectionProblem; }

    /** Clears a pop-up message once the screen has shown it, so it is not shown again when the screen is rebuilt. */
    public void consumeMessages() {
        error.setValue(null);
        actionResult.setValue(null);
    }

    /** Starts (or restarts) polling from scratch: fresh connections, immediate first request. Call when the screen becomes visible. */
    public synchronized void startUpdates() {
        long now = System.currentTimeMillis();
        if (now - lastStartMs < 3000L) return; // several triggers at once
        lastStartMs = now;
        if (pollTask != null) pollTask.cancel(false);
        generation++;
        consecutiveFailures = 0;
        Http.resetConnections();
        scheduleNext(0L, generation);
    }

    /** Stops polling. Call when the screen is no longer visible: nobody is looking, and the phone may be asleep. */
    public synchronized void stopUpdates() {
        generation++;
        lastStartMs = 0L; // a later start must always run
        if (pollTask != null) pollTask.cancel(false);
    }

    private synchronized void scheduleNext(long delayMs, int gen) {
        if (gen != generation || poller.isShutdown()) return;
        pollTask = poller.schedule(() -> pollCycle(gen), delayMs, TimeUnit.MILLISECONDS);
    }

    private void pollCycle(int gen) {
        boolean ok;
        try {
            ok = fetchOnce();
        } catch (Throwable t) {
            ok = false;
        }
        long delay;
        synchronized (this) {
            if (ok) {
                consecutiveFailures = 0;
                delay = somebodyPlaying ? POLL_PLAYING_MS : POLL_IDLE_MS;
            } else {
                consecutiveFailures++;
                delay = Math.min(POLL_MAX_BACKOFF_MS, POLL_PLAYING_MS * consecutiveFailures);
            }
        }
        scheduleNext(delay, gen);
    }

    private WatchingEyeApiService weApi() {
        return WatchingEyeRetrofitClient.getService(serverUrl, watchingEyeApiKey, embyApiKey);
    }

    /** Explains a connection failure in words, including the technical reason so a persistent problem can be diagnosed. */
    static String describeConnectionFailure(Throwable t) {
        String reason = t.getClass().getSimpleName();
        if (t instanceof java.net.UnknownHostException) {
            return "Can't find the server (" + reason + "). Check the server address and your internet connection. Retrying...";
        }
        if (t instanceof java.net.SocketTimeoutException || t instanceof java.io.InterruptedIOException) {
            return "The server is not answering (" + reason + "). Retrying...";
        }
        if (t instanceof javax.net.ssl.SSLException) {
            return "Secure connection failed (" + reason + "). Check the server's certificate and https:// address. Retrying...";
        }
        return "Can't reach the server (" + reason + "). Retrying...";
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

    /** One refresh. Returns true when the server answered usefully; on failure the last good cards stay on screen. */
    private boolean fetchOnce() {
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
                connectionProblem.postValue(describeFailure("Loading status", statusResp.code()));
                return false;
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

            boolean anyPlaying = false;
            for (UserCardInfo card : cards) {
                if (card.isPlaying()) anyPlaying = true;
            }
            somebodyPlaying = anyPlaying;

            userCards.postValue(cards);
            connectionProblem.postValue(null);
            hasLoadedOnce.set(true);
            return true;

        } catch (Exception e) {
            connectionProblem.postValue(describeConnectionFailure(e));
            return false;
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
        poller.shutdownNow();
        actionExecutor.shutdownNow();
        super.onCleared();
    }
}
