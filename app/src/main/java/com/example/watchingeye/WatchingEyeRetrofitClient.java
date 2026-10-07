package com.example.watchingeye;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WatchingEyeRetrofitClient {

    public static WatchingEyeApiService getService(String baseUrl, final String pluginApiKey) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("X-WatchingEye-Api-Key", pluginApiKey != null ? pluginApiKey : "")
                            .build();
                    return chain.proceed(request);
                })
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // Never log bodies in release — they include API keys
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