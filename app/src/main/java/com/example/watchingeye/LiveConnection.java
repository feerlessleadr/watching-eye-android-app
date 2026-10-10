package com.example.watchingeye;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * The live connection to Emby's websocket. The server pushes the plugin's status and Emby's sessions to the app as
 * they change, so the app never has to ask. If the connection drops (or goes silent, which happens after the phone has
 * slept) it reconnects by itself with a growing delay.
 */
final class LiveConnection {

    enum State { STOPPED, CONNECTING, LIVE, RECONNECTING, REFUSED }

    interface Listener {
        void onStatus(List<LimitedUserStatus> users, long atMs);

        void onSessions(List<SessionInfo> sessions, long atMs);

        void onState(State state, String detail);
    }

    /** The plugin pushes at least every 30 s; if nothing at all arrives for this long the connection is considered dead. */
    static final long SILENCE_LIMIT_MS = 75_000L;
    private static final long[] BACKOFF_SECONDS = {1, 2, 4, 8, 15, 30, 60};
    private static final long REFUSED_RETRY_SECONDS = 60;
    private static final String DEVICE_ID = "watchingeye-android";

    private final OkHttpClient client;
    private final Listener listener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private String baseUrl;
    private String embyKey;
    private String pluginKey;

    private int generation = 0;          // every start/stop/reconnect bumps it so callbacks of an old socket are ignored
    private WebSocket socket;
    private State state = State.STOPPED;
    private int failures = 0;
    private long lastMessageMs = 0L;
    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> watchdogTask;
    private ScheduledFuture<?> keepAliveTask;

    LiveConnection(OkHttpClient base, Listener listener) {
        // A websocket stays open for hours: no read or call timeout, and a ping every 30 s keeps routers and proxies from dropping it.
        this.client = base.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        this.listener = listener;
    }

    private boolean sessionsWanted = false;

    /**
     * Also subscribe to Emby's own session list. Only needed with an older plugin (before 1.9) that does not report what is
     * playing; with a newer one the app never asks Emby for sessions, so a regular Emby account is enough.
     */
    synchronized void requestSessions() {
        if (sessionsWanted) return;
        sessionsWanted = true;
        if (socket != null) socket.send(LiveMessages.subscribeSessions());
    }

    synchronized State getState() {
        return state;
    }

    /** Connects (or reconnects from scratch). Safe to call repeatedly. */
    synchronized void start(String baseUrl, String embyKey, String pluginKey) {
        this.baseUrl = baseUrl;
        this.embyKey = embyKey;
        this.pluginKey = pluginKey;
        failures = 0;
        connect();
    }

    synchronized void stop() {
        generation++;
        cancelTimers();
        closeSocket();
        setState(State.STOPPED, "");
    }

    /** Drops the current connection and connects again at once (after a network change or a long sleep). */
    synchronized void reconnectNow() {
        if (state == State.STOPPED || baseUrl == null) return;
        failures = 0;
        connect();
    }

    synchronized void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    private void connect() {
        generation++;
        final int gen = generation;
        cancelTimers();
        closeSocket();
        setState(failures == 0 && state != State.LIVE ? State.CONNECTING : State.RECONNECTING, state == State.RECONNECTING ? lastDetail : "");
        Request request;
        try {
            request = new Request.Builder()
                    .url(LiveMessages.webSocketUrl(baseUrl, DEVICE_ID))
                    .header("X-Emby-Token", embyKey)
                    .build();
        } catch (RuntimeException e) {
            scheduleReconnect(gen, "The server address is not valid (" + e.getClass().getSimpleName() + ").");
            return;
        }
        lastMessageMs = System.currentTimeMillis();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                synchronized (LiveConnection.this) {
                    if (gen != generation) return;
                    webSocket.send(LiveMessages.subscribeStatus(pluginKey));
                    if (sessionsWanted) webSocket.send(LiveMessages.subscribeSessions());
                    startWatchdog(gen);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handle(gen, text);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                lost(gen, "The connection was closed (" + code + ").");
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String what = response != null ? "HTTP " + response.code() : ErrorText.describeConnectionFailure(t);
                lost(gen, what);
            }
        });
    }

    private String lastDetail = "";

    private void handle(int gen, String text) {
        LiveMessages.Message m = LiveMessages.parse(text);
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (gen != generation) return;
            lastMessageMs = now;
            switch (m.kind) {
                case STATUS:
                    if (m.error != null) {
                        // The plugin does not accept this key: reconnecting quickly would only count as more failed attempts.
                        failures = 0;
                        closeSocket();
                        cancelTimers();
                        setState(State.REFUSED, m.error);
                        reconnectTask = scheduler.schedule(() -> restartAfterRefusal(gen), REFUSED_RETRY_SECONDS, TimeUnit.SECONDS);
                        return;
                    }
                    failures = 0;
                    setState(State.LIVE, "");
                    break;
                case KEEP_ALIVE_REQUEST:
                    startKeepAlive(gen, m.keepAliveSeconds);
                    if (socket != null) socket.send(LiveMessages.keepAlive());
                    return;
                default:
                    break;
            }
        }
        // Outside the lock: the listener may take its own locks.
        if (m.kind == LiveMessages.Kind.STATUS) {
            listener.onStatus(m.users, now);
        } else if (m.kind == LiveMessages.Kind.SESSIONS) {
            listener.onSessions(m.sessions, now);
        }
    }

    private synchronized void restartAfterRefusal(int gen) {
        if (gen != generation) return;
        failures = 0;
        connect();
    }

    private synchronized void lost(int gen, String detail) {
        if (gen != generation) return;
        scheduleReconnect(gen, detail);
    }

    private void scheduleReconnect(int gen, String detail) {
        cancelTimers();
        closeSocket();
        lastDetail = detail;
        long delay = BACKOFF_SECONDS[Math.min(failures, BACKOFF_SECONDS.length - 1)];
        failures++;
        setState(State.RECONNECTING, detail);
        reconnectTask = scheduler.schedule(() -> {
            synchronized (LiveConnection.this) {
                if (gen != generation) return;
                connect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void startWatchdog(int gen) {
        watchdogTask = scheduler.scheduleWithFixedDelay(() -> {
            synchronized (LiveConnection.this) {
                if (gen != generation) return;
                if (System.currentTimeMillis() - lastMessageMs > SILENCE_LIMIT_MS) {
                    scheduleReconnect(gen, "No news from the server for over a minute.");
                }
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void startKeepAlive(int gen, int seconds) {
        if (keepAliveTask != null) keepAliveTask.cancel(false);
        long period = Math.max(10, seconds > 0 ? seconds / 2 : 30);
        keepAliveTask = scheduler.scheduleWithFixedDelay(() -> {
            synchronized (LiveConnection.this) {
                if (gen == generation && socket != null) socket.send(LiveMessages.keepAlive());
            }
        }, period, period, TimeUnit.SECONDS);
    }

    private void cancelTimers() {
        if (reconnectTask != null) reconnectTask.cancel(false);
        if (watchdogTask != null) watchdogTask.cancel(false);
        if (keepAliveTask != null) keepAliveTask.cancel(false);
        reconnectTask = null;
        watchdogTask = null;
        keepAliveTask = null;
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.cancel();
            } catch (RuntimeException ignored) {
                // already closed
            }
            socket = null;
        }
    }

    private void setState(State newState, String detail) {
        if (state == newState && detail.equals(lastDetail)) return;
        state = newState;
        lastDetail = detail;
        final State s = newState;
        final String d = detail;
        // Notify on the scheduler thread so a slow listener never blocks the socket.
        try {
            scheduler.execute(() -> listener.onState(s, d));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }
}
