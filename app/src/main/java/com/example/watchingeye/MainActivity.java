package com.example.watchingeye;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity implements UserCardAdapter.Listener {

    private MainViewModel viewModel;
    private UserCardAdapter adapter;
    private ProgressBar progressBar;
    private TextView connectionBanner;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setTitle("Watching Eye");
        }

        progressBar = findViewById(R.id.progressBar);
        RecyclerView recyclerView = findViewById(R.id.sessionsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new UserCardAdapter(this);
        recyclerView.setAdapter(adapter);
        // Cards are refreshed in place every few seconds; the default cross-fade on each refresh looks like flicker.
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        viewModel.getUserCards().observe(this, cards -> {
            if (cards != null) {
                adapter.submitList(cards);
            }
        });

        viewModel.getLoading().observe(this, loading -> {
            if (progressBar != null) {
                progressBar.setVisibility(loading != null && loading ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                viewModel.consumeMessages();
            }
        });

        viewModel.getActionResult().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.consumeMessages();
            }
        });

        // Connection trouble is shown as a banner above the (still visible) last-known data, not as a pop-up per refresh.
        connectionBanner = findViewById(R.id.connectionBanner);
        viewModel.getConnectionProblem().observe(this, problem -> {
            if (connectionBanner == null) return;
            if (problem == null || problem.isEmpty()) {
                connectionBanner.setVisibility(View.GONE);
            } else {
                connectionBanner.setText(problem);
                connectionBanner.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Poll only while the screen is visible; every time it becomes visible start from scratch with fresh connections. */
    @Override
    protected void onStart() {
        super.onStart();
        applyCredentialsAndStart();
        registerNetworkCallback();
    }

    @Override
    protected void onStop() {
        unregisterNetworkCallback();
        viewModel.stopUpdates();
        super.onStop();
    }

    /** When the phone changes network (Wi-Fi to mobile, VPN, coming back online) retry straight away on a fresh connection. */
    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || networkCallback != null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                viewModel.startUpdates();
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            networkCallback = null; // not essential: polling still retries on its own
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

    private void applyCredentialsAndStart() {
        SharedPreferences prefs = SecurePrefs.get(this);
        String serverUrl = prefs.getString(SecurePrefs.KEY_SERVER_URL, "");
        String embyKey = prefs.getString(SecurePrefs.KEY_EMBY_API, "");
        String weKey = prefs.getString(SecurePrefs.KEY_WE_API, "");

        if (serverUrl == null) serverUrl = "";
        if (embyKey == null) embyKey = "";
        if (weKey == null) weKey = "";

        serverUrl = serverUrl.trim();
        embyKey = embyKey.trim();
        weKey = weKey.trim();

        if (serverUrl.isEmpty() || embyKey.isEmpty() || weKey.isEmpty()) {
            Toast.makeText(this, "Please configure server settings", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        if (serverUrl.toLowerCase(java.util.Locale.ROOT).startsWith("http://")) {
            // This app does not allow plain-HTTP connections (usesCleartextTraffic=false), so the requests below would fail.
            Toast.makeText(this,
                    "This app only allows secure connections. Use an https:// address for your Emby server in Settings.",
                    Toast.LENGTH_LONG).show();
        }

        viewModel.setCredentials(serverUrl, embyKey, weKey);
        adapter.setServerInfo(serverUrl, embyKey);
        viewModel.startUpdates();
    }

    @Override
    public void onExtendClick(LimitedUserStatus user, int minutes) {
        viewModel.extendTime(user.getUserId(), minutes);
    }

    @Override
    public void onTimeoutClick(LimitedUserStatus user, int minutes) {
        viewModel.timeOutUser(user.getUserId(), minutes);
    }

    @Override
    public void onClearTimeoutClick(LimitedUserStatus user) {
        viewModel.clearTimeOut(user.getUserId());
    }

    @Override
    public void onResetTimeClick(LimitedUserStatus user) {
        viewModel.resetUserTime(user.getUserId());
    }

    @Override
    public void onStopStreamClick(SessionInfo session) {
        if (session != null && session.getId() != null) {
            viewModel.stopStream(session.getId());
        }
    }

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
            viewModel.stopUpdates(); // a manual refresh always restarts, even right after a start
            applyCredentialsAndStart();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}