package com.example.watchingeye;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static String baseUrl = "http://127.0.0.1:8096/";
    private static EmbyApi instance;

    public static void setBaseUrl(String url) {
        baseUrl = url.endsWith("/") ? url : url + "/";
        instance = null; // force rebuild on next get
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static synchronized EmbyApi getInstance() {
        if (instance == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
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