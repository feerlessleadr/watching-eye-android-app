package com.example.watchingeye;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * One shared HTTP client (one connection pool, one set of threads) for every request the app makes. The Emby and
 * plugin clients are derived from it with {@code newBuilder()}, which keeps sharing the pool.
 */
final class Http {

    private static final OkHttpClient BASE = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private Http() {}

    static OkHttpClient.Builder newBuilder() {
        return BASE.newBuilder();
    }

    /** Drops pooled connections and cancels running calls, so the next request opens a fresh connection (after a long sleep or a network change the old ones are dead). */
    static void resetConnections() {
        BASE.connectionPool().evictAll();
        BASE.dispatcher().cancelAll();
    }
}
