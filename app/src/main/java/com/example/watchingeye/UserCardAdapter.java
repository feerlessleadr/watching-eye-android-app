package com.example.watchingeye;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public class UserCardAdapter extends ListAdapter<UserCardInfo, UserCardAdapter.CardViewHolder> {

    /** What the card's buttons ask the screen to do. */
    public interface Listener {
        void onAddTime(LimitedUserStatus user, int minutes);
        void onAddTimeOther(LimitedUserStatus user);
        void onTimeOut(LimitedUserStatus user);
        void onEndTimeOut(LimitedUserStatus user);
        void onResume(LimitedUserStatus user, int minutes);
        void onResetTime(LimitedUserStatus user);
        void onStopStream(LimitedUserStatus user, SessionInfo session);
    }

    /** Minutes added by the one-tap quick buttons, and by "End time-out + 30". */
    static final int[] QUICK_MINUTES = {15, 30, 60};
    static final int RESUME_MINUTES = 30;

    private static final Object TICK = new Object();

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

    /** Advances the countdowns and playback positions using the clock, without asking the server. Call about once a second. */
    public void tick() {
        if (getItemCount() > 0) {
            notifyItemRangeChanged(0, getItemCount(), TICK);
        }
    }

    /** True when something on screen moves by itself (a countdown or a playing position), so the ticker is worth running. */
    public boolean needsTicking() {
        List<UserCardInfo> list = getCurrentList();
        for (UserCardInfo card : list) {
            if (card.isPlaying()) return true;
        }
        return false;
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

                @Override
                public Object getChangePayload(@NonNull UserCardInfo a, @NonNull UserCardInfo b) {
                    return null;
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

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(TICK)) {
            holder.bindTick(getItem(position));
        } else {
            holder.bind(getItem(position));
        }
    }

    private GlideUrl embyImageUrl(String path) {
        String base = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        return new GlideUrl(base + path, new LazyHeaders.Builder()
                .addHeader("X-Emby-Token", embyApiKey != null ? embyApiKey : "")
                .build());
    }

    class CardViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView userNameTextView;
        private final TextView statusChipTextView;
        private final ImageButton moreButton;
        private final TextView timeLeftTextView;
        private final LinearProgressIndicator timeProgress;
        private final TextView timeDetailTextView;
        private final LinearLayout nowPlayingSection;
        private final ImageView posterImageView;
        private final TextView nowPlayingTextView;
        private final TextView progressTextView;
        private final ProgressBar playbackProgressBar;
        private final TextView clientDeviceTextView;
        private final LinearLayout addTimeRow;
        private final LinearLayout timeoutRow;

        private UserCardInfo current;

        CardViewHolder(View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            userNameTextView = itemView.findViewById(R.id.userNameTextView);
            statusChipTextView = itemView.findViewById(R.id.statusChipTextView);
            moreButton = itemView.findViewById(R.id.moreButton);
            timeLeftTextView = itemView.findViewById(R.id.timeLeftTextView);
            timeProgress = itemView.findViewById(R.id.timeProgress);
            timeDetailTextView = itemView.findViewById(R.id.timeDetailTextView);
            nowPlayingSection = itemView.findViewById(R.id.nowPlayingSection);
            posterImageView = itemView.findViewById(R.id.posterImageView);
            nowPlayingTextView = itemView.findViewById(R.id.nowPlayingTextView);
            progressTextView = itemView.findViewById(R.id.progressTextView);
            playbackProgressBar = itemView.findViewById(R.id.playbackProgressBar);
            clientDeviceTextView = itemView.findViewById(R.id.clientDeviceTextView);
            addTimeRow = itemView.findViewById(R.id.addTimeRow);
            timeoutRow = itemView.findViewById(R.id.timeoutRow);

            int[] ids = {R.id.add15Button, R.id.add30Button, R.id.add60Button};
            for (int i = 0; i < ids.length; i++) {
                final int minutes = QUICK_MINUTES[i];
                MaterialButton b = itemView.findViewById(ids[i]);
                b.setText("+" + minutes);
                b.setContentDescription("Add " + minutes + " minutes");
                b.setOnClickListener(v -> {
                    if (current != null && listener != null) listener.onAddTime(current.getUserStatus(), minutes);
                });
            }
            itemView.findViewById(R.id.addCustomButton).setOnClickListener(v -> {
                if (current != null && listener != null) listener.onAddTimeOther(current.getUserStatus());
            });
            MaterialButton resume = itemView.findViewById(R.id.resumeButton);
            resume.setText("End time-out + " + RESUME_MINUTES);
            resume.setOnClickListener(v -> {
                if (current != null && listener != null) listener.onResume(current.getUserStatus(), RESUME_MINUTES);
            });
            itemView.findViewById(R.id.endTimeoutButton).setOnClickListener(v -> {
                if (current != null && listener != null) listener.onEndTimeOut(current.getUserStatus());
            });
            moreButton.setOnClickListener(this::showMenu);
        }

        private void showMenu(View anchor) {
            if (current == null || listener == null) return;
            final UserCardInfo card = current;
            PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
            menu.getMenu().add(0, 1, 0, "Time out…");
            if (isTimedOut(card)) menu.getMenu().add(0, 2, 1, "End time-out");
            menu.getMenu().add(0, 3, 2, "Reset time used…");
            if (card.isPlaying() && card.getSessionInfo().getId() != null) menu.getMenu().add(0, 4, 3, "Stop stream…");
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: listener.onTimeOut(card.getUserStatus()); return true;
                    case 2: listener.onEndTimeOut(card.getUserStatus()); return true;
                    case 3: listener.onResetTime(card.getUserStatus()); return true;
                    case 4: listener.onStopStream(card.getUserStatus(), card.getSessionInfo()); return true;
                    default: return false;
                }
            });
            menu.show();
        }

        private boolean isTimedOut(UserCardInfo card) {
            return TimeOutFormat.activeEnd(card.getUserStatus().getTimeOutUntil(), Instant.now()) != null;
        }

        void bind(UserCardInfo card) {
            current = card;
            LimitedUserStatus status = card.getUserStatus();
            SessionInfo session = card.getSessionInfo();

            userNameTextView.setText(status.getUsername() != null ? status.getUsername() : "Unknown");
            bindTick(card);


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
                nowPlayingTextView.setText(title);
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
                String client = session.getClient() != null ? session.getClient() : "Unknown";
                String device = session.getDeviceName() != null ? session.getDeviceName() : "Device";
                clientDeviceTextView.setText(client + " · " + device);
            } else {
                nowPlayingSection.setVisibility(View.GONE);
                posterImageView.setVisibility(View.GONE);
            }
        }

        private void bindStatusChip(UserCardInfo card) {
            LimitedUserStatus status = card.getUserStatus();
            Instant timeoutEnd = TimeOutFormat.activeEnd(status.getTimeOutUntil(), Instant.now());
            String reason = status.getBlockReason();
            int colorRes;
            String text;
            if (!status.isEnabled()) {
                text = "Limit switched off";
                colorRes = 0;
            } else if (timeoutEnd != null) {
                text = "Timed out until " + TimeOutFormat.describe(timeoutEnd, ZoneId.systemDefault(), Locale.getDefault());
                colorRes = R.color.we_bad;
            } else if ("TimeLimitExceeded".equals(reason)) {
                text = "Limit reached (" + status.getLimitingPeriodLabel() + ")";
                colorRes = R.color.we_bad;
            } else if ("OutsideTimeWindow".equals(reason)) {
                text = "Outside allowed hours";
                colorRes = R.color.we_warn;
            } else if (card.isPaused()) {
                text = "Paused";
                colorRes = R.color.we_warn;
            } else if (card.isPlaying()) {
                text = "Watching now";
                colorRes = R.color.we_ok;
            } else {
                text = "Not watching";
                colorRes = 0;
            }
            statusChipTextView.setText(text);
            if (colorRes != 0) {
                statusChipTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), colorRes));
            } else {
                statusChipTextView.setTextColor(android.graphics.Color.GRAY);
            }
        }

        /** The numbers that move on their own: time left, the bar under it, and the playback position. */
        void bindTick(UserCardInfo card) {
            current = card;
            LimitedUserStatus status = card.getUserStatus();
            long now = System.currentTimeMillis();
            double remaining = card.remainingSecondsNow(now);

            // Cheap and idempotent: keeps the chip and rows right when a time-out ends on its own between updates.
            bindStatusChip(card);
            addTimeRow.setVisibility(status.isEnabled() ? View.VISIBLE : View.GONE);
            timeoutRow.setVisibility(isTimedOut(card) ? View.VISIBLE : View.GONE);

            if (!status.isEnabled()) {
                timeLeftTextView.setText("Limit is off");
                timeLeftTextView.setTextColor(android.graphics.Color.GRAY);
                timeProgress.setVisibility(View.GONE);
                timeDetailTextView.setText("Switched off on the plugin page.");
            } else if (remaining < 0) {
                timeLeftTextView.setText("No time limit");
                timeLeftTextView.setTextColor(android.graphics.Color.GRAY);
                timeProgress.setVisibility(View.GONE);
                timeDetailTextView.setText("Watched today: " + TimeText.duration(status.getSecondsWatchedDaily()));
            } else {
                double allowed = status.getLimitingAllowedSeconds();
                double used = Math.max(0.0, allowed - remaining);
                timeLeftTextView.setText(remaining <= 0 ? "No time left" : TimeText.duration(remaining) + " left");
                int color = remaining <= 0 ? R.color.we_bad : (remaining < 300 ? R.color.we_warn : 0);
                if (color != 0) {
                    timeLeftTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), color));
                } else {
                    timeLeftTextView.setTextColor(resolveOnSurface());
                }
                timeProgress.setVisibility(View.VISIBLE);
                int permille = allowed > 0 ? (int) Math.min(1000, Math.round(used * 1000.0 / allowed)) : 1000;
                timeProgress.setProgress(permille);
                timeDetailTextView.setText("Used " + TimeText.duration(used) + " of " + TimeText.duration(allowed) + " "
                        + TimeText.periodPhrase(status.getLimitingPeriodLabel()));
            }

            if (card.isPlaying()) {
                long runtime = card.getSessionInfo().getNowPlayingItem() != null && card.getSessionInfo().getNowPlayingItem().getRunTimeTicks() != null
                        ? card.getSessionInfo().getNowPlayingItem().getRunTimeTicks() : 0L;
                long position = card.positionTicksNow(now);
                if (runtime > 0) position = Math.min(position, runtime);
                int pct = runtime > 0 ? (int) Math.min(100, (position * 100) / runtime) : 0;
                progressTextView.setText(TimeText.clock(position) + (runtime > 0 ? " / " + TimeText.clock(runtime) : "")
                        + (card.isPaused() ? "  (paused)" : ""));
                playbackProgressBar.setProgress(pct);
            }
        }

        private int resolveOnSurface() {
            android.util.TypedValue value = new android.util.TypedValue();
            itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, value, true);
            return value.data;
        }
    }
}
