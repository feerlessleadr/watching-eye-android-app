package com.example.watchingeye;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.function.IntConsumer;

public class MainActivity extends LockedActivity implements UserCardAdapter.Listener {

    private static final long TICK_MS = 1000L;

    private MainViewModel viewModel;
    private UserCardAdapter adapter;
    private ProgressBar progressBar;
    private TextView connectionBanner;
    private TextView emptyView;
    private MaterialToolbar toolbar;
    private SwipeRefreshLayout swipeRefresh;
    private View rootView;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean firstNetworkCallback;
    private StatusRepository.ConnectionInfo lastConnection;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            adapter.tick();
            updateSubtitle();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            setTitle("Watching Eye");
        }

        rootView = findViewById(R.id.main);
        progressBar = findViewById(R.id.progressBar);
        connectionBanner = findViewById(R.id.connectionBanner);
        emptyView = findViewById(R.id.emptyView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = findViewById(R.id.sessionsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new UserCardAdapter(this);
        recyclerView.setAdapter(adapter);
        // Cards are updated in place; the default cross-fade on each update would look like flicker.
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.refreshNow();
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 800);
        });

        StatusRepository.get(this); // gives the repository an application context (for the widget)
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        viewModel.getCards().observe(this, cards -> {
            if (cards == null) return;
            adapter.submitList(cards, adapter::tick);
            updateEmptyState(cards);
        });

        viewModel.getConnection().observe(this, info -> {
            lastConnection = info;
            showConnection(info);
            updateSubtitle();
            updateEmptyState(viewModel.getCards().getValue());
        });

        viewModel.getEvents().observe(this, event -> {
            if (event == null) return;
            showEvent(event);
            viewModel.consumeEvent();
        });
    }

    /** Live data only flows while the screen is visible: on the way in connect afresh, on the way out hang up. */
    @Override
    protected void onUnlocked() {
        if (SecurePrefs.takeWasReset()) {
            Toast.makeText(this, "The saved settings could not be read and were cleared. Please enter them again.", Toast.LENGTH_LONG).show();
        }
        if (SecurePrefs.isUnavailable()) {
            Toast.makeText(this, "Secure storage is not available on this phone, so settings are kept only until the app closes.", Toast.LENGTH_LONG).show();
        }
        applyCredentialsAndStart();
        registerNetworkCallback();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(ticker);
        unregisterNetworkCallback();
        viewModel.onHidden();
        super.onStop();
    }

    private void applyCredentialsAndStart() {
        SharedPreferences prefs = SecurePrefs.get(this);
        String serverUrl = trimmed(prefs.getString(SecurePrefs.KEY_SERVER_URL, ""));
        String embyKey = trimmed(prefs.getString(SecurePrefs.KEY_EMBY_API, ""));
        String weKey = trimmed(prefs.getString(SecurePrefs.KEY_WE_API, ""));

        if (serverUrl.isEmpty() || embyKey.isEmpty() || weKey.isEmpty()) {
            Toast.makeText(this, "Please configure server settings", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        if (!ServerUrl.isValid(serverUrl)) {
            Toast.makeText(this, "The server address is not valid. It must look like https://emby.example.com", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        if (serverUrl.toLowerCase(java.util.Locale.ROOT).startsWith("http://") && !BuildConfig.DEBUG) {
            // This app does not allow plain-HTTP connections (usesCleartextTraffic=false), so the requests below would fail.
            Toast.makeText(this,
                    "This app only allows secure connections. Use an https:// address for your Emby server in Settings.",
                    Toast.LENGTH_LONG).show();
        }

        adapter.setServerInfo(serverUrl, embyKey);
        viewModel.onVisible(new StatusRepository.Credentials(serverUrl, embyKey, weKey));
        AlertService.syncWithSetting(this);
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    // ---- connection state ---------------------------------------------------------------------------------

    private void showConnection(StatusRepository.ConnectionInfo info) {
        if (info == null) return;
        progressBar.setVisibility(info.link == StatusRepository.Link.CONNECTING ? View.VISIBLE : View.GONE);
        switch (info.link) {
            case LIVE:
            case CONNECTING:
                connectionBanner.setVisibility(View.GONE);
                break;
            case POLLING:
                connectionBanner.setBackgroundColor(0xFF7A5200);
                connectionBanner.setText("Live updates are not connected, so the app checks every 20 seconds. " + info.detail);
                connectionBanner.setVisibility(View.VISIBLE);
                break;
            default:
                connectionBanner.setBackgroundColor(getColor(R.color.we_banner));
                connectionBanner.setText(info.detail.isEmpty() ? "Can't reach the server. Retrying..." : info.detail + " Retrying...");
                connectionBanner.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateSubtitle() {
        if (toolbar == null || lastConnection == null) return;
        String text;
        switch (lastConnection.link) {
            case LIVE:
                text = "● Live";
                break;
            case CONNECTING:
                text = "Connecting…";
                break;
            default:
                if (lastConnection.lastDataMs <= 0) {
                    text = "No data yet";
                } else {
                    long seconds = Math.max(0, (System.currentTimeMillis() - lastConnection.lastDataMs) / 1000);
                    text = "Updated " + (seconds < 60 ? seconds + " s" : (seconds / 60) + " min") + " ago";
                }
                break;
        }
        toolbar.setSubtitle(text);
    }

    private void updateEmptyState(List<UserCardInfo> cards) {
        boolean empty = cards == null || cards.isEmpty();
        boolean connecting = lastConnection == null || lastConnection.link == StatusRepository.Link.CONNECTING;
        boolean offline = lastConnection != null && lastConnection.link == StatusRepository.Link.OFFLINE;
        emptyView.setVisibility(empty && !connecting && !offline ? View.VISIBLE : View.GONE);
    }

    private void showEvent(MainViewModel.UiEvent event) {
        Snackbar bar = Snackbar.make(rootView, event.message, event.undo != null ? 8000 : (event.error ? 6000 : 3000));
        if (event.undo != null) {
            bar.setAction(event.undoLabel, v -> event.undo.run());
        }
        if (event.error) {
            bar.setTextMaxLines(6);
        }
        bar.show();
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || networkCallback != null) return;
        firstNetworkCallback = true;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // The first call comes straight away on registering; later calls mean the phone changed network.
                if (firstNetworkCallback) {
                    firstNetworkCallback = false;
                    return;
                }
                viewModel.refreshNow();
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            networkCallback = null; // not essential: the live connection also notices on its own
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
            // already unregistered
        }
        networkCallback = null;
    }

    // ---- card actions -------------------------------------------------------------------------------------

    @Override
    public void onAddTime(LimitedUserStatus user, int minutes) {
        viewModel.extend(user, minutes);
    }

    @Override
    public void onAddTimeOther(LimitedUserStatus user) {
        askMinutes("Add time for " + user.getUsername(), "How many minutes should they get?", 45, "Add", minutes -> viewModel.extend(user, minutes));
    }

    @Override
    public void onTimeOut(LimitedUserStatus user) {
        askMinutes("Time out " + user.getUsername(), "Playback stops right away and is blocked for this many minutes.", 15, "Time out",
                minutes -> viewModel.timeOut(user, minutes));
    }

    @Override
    public void onEndTimeOut(LimitedUserStatus user) {
        viewModel.clearTimeOut(user);
    }

    @Override
    public void onResume(LimitedUserStatus user, int minutes) {
        viewModel.resume(user, minutes);
    }

    @Override
    public void onResetTime(LimitedUserStatus user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset " + user.getUsername() + "'s time?")
                .setMessage("The time used (day, week, month and year) and any extra time go back to zero.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> viewModel.resetTime(user))
                .show();
    }

    @Override
    public void onStopStream(LimitedUserStatus user, SessionInfo session) {
        if (session == null || session.getId() == null) return;
        String title = session.getNowPlayingItem() != null && session.getNowPlayingItem().getName() != null
                ? session.getNowPlayingItem().getName() : "the stream";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Stop " + user.getUsername() + "'s stream?")
                .setMessage("This stops \"" + title + "\" now. It does not block them from starting it again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Stop", (d, w) -> viewModel.stopStream(user, session.getId()))
                .show();
    }

    /** A small dialog asking for a number of minutes (1 to 43200). */
    private void askMinutes(String title, String message, int defaultMinutes, String positive, IntConsumer onChosen) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint("Minutes");
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(defaultMinutes));
        input.setSelectAllOnFocus(true);
        layout.addView(input);
        FrameLayout holder = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(layout);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(holder)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(positive, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int minutes = parseMinutes(input.getText() == null ? "" : input.getText().toString());
            if (minutes < 1) {
                layout.setError("Enter 1 to " + ErrorText.MAX_MINUTES);
                return;
            }
            dialog.dismiss();
            onChosen.accept(minutes);
        }));
        dialog.show();
    }

    /** Minutes from typed text: 1..43200, or -1 when it is not a usable number. */
    static int parseMinutes(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 1 && value <= ErrorText.MAX_MINUTES ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- menu -------------------------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Settings");
        menu.add(0, 2, 0, "Refresh");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == 2) {
            viewModel.refreshNow();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
