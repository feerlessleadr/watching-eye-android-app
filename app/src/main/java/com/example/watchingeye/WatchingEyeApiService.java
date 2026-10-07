package com.example.watchingeye;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface WatchingEyeApiService {

    /** Public status (may omit credit fields) */
    @GET("/WatchingEye/Public/Status")
    Call<List<LimitedUserStatus>> getStatus(@Query("api_key") String embyApiKey);

    /**
     * Same data the Emby plugin WebUI uses — often includes CreditSecondsDaily.
     * Authenticated with Emby API key.
     */
    @GET("/WatchingEye/LimitedUsersStatus")
    Call<List<LimitedUserStatus>> getLimitedUsersStatus(@Query("api_key") String embyApiKey);

    @POST("/WatchingEye/Public/ExtendTime")
    Call<Void> extendTime(@Query("api_key") String embyApiKey, @Body UserMinutesRequest request);

    @POST("/WatchingEye/Public/TimeOutUser")
    Call<Void> timeOutUser(@Query("api_key") String embyApiKey, @Body UserMinutesRequest request);

    @POST("/WatchingEye/Public/ClearTimeOut")
    Call<Void> clearTimeOut(@Query("api_key") String embyApiKey, @Body UserIdRequest request);

    @POST("/WatchingEye/Public/ResetUserTime")
    Call<Void> resetUserTime(@Query("api_key") String embyApiKey, @Body UserIdRequest request);
}