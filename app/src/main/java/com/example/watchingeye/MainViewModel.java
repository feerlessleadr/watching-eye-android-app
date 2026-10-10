package com.example.watchingeye;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

/** The main screen's view of {@link StatusRepository}, plus the actions with their confirmations and Undo. */
public class MainViewModel extends ViewModel {

    /** A message for the screen: shown once, optionally with an Undo button. */
    public static final class UiEvent {
        public final String message;
        public final boolean error;
        public final String undoLabel;
        public final Runnable undo;

        UiEvent(String message, boolean error, String undoLabel, Runnable undo) {
            this.message = message;
            this.error = error;
            this.undoLabel = undoLabel;
            this.undo = undo;
        }
    }

    private final StatusRepository repository = StatusRepository.get(null);
    private final MutableLiveData<UiEvent> events = new MutableLiveData<>();
    private boolean holding = false;
    private StatusRepository.Credentials held;

    public LiveData<List<UserCardInfo>> getCards() { return repository.getCards(); }
    public LiveData<StatusRepository.ConnectionInfo> getConnection() { return repository.getConnection(); }
    public LiveData<UiEvent> getEvents() { return events; }

    /** Call when the screen becomes visible. */
    public synchronized void onVisible(StatusRepository.Credentials credentials) {
        if (holding && credentials.equals(held)) {
            return;
        }
        if (holding) {
            repository.release();
        }
        repository.acquire(credentials);
        held = credentials;
        holding = true;
    }

    /** Call when the screen is no longer visible. */
    public synchronized void onHidden() {
        if (holding) {
            repository.release();
            holding = false;
        }
    }

    public void refreshNow() {
        repository.refreshNow();
    }

    public void consumeEvent() {
        events.setValue(null);
    }

    private void tell(boolean ok, String message) {
        events.postValue(new UiEvent(message, !ok, null, null));
    }

    // ---- actions --------------------------------------------------------------------------------------------

    public void extend(LimitedUserStatus user, int minutes) {
        if (!validMinutes(minutes)) return;
        String userId = user.getUserId();
        String name = user.getUsername();
        repository.extend(userId, minutes, (ok, message) -> {
            if (ok) {
                events.postValue(new UiEvent(message + " for " + name, false, "UNDO",
                        () -> repository.reduce(userId, minutes, (ok2, message2) -> tell(ok2, ok2 ? "Undone for " + name : message2))));
            } else {
                tell(false, message);
            }
        });
    }

    public void timeOut(LimitedUserStatus user, int minutes) {
        if (!validMinutes(minutes)) return;
        String userId = user.getUserId();
        String name = user.getUsername();
        repository.timeOut(userId, minutes, (ok, message) -> {
            if (ok) {
                events.postValue(new UiEvent(name + ": " + message, false, "UNDO",
                        () -> repository.clearTimeOut(userId, (ok2, message2) -> tell(ok2, ok2 ? "Time-out ended for " + name : message2))));
            } else {
                tell(false, message);
            }
        });
    }

    public void clearTimeOut(LimitedUserStatus user) {
        repository.clearTimeOut(user.getUserId(), (ok, message) -> tell(ok, ok ? message + " for " + user.getUsername() : message));
    }

    /** Ends a time-out and adds time in one step. */
    public void resume(LimitedUserStatus user, int minutes) {
        if (!validMinutes(minutes)) return;
        repository.resume(user.getUserId(), minutes, (ok, message) -> tell(ok, ok ? message + " for " + user.getUsername() : message));
    }

    public void resetTime(LimitedUserStatus user) {
        repository.resetTime(user.getUserId(), (ok, message) -> tell(ok, ok ? message + " for " + user.getUsername() : message));
    }

    public void stopStream(String sessionId) {
        repository.stopStream(sessionId, (ok, message) -> tell(ok, message));
    }

    private boolean validMinutes(int minutes) {
        if (minutes < 1 || minutes > ErrorText.MAX_MINUTES) {
            tell(false, "Minutes must be between 1 and " + ErrorText.MAX_MINUTES + ".");
            return false;
        }
        return true;
    }

    @Override
    protected void onCleared() {
        onHidden();
        super.onCleared();
    }
}
