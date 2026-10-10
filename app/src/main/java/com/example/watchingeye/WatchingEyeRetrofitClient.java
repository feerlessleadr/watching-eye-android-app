package com.example.watchingeye;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WatchingEyeRetrofitClient {

    /**
     * @param baseUrl      Emby server address, e.g. https://emby.example.com
     * @param pluginApiKey key generated on the Watching Eye plugin page
     * @param embyApiKey   Emby administrator API key (Dashboard -> API Keys)
     */
    public static WatchingEyeApiService getService(String baseUrl, final String pluginApiKey, final String embyApiKey) {
        OkHttpClient.Builder clientBuilder = Http.newBuilder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("X-WatchingEye-Api-Key", pluginApiKey != null ? pluginApiKey : "")
                            .header("X-Emby-Token", embyApiKey != null ? embyApiKey : "")
                            .build();
                    return chain.proceed(request);
                });

        // BASIC logs only the method, URL and status code (never headers or bodies).
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            clientBuilder.addInterceptor(logging);
        }

        String url = (baseUrl == null || baseUrl.isEmpty())
                ? "http://127.0.0.1:8096/"
                : (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(WatchingEyeApiService.class);
    }
}
