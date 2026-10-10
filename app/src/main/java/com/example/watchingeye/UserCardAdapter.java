package com.example.watchingeye;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class UserCardAdapter extends ListAdapter<UserCardInfo, UserCardAdapter.CardViewHolder> {

    public interface Listener {
        void onExtendClick(LimitedUserStatus user, int minutes);
        void onTimeoutClick(LimitedUserStatus user, int minutes);
        void onClearTimeoutClick(LimitedUserStatus user);
        void onResetTimeClick(LimitedUserStatus user);
        void onStopStreamClick(SessionInfo session);
    }

    private final Listener listener;
    private String serverUrl;
    private String embyApiKey;

    public UserCardAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setServerInfo(String serverUrl, String embyApiKey) {
        this.serverUrl = serverUrl;
        this.embyApiKey = embyApiKey;
    }

    private static final DiffUtil.ItemCallback<UserCardInfo> DIFF =
            new DiffUtil.ItemCallback<UserCardInfo>() {
                @Override
                public boolean areItemsTheSame(@NonNull UserCardInfo a, @NonNull UserCardInfo b) {
                    return a.getUserStatus().getUserId().equals(b.getUserStatus().getUserId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull UserCardInfo a, @NonNull UserCardInfo b) {
                    return UserCardInfo.sameDisplay(a, b);
                }
            };

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.user_card_list_item, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    private GlideUrl embyImageUrl(String path) {
        String base = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        String url = base + path;
        return new GlideUrl(url, new LazyHeaders.Builder()
                .addHeader("X-Emby-Token", embyApiKey != null ? embyApiKey : "")
                .build());
    }

    class CardViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView userNameTextView;
        private final TextView dailyLimitTextView;
        private final TextView timeoutStatusTextView;
        private final EditText extendMinutesEditText;
        private final Button extendButton;
        private final EditText timeoutMinutesEditText;
        private final Button timeoutButton;
        private final Button clearTimeoutButton;
        private final Button resetTimeButton;
        private final LinearLayout nowPlayingSection;
        private final ImageView posterImageView;
        private final TextView nowPlayingTextView;
        private final TextView progressTextView;
        private final ProgressBar playbackProgressBar;
        private final TextView clientDeviceTextView;
        private final TextView ipAddressTextView;
        private final Button stopStreamButton;

        private UserCardInfo current;

        CardViewHolder(View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            userNameTextView = itemView.findViewById(R.id.userNameTextView);
            dailyLimitTextView = itemView.findViewById(R.id.dailyLimitTextView);
            timeoutStatusTextView = itemView.findViewById(R.id.timeoutStatusTextView);
            extendMinutesEditText = itemView.findViewById(R.id.extendMinutesEditText);
            extendButton = itemView.findViewById(R.id.extendButton);
            timeoutMinutesEditText = itemView.findViewById(R.id.timeoutMinutesEditText);
            timeoutButton = itemView.findViewById(R.id.timeoutButton);
            clearTimeoutButton = itemView.findViewById(R.id.clearTimeoutButton);
            resetTimeButton = itemView.findViewById(R.id.resetTimeButton);
            nowPlayingSection = itemView.findViewById(R.id.nowPlayingSection);
            posterImageView = itemView.findViewById(R.id.posterImageView);
            nowPlayingTextView = itemView.findViewById(R.id.nowPlayingTextView);
            progressTextView = itemView.findViewById(R.id.progressTextView);
            playbackProgressBar = itemView.findViewById(R.id.playbackProgressBar);
            clientDeviceTextView = itemView.findViewById(R.id.clientDeviceTextView);
            ipAddressTextView = itemView.findViewById(R.id.ipAddressTextView);
            stopStreamButton = itemView.findViewById(R.id.stopStreamButton);

            extendButton.setOnClickListener(v -> {
                if (current == null || listener == null) return;
                listener.onExtendClick(current.getUserStatus(), parseMinutes(extendMinutesEditText, 30));
            });

            timeoutButton.setOnClickListener(v -> {
                if (current == null || listener == null) return;
                listener.onTimeoutClick(current.getUserStatus(), parseMinutes(timeoutMinutesEditText, 15));
            });

            clearTimeoutButton.setOnClickListener(v -> {
                if (current == null || listener == null) return;
                listener.onClearTimeoutClick(current.getUserStatus());
            });

            resetTimeButton.setOnClickListener(v -> {
                if (current == null || listener == null) return;
                listener.onResetTimeClick(current.getUserStatus());
            });

            stopStreamButton.setOnClickListener(v -> {
                if (current == null || listener == null) return;
                SessionInfo session = current.getSessionInfo();
                if (session != null && session.getId() != null) {
                    listener.onStopStreamClick(session);
                }
            });
        }

        void bind(UserCardInfo card) {
            current = card;
            LimitedUserStatus status = card.getUserStatus();
            SessionInfo session = card.getSessionInfo();

            String name = status.getUsername() != null ? status.getUsername() : "Unknown";
            userNameTextView.setText(status.isEnabled() ? name : name + " (limit disabled)");

            long usedSec = (long) status.getSecondsWatchedDaily();
            long usedMin = TimeUnit.SECONDS.toMinutes(usedSec);
            long allowedMin = TimeUnit.SECONDS.toMinutes(status.getEffectiveAllowedSecondsDaily());
            if (status.getEffectiveDailyLimitMinutes() <= 0) {
                dailyLimitTextView.setText("Daily time used: " + formatMin(usedMin) + " (no daily limit)");
            } else {
                dailyLimitTextView.setText(
                        "Daily time limit used: " + formatMin(usedMin) + " / " + formatMin(allowedMin)
                );
            }
            dailyLimitTextView.setTextColor(0xFFB0B0B0);

            // Show a time-out only while it is still active, in the phone's local time.
            Instant timeoutEnd = TimeOutFormat.activeEnd(status.getTimeOutUntil(), Instant.now());
            boolean hasTimeout = timeoutEnd != null;
            if (hasTimeout) {
                timeoutStatusTextView.setVisibility(View.VISIBLE);
                timeoutStatusTextView.setText("Timed out until "
                        + TimeOutFormat.describe(timeoutEnd, ZoneId.systemDefault(), Locale.getDefault()));
                clearTimeoutButton.setVisibility(View.VISIBLE);
            } else {
                timeoutStatusTextView.setVisibility(View.GONE);
                clearTimeoutButton.setVisibility(View.GONE);
            }

            if (serverUrl != null && embyApiKey != null && status.getUserId() != null) {
                String avatarPath = "Users/" + status.getUserId() + "/Images/Primary?maxHeight=80&quality=90";
                // Only (re)load when the picture actually changed: reloading the same image shows the placeholder for a moment.
                if (!(serverUrl + avatarPath).equals(avatarImageView.getTag())) {
                    avatarImageView.setTag(serverUrl + avatarPath);
                    Glide.with(itemView.getContext())
                            .load(embyImageUrl(avatarPath))
                            .transform(new CircleCrop())
                            .placeholder(android.R.drawable.ic_menu_myplaces)
                            .error(android.R.drawable.ic_menu_myplaces)
                            .into(avatarImageView);
                }
            }

            if (card.isPlaying() && session != null) {
                nowPlayingSection.setVisibility(View.VISIBLE);

                String title = "Unknown";
                String itemId = null;
                if (session.getNowPlayingItem() != null) {
                    if (session.getNowPlayingItem().getName() != null) {
                        title = session.getNowPlayingItem().getName();
                    }
                    itemId = session.getNowPlayingItem().getId();
                }
                nowPlayingTextView.setText("Playing: " + title);

                if (serverUrl != null && embyApiKey != null && itemId != null) {
                    String posterPath = "Items/" + itemId + "/Images/Primary?maxHeight=200&quality=90";
                    posterImageView.setVisibility(View.VISIBLE);
                    if (!(serverUrl + posterPath).equals(posterImageView.getTag())) {
                        posterImageView.setTag(serverUrl + posterPath);
                        Glide.with(itemView.getContext())
                                .load(embyImageUrl(posterPath))
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_gallery)
                                .into(posterImageView);
                    }
                } else {
                    posterImageView.setVisibility(View.GONE);
                }

                long positionTicks = 0;
                long runtimeTicks = 1;
                if (session.getPlayState() != null && session.getPlayState().getPositionTicks() != null) {
                    positionTicks = session.getPlayState().getPositionTicks();
                }
                if (session.getNowPlayingItem() != null && session.getNowPlayingItem().getRunTimeTicks() != null
                        && session.getNowPlayingItem().getRunTimeTicks() > 0) {
                    runtimeTicks = session.getNowPlayingItem().getRunTimeTicks();
                }
                int pct = (int) Math.min(100, Math.max(0, (positionTicks * 100) / runtimeTicks));

                progressTextView.setText(formatTicks(positionTicks) + " / " + formatTicks(runtimeTicks)
                        + "  (" + pct + "%)");
                playbackProgressBar.setMax(100);
                playbackProgressBar.setProgress(pct);

                String client = session.getClient() != null ? session.getClient() : "Unknown";
                String device = session.getDeviceName() != null ? session.getDeviceName() : "Device";
                clientDeviceTextView.setText(client + " · " + device);

                ipAddressTextView.setVisibility(View.GONE);
                stopStreamButton.setVisibility(View.VISIBLE);
            } else {
                nowPlayingSection.setVisibility(View.GONE);
                posterImageView.setVisibility(View.GONE);
            }
        }

        private int parseMinutes(EditText editText, int defaultMinutes) {
            try {
                String text = editText.getText() != null ? editText.getText().toString().trim() : "";
                if (text.isEmpty()) return defaultMinutes;
                int value = Integer.parseInt(text);
                if (value <= 0) return defaultMinutes;
                // The plugin accepts at most 43200 minutes (30 days) per request.
                return Math.min(value, MainViewModel.MAX_MINUTES);
            } catch (NumberFormatException e) {
                return defaultMinutes;
            }
        }

        private String formatMin(long totalMinutes) {
            long h = totalMinutes / 60;
            long m = totalMinutes % 60;
            if (h > 0) return h + ":" + String.format("%02d", m);
            return m + ":00";
        }

        private String formatTicks(long ticks) {
            long millis = ticks / 10000;
            long hours = TimeUnit.MILLISECONDS.toHours(millis);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
            long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
            if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}