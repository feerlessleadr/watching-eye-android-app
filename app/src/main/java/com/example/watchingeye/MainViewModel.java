package com.example.watchingeye;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

public class MainViewModel extends ViewModel {

    private final MutableLiveData<List<UserCardInfo>> userCards = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> actionResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    private final ExecutorService pollExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicBoolean hasLoadedOnce = new AtomicBoolean(false);

    private String embyApiKey;
    private String watchingEyeApiKey;
    private String serverUrl;

    public LiveData<List<UserCardInfo>> getUserCards() { return userCards; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getActionResult() { return actionResult; }
    public LiveData<Boolean> getLoading() { return loading; }

    public void setCredentials(String serverUrl, String embyApiKey, String watchingEyeApiKey) {
        this.serverUrl = serverUrl;
        this.embyApiKey = embyApiKey;
        this.watchingEyeApiKey = watchingEyeApiKey;
        RetrofitClient.setBaseUrl(serverUrl);
    }

    public void startUpdates() {
        if (polling.getAndSet(true)) return;
        pollExecutor.execute(() -> {
            while (polling.get()) {
                fetchOnce();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    public void stopUpdates() {
        polling.set(false);
    }

    private void fetchOnce() {
        try {
            if (!hasLoadedOnce.get()) {
                loading.postValue(true);
            }

            WatchingEyeApiService weApi =
                    WatchingEyeRetrofitClient.getService(serverUrl, watchingEyeApiKey);

            List<LimitedUserStatus> limitedUsers = new ArrayList<>();

            Response<List<LimitedUserStatus>> statusResp =
                    weApi.getLimitedUsersStatus(embyApiKey).execute();

            if (!statusResp.isSuccessful() || statusResp.body() == null) {
                statusResp = weApi.getStatus(embyApiKey).execute();
            }

            if (statusResp.isSuccessful() && statusResp.body() != null) {
                limitedUsers = statusResp.body();
            } else if (!statusResp.isSuccessful()) {
                String err = "Status " + statusResp.code();
                if (statusResp.errorBody() != null) {
                    try { err += " " + statusResp.errorBody().string(); } catch (Exception ignored) {}
                }
                error.postValue(err);
            }

            EmbyApi embyApi = RetrofitClient.getInstance();
            Response<List<SessionInfo>> sessionsResp =
                    embyApi.getSessions(embyApiKey, "TranscodingInfo").execute();

            List<SessionInfo> sessions = new ArrayList<>();
            if (sessionsResp.isSuccessful() && sessionsResp.body() != null) {
                sessions = sessionsResp.body();
            }

            List<UserCardInfo> cards = new ArrayList<>();
            for (LimitedUserStatus user : limitedUsers) {
                SessionInfo match = null;
                for (SessionInfo s : sessions) {
                    if (s.getUserName() != null
                            && s.getUserName().equalsIgnoreCase(user.getUsername())
                            && s.getNowPlayingItem() != null) {
                        match = s;
                        break;
                    }
                }
                cards.add(new UserCardInfo(user, match));
            }

            userCards.postValue(cards);
            hasLoadedOnce.set(true);
        } catch (Exception e) {
            error.postValue("Error: " + e.getMessage());
        } finally {
            loading.postValue(false);
        }
    }

    public void extendTime(String userId, int minutes) {
        actionExecutor.execute(() -> {
            try {
                WatchingEyeApiService weApi =
                        WatchingEyeRetrofitClient.getService(serverUrl, watchingEyeApiKey);
                Response<?> resp = weApi.extendTime(
                        embyApiKey,
                        new UserMinutesRequest(userId, minutes)
                ).execute();

                if (resp.isSuccessful()) {
                    actionResult.postValue("Extended by " + minutes + " minutes");
                    fetchOnce();
                } else {
                    String err = "Extend failed HTTP " + resp.code();
                    if (resp.errorBody() != null) {
                        try { err += ": " + resp.errorBody().string(); } catch (Exception ignored) {}
                    }
                    error.postValue(err);
                }
            } catch (Exception e) {
                error.postValue("Extend error: " + e.getMessage());
            }
        });
    }

    public void timeOutUser(String userId, int minutes) {
        actionExecutor.execute(() -> {
            try {
                WatchingEyeApiService weApi =
                        WatchingEyeRetrofitClient.getService(serverUrl, watchingEyeApiKey);
                Response<?> resp = weApi.timeOutUser(
                        embyApiKey,
                        new UserMinutesRequest(userId, minutes)
                ).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("User timed out");
                    fetchOnce();
                } else {
                    error.postValue("Timeout failed: " + resp.code());
                }
            } catch (Exception e) {
                error.postValue("Timeout error: " + e.getMessage());
            }
        });
    }

    public void clearTimeOut(String userId) {
        actionExecutor.execute(() -> {
            try {
                WatchingEyeApiService weApi =
                        WatchingEyeRetrofitClient.getService(serverUrl, watchingEyeApiKey);
                Response<?> resp = weApi.clearTimeOut(
                        embyApiKey,
                        new UserIdRequest(userId)
                ).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("Timeout cleared");
                    fetchOnce();
                } else {
                    error.postValue("Clear timeout failed: " + resp.code());
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    public void resetUserTime(String userId) {
        actionExecutor.execute(() -> {
            try {
                WatchingEyeApiService weApi =
                        WatchingEyeRetrofitClient.getService(serverUrl, watchingEyeApiKey);
                Response<?> resp = weApi.resetUserTime(
                        embyApiKey,
                        new UserIdRequest(userId)
                ).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("Time reset");
                    fetchOnce();
                } else {
                    error.postValue("Reset failed: " + resp.code());
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    public void stopStream(String sessionId) {
        actionExecutor.execute(() -> {
            try {
                EmbyApi embyApi = RetrofitClient.getInstance();
                Response<?> resp = embyApi.stopStream(sessionId, embyApiKey).execute();
                if (resp.isSuccessful()) {
                    actionResult.postValue("Stream stopped");
                    fetchOnce();
                } else {
                    error.postValue("Stop stream failed: " + resp.code());
                }
            } catch (Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        stopUpdates();
        pollExecutor.shutdownNow();
        actionExecutor.shutdownNow();
        super.onCleared();
    }
}