package com.example.watchingeye;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface EmbyApi {

    @GET("emby/Sessions")
    Call<List<SessionInfo>> getSessions(
            @Query("api_key") String apiKey,
            @Query("Fields") String fields
    );

    @POST("emby/Sessions/{sessionId}/Playing/Stop")
    Call<Void> stopStream(
            @Path("sessionId") String sessionId,
            @Query("api_key") String apiKey
    );

    @POST("emby/Sessions/{sessionId}/Message")
    Call<Void> sendMessage(
            @Path("sessionId") String sessionId,
            @Query("api_key") String apiKey,
            @Body MessageCommand message
    );
}