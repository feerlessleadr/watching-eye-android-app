package com.example.watchingeye;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity implements UserCardAdapter.Listener {

    private MainViewModel viewModel;
    private UserCardAdapter adapter;
    private ProgressBar progressBar;

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
            }
        });

        viewModel.getActionResult().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        applyCredentialsAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCredentialsAndStart();
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
        viewModel.stopUpdates();
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
            applyCredentialsAndStart();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null) viewModel.stopUpdates();
        super.onDestroy();
    }
}