package com.example.watchingeye;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static String baseUrl = "http://127.0.0.1:8096/";
    private static volatile String embyApiKey = "";
    private static EmbyApi instance;

    /** Sets the server address and the Emby administrator API key used for all Emby requests. */
    public static synchronized void configure(String url, String apiKey) {
        baseUrl = url.endsWith("/") ? url : url + "/";
        embyApiKey = apiKey != null ? apiKey : "";
        instance = null; // force rebuild on next get
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static synchronized EmbyApi getInstance() {
        if (instance == null) {
            OkHttpClient client = Http.newBuilder()
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("X-Emby-Token", embyApiKey)
                            .build()))
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            instance = retrofit.create(EmbyApi.class);
        }
        return instance;
    }
}
