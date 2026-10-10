package com.example.watchingeye;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Watching Eye plugin routes. Authentication is done with request headers added by
 * {@link WatchingEyeRetrofitClient}: {@code X-Emby-Token} (an Emby administrator API key) and
 * {@code X-WatchingEye-Api-Key} (the key generated on the plugin page). No credentials are put in URLs,
 * because Emby writes request URLs to its log files.
 */
public interface WatchingEyeApiService {

    /** Public status route. Needs "Enable Public API" ticked on the plugin page and the plugin key. */
    @GET("/WatchingEye/Public/Status")
    Call<List<LimitedUserStatus>> getStatus();

    /** Same data the plugin page uses. Needs an Emby administrator API key. */
    @GET("/WatchingEye/LimitedUsersStatus")
    Call<List<LimitedUserStatus>> getLimitedUsersStatus();

    /** Minutes must be 1..43200 and the user must be a limited user. */
    @POST("/WatchingEye/Public/ExtendTime")
    Call<Void> extendTime(@Body UserMinutesRequest request);

    /** Takes back extra time (undo for ExtendTime). Plugin 1.8 or newer. */
    @POST("/WatchingEye/Public/ReduceTime")
    Call<Void> reduceTime(@Body UserMinutesRequest request);

    /** Minutes must be 1..43200; the limiter must be enabled and the user an enabled limited user. */
    @POST("/WatchingEye/Public/TimeOutUser")
    Call<Void> timeOutUser(@Body UserMinutesRequest request);

    @POST("/WatchingEye/Public/ClearTimeOut")
    Call<Void> clearTimeOut(@Body UserIdRequest request);

    @POST("/WatchingEye/Public/ResetUserTime")
    Call<Void> resetUserTime(@Body UserIdRequest request);
}
