package com.example.watchingeye;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * The one place that knows the current state of every limited user. The server pushes changes over the live
 * connection; asking the server (the old way) is only a safety net while the live connection is down. The screen, the
 * alert service and the home-screen widget all read from here, so they always agree.
 *
 * Users of this class call {@link #acquire} while they need live data and {@link #release} when they do not;
 * the connection is open only while at least one user is holding it.
 */
public final class StatusRepository implements LiveConnection.Listener {

    /** What the screen shows about the connection. */
    public enum Link { CONNECTING, LIVE, POLLING, OFFLINE }

    public static final class ConnectionInfo {
        public final Link link;
        public final String detail;
        public final long lastDataMs;

        ConnectionInfo(Link link, String detail, long lastDataMs) {
            this.link = link;
            this.detail = detail == null ? "" : detail;
            this.lastDataMs = lastDataMs;
        }
    }

    public static final class Credentials {
        public final String serverUrl;
        public final String embyKey;
        public final String pluginKey;

        public Credentials(String serverUrl, String embyKey, String pluginKey) {
            this.serverUrl = serverUrl;
            this.embyKey = embyKey;
            this.pluginKey = pluginKey;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Credentials)) return false;
            Credentials c = (Credentials) o;
            return Objects.equals(serverUrl, c.serverUrl) && Objects.equals(embyKey, c.embyKey) && Objects.equals(pluginKey, c.pluginKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serverUrl, embyKey, pluginKey);
        }
    }

    /** Result of an action (extend, time-out, ...), reported on a background thread. */
    public interface ResultCallback {
        void done(boolean ok, String message);
    }

    /** While the live connection is down the app asks the server this often. */
    private static final long FALLBACK_POLL_SECONDS = 20L;
    /** After the server refuses the public route (403) the app stops using it for this long, so a wrong key cannot trigger the plugin's lockout. */
    private static final long PUBLIC_BACKOFF_MS = 5L * 60L * 1000L;

    private static StatusRepository instance;

    public static synchronized StatusRepository get(Context context) {
        if (instance == null) {
            instance = new StatusRepository();
        }
        if (context != null && instance.appContext == null) {
            instance.appContext = context.getApplicationContext();
        }
        return instance;
    }

    /** Used to keep the home-screen widget up to date; set the first time a Context is passed to {@link #get}. */
    private volatile Context appContext;

    private final MutableLiveData<List<UserCardInfo>> cards = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<ConnectionInfo> connection = new MutableLiveData<>(new ConnectionInfo(Link.CONNECTING, "", 0L));
    private final LiveConnection live = new LiveConnection(Http.newBuilder().build(), this);
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();

    private int clients = 0;
    private Credentials credentials;
    private WatchingEyeApiService weApi;
    private EmbyApi embyApi;
    private ScheduledFuture<?> fallbackTask;
    private volatile long publicBlockedUntil = 0L;

    private List<LimitedUserStatus> statuses = new ArrayList<>();
    private long statusAtMs = 0L;
    private List<SessionInfo> sessions = new ArrayList<>();
    private long sessionsAtMs = 0L;
    private LiveConnection.State liveState = LiveConnection.State.STOPPED;
    private String liveDetail = "";
    private String restProblem = "";
    private long lastRestOkMs = 0L;
    private long lastDataMs = 0L;
    private boolean everHadData = false;

    private StatusRepository() {}

    public LiveData<List<UserCardInfo>> getCards() { return cards; }
    public LiveData<ConnectionInfo> getConnection() { return connection; }

    public synchronized Credentials getCredentials() { return credentials; }

    /** Starts (or keeps) the live connection with these settings. Call {@link #release} when done. */
    public synchronized void acquire(Credentials creds) {
        clients++;
        boolean changed = !creds.equals(credentials);
        if (changed) {
            credentials = creds;
            RetrofitClient.configure(creds.serverUrl, creds.embyKey);
            weApi = WatchingEyeRetrofitClient.getService(creds.serverUrl, creds.pluginKey, creds.embyKey);
            embyApi = RetrofitClient.getInstance();
            publicBlockedUntil = 0L;
            statuses = new ArrayList<>();
            sessions = new ArrayList<>();
            everHadData = false;
        }
        if (clients == 1 || changed) {
            Http.resetConnections();
            live.start(creds.serverUrl, creds.embyKey, creds.pluginKey);
            startFallback();
        } else if (liveState != LiveConnection.State.LIVE) {
            live.reconnectNow();
        }
        worker.execute(this::refreshRest);
    }

    public synchronized void release() {
        if (clients > 0) clients--;
        if (clients == 0) {
            live.stop();
            if (fallbackTask != null) fallbackTask.cancel(false);
            fallbackTask = null;
        }
    }

    /** Pull-to-refresh and "the phone just got a network": ask the server once and reconnect the live connection. */
    public synchronized void refreshNow() {
        if (credentials == null) return;
        live.reconnectNow();
        worker.execute(this::refreshRest);
    }

    private void startFallback() {
        if (fallbackTask != null) fallbackTask.cancel(false);
        fallbackTask = worker.scheduleWithFixedDelay(() -> {
            boolean liveNow;
            synchronized (StatusRepository.this) {
                liveNow = liveState == LiveConnection.State.LIVE;
            }
            if (!liveNow) refreshRest();
        }, FALLBACK_POLL_SECONDS, FALLBACK_POLL_SECONDS, TimeUnit.SECONDS);
    }

    // ---- live connection callbacks --------------------------------------------------------------------

    @Override
    public void onStatus(List<LimitedUserStatus> users, long atMs) {
        synchronized (this) {
            statuses = users;
            statusAtMs = atMs;
            lastDataMs = atMs;
            everHadData = true;
        }
        publish();
    }

    @Override
    public void onSessions(List<SessionInfo> list, long atMs) {
        synchronized (this) {
            sessions = list;
            sessionsAtMs = atMs;
        }
        publish();
    }

    @Override
    public void onState(LiveConnection.State state, String detail) {
        synchronized (this) {
            liveState = state;
            liveDetail = detail == null ? "" : detail;
        }
        publish();
    }

    // ---- asking the server (first load, pull-to-refresh, and fallback while the live connection is down) ----

    private void refreshRest() {
        WatchingEyeApiService we;
        EmbyApi emby;
        synchronized (this) {
            we = weApi;
            emby = embyApi;
        }
        if (we == null || emby == null) return;
        try {
            List<LimitedUserStatus> users = new ArrayList<>();
            // The administrator route works with an Emby administrator key alone.
            Response<List<LimitedUserStatus>> statusResp = we.getLimitedUsersStatus().execute();
            if ((!statusResp.isSuccessful() || statusResp.body() == null) && System.currentTimeMillis() >= publicBlockedUntil) {
                Response<List<LimitedUserStatus>> publicResp = we.getStatus().execute();
                if (publicResp.code() == 403) {
                    publicBlockedUntil = System.currentTimeMillis() + PUBLIC_BACKOFF_MS;
                }
                statusResp = publicResp;
            }
            if (!statusResp.isSuccessful() || statusResp.body() == null) {
                setRestProblem(ErrorText.describeFailure("Loading status", statusResp.code()));
                return;
            }
            users = statusResp.body();
            Response<List<SessionInfo>> sessionsResp = emby.getSessions("TranscodingInfo").execute();
            List<SessionInfo> list = sessionsResp.isSuccessful() && sessionsResp.body() != null ? sessionsResp.body() : new ArrayList<>();
            long now = System.currentTimeMillis();
            synchronized (this) {
                // Do not overwrite fresher live data with this answer.
                if (liveState != LiveConnection.State.LIVE || statusAtMs == 0L) {
                    statuses = users;
                    statusAtMs = now;
                    sessions = list;
                    sessionsAtMs = now;
                }
                lastRestOkMs = now;
                lastDataMs = now;
                restProblem = "";
                everHadData = true;
            }
            publish();
        } catch (Exception e) {
            setRestProblem(ErrorText.describeConnectionFailure(e));
        }
    }

    private void setRestProblem(String problem) {
        synchronized (this) {
            restProblem = problem;
        }
        publish();
    }

    // ---- merging -----------------------------------------------------------------------------------------

    private void publish() {
        List<UserCardInfo> built = new ArrayList<>();
        ConnectionInfo info;
        synchronized (this) {
            for (LimitedUserStatus user : statuses) {
                SessionInfo match = null;
                for (SessionInfo s : sessions) {
                    if (s.getUserName() != null
                            && s.getUserName().equalsIgnoreCase(user.getUsername())
                            && s.getNowPlayingItem() != null) {
                        match = s;
                        break;
                    }
                }
                built.add(new UserCardInfo(user, match, statusAtMs, sessionsAtMs));
            }
            info = computeConnection();
        }
        cards.postValue(built);
        Context widgetContext = appContext;
        if (widgetContext != null && !built.isEmpty()) {
            WidgetUpdater.updateFromCards(widgetContext, built);
        }
        connection.postValue(info);
    }

    private ConnectionInfo computeConnection() {
        long now = System.currentTimeMillis();
        if (liveState == LiveConnection.State.LIVE) {
            return new ConnectionInfo(Link.LIVE, "", lastDataMs);
        }
        boolean restFresh = lastRestOkMs > 0 && now - lastRestOkMs < 2 * FALLBACK_POLL_SECONDS * 1000L;
        String why = liveDetail.isEmpty() ? "Live updates are not connected yet." : liveDetail;
        if (restFresh) {
            return new ConnectionInfo(Link.POLLING, why, lastDataMs);
        }
        if (!everHadData && restProblem.isEmpty()
                && (liveState == LiveConnection.State.CONNECTING || liveState == LiveConnection.State.STOPPED)) {
            return new ConnectionInfo(Link.CONNECTING, "", 0L);
        }
        String problem = !restProblem.isEmpty() ? restProblem : why;
        return new ConnectionInfo(Link.OFFLINE, problem, lastDataMs);
    }

    // ---- actions ------------------------------------------------------------------------------------------

    public void extend(String userId, int minutes, ResultCallback cb) {
        runAction("Extend", cb, () -> weApi().extendTime(new UserMinutesRequest(userId, minutes)).execute(), "Added " + minutes + " min");
    }

    /** Takes back extra time (the Undo for {@link #extend}). Needs plugin 1.8 or newer. */
    public void reduce(String userId, int minutes, ResultCallback cb) {
        runAction("Undo", cb, () -> weApi().reduceTime(new UserMinutesRequest(userId, minutes)).execute(), "Took back " + minutes + " min");
    }

    public void timeOut(String userId, int minutes, ResultCallback cb) {
        runAction("Time-out", cb, () -> weApi().timeOutUser(new UserMinutesRequest(userId, minutes)).execute(), "Timed out for " + minutes + " min");
    }

    public void clearTimeOut(String userId, ResultCallback cb) {
        runAction("Clear time-out", cb, () -> weApi().clearTimeOut(new UserIdRequest(userId)).execute(), "Time-out ended");
    }

    public void resetTime(String userId, ResultCallback cb) {
        runAction("Reset", cb, () -> weApi().resetUserTime(new UserIdRequest(userId)).execute(), "Time reset");
    }

    /** Ends a time-out and adds time in one step. */
    public void resume(String userId, int minutes, ResultCallback cb) {
        actionExecutor.execute(() -> {
            try {
                Response<?> cleared = weApi().clearTimeOut(new UserIdRequest(userId)).execute();
                if (!cleared.isSuccessful()) {
                    report(cb, false, ErrorText.describeFailure("Clear time-out", cleared.code()));
                    return;
                }
                Response<?> extended = weApi().extendTime(new UserMinutesRequest(userId, minutes)).execute();
                if (!extended.isSuccessful()) {
                    report(cb, false, ErrorText.describeFailure("Extend", extended.code()));
                    return;
                }
                afterAction();
                report(cb, true, "Time-out ended, added " + minutes + " min");
            } catch (Exception e) {
                report(cb, false, "Resume failed. " + ErrorText.describeConnectionFailure(e));
            }
        });
    }

    public void stopStream(String sessionId, ResultCallback cb) {
        runAction("Stop stream", cb, () -> embyApi().stopStream(sessionId).execute(), "Stream stopped");
    }

    private interface Call {
        Response<?> run() throws Exception;
    }

    private void runAction(String what, ResultCallback cb, Call call, String successMessage) {
        actionExecutor.execute(() -> {
            try {
                Response<?> resp = call.run();
                if (resp.isSuccessful()) {
                    afterAction();
                    report(cb, true, successMessage);
                } else {
                    report(cb, false, ErrorText.describeFailure(what, resp.code()));
                }
            } catch (Exception e) {
                report(cb, false, what + " failed. " + ErrorText.describeConnectionFailure(e));
            }
        });
    }

    /** With the live connection up the change arrives by push; otherwise ask the server so the screen is not left stale. */
    private void afterAction() {
        boolean liveNow;
        synchronized (this) {
            liveNow = liveState == LiveConnection.State.LIVE;
        }
        if (!liveNow) worker.execute(this::refreshRest);
    }

    private void report(ResultCallback cb, boolean ok, String message) {
        if (cb != null) cb.done(ok, message);
    }

    private synchronized WatchingEyeApiService weApi() {
        if (weApi == null) throw new IllegalStateException("Not configured");
        return weApi;
    }

    private synchronized EmbyApi embyApi() {
        if (embyApi == null) throw new IllegalStateException("Not configured");
        return embyApi;
    }
}
