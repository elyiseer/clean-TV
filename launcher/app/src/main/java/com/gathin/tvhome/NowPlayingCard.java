package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Now playing card (now_playing): the media session of an app playing in the background, right-aligned
 * just above the dock, with play/pause on OK, skip on Left/Right and the app's player on a long press.
 * It is reached only from Settings (Down) and the dock's last app (Up); Down goes back to that app.
 *
 * Android lists other apps' media sessions only to a caller with an enabled notification listener
 * ({@link NowPlayingListener}), which the install script enables when asked (NOW_PLAYING=1). Without it
 * the card never shows. Every step into the media session APIs runs under {@link NowPlaying#run}: the
 * first failure switches the card off for the session.
 */
final class NowPlayingCard {

    /** What the card needs from Home. */
    interface Host {
        /** Springs the tile highlight onto the card, as it looks scaled by {@code scale}. */
        void highlight(View card, float scale, float radius);

        /** Focuses the dock's last app, the tile the card is entered from. */
        void focusDockEnd();

        void open(Intent intent);
    }

    static final float FOCUS_SCALE = 1.04f;

    private final Activity activity;
    private final Handler handler;
    private final FrameLayout root;
    private final RecyclerView grid;
    private final View topBar;
    private final TextView heroName;
    private final Host host;
    private final boolean animations;
    private final float density;
    private final View card;
    private final ImageView art;
    private final ImageView stateIcon;
    private final TextView title;
    private final TextView subtitle;
    private final ComponentName listener;
    private MediaSessionManager sessions;
    private final List<MediaController> watched = new ArrayList<>();
    private MediaController shown;
    private boolean hasSession;
    private boolean visible;
    private Bitmap artSource;
    private String artPkg;

    private final Runnable refresh = () -> NowPlaying.run(() -> {
        refresh();
        return null;
    }, null);
    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            schedule();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            schedule();
        }

        @Override
        public void onSessionDestroyed() {
            schedule();
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> NowPlaying.run(() -> {
        watch(list);
        return null;
    }, null);

    NowPlayingCard(Activity activity, Handler handler, FrameLayout root, RecyclerView grid, View topBar,
                   TextView heroName, boolean animations, Host host) {
        this.activity = activity;
        this.handler = handler;
        this.root = root;
        this.grid = grid;
        this.topBar = topBar;
        this.heroName = heroName;
        this.animations = animations;
        this.host = host;
        density = activity.getResources().getDisplayMetrics().density;
        card = activity.findViewById(R.id.nowPlaying);
        art = activity.findViewById(R.id.nowPlayingArt);
        stateIcon = activity.findViewById(R.id.nowPlayingState);
        title = activity.findViewById(R.id.nowPlayingTitle);
        subtitle = activity.findViewById(R.id.nowPlayingSubtitle);
        listener = new ComponentName(activity, NowPlayingListener.class);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        card.setContentDescription(activity.getString(R.string.now_playing_description));

        // Focus looks like a tile's: the card lifts and the glass highlight springs onto it.
        card.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? FOCUS_SCALE : 1f;
            if (animations) v.animate().scaleX(s).scaleY(s).setDuration(focused ? 200 : 160).start();
            else {
                v.setScaleX(s);
                v.setScaleY(s);
            }
            if (focused) host.highlight(v, FOCUS_SCALE, 16 * density);
        });
        final float artRadius = 9 * density;
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), artRadius);
            }
        });
        art.setClipToOutline(true);
        card.setOnClickListener(v -> NowPlaying.run(() -> {
            MediaController c = shown;
            if (c == null) return null;
            PlaybackState st = c.getPlaybackState();
            if (st != null && NowPlaying.isActive(st.getState())) c.getTransportControls().pause();
            else c.getTransportControls().play();
            return null;
        }, null));
        card.setOnLongClickListener(v -> {
            NowPlaying.run(() -> {
                openApp();
                return null;
            }, null);
            return true;
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            boolean left = keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
            if (!left && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.focusDockEnd();
                return true;
            }
            if (event.getRepeatCount() > 0) return true;   // one skip per press, not a run of them
            NowPlaying.run(() -> {
                MediaController c = shown;
                if (c == null) return null;
                if (left) c.getTransportControls().skipToPrevious();
                else c.getTransportControls().skipToNext();
                return null;
            }, null);
            return true;
        });
        // The card sits in the space above the dock: keep it there as the layout settles, and out of the
        // way while the grid is scrolled up over that space.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> grid.post(this::place));
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (hasSession) place();
            }
        });
    }

    /** On screen and focusable: the dock's last app and Settings lead to it. */
    boolean visible() {
        return visible;
    }

    boolean requestFocus() {
        return visible && card.requestFocus();
    }

    void start() {
        if (!NowPlaying.working()) return;
        NowPlaying.run(() -> {
            if (!listenerGranted()) {
                hasSession = false;
                place();
                return null;
            }
            if (sessions == null) sessions = activity.getSystemService(MediaSessionManager.class);
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
            watch(sessions.getActiveSessions(listener));
            return null;
        }, null);
        if (!NowPlaying.working()) {
            hasSession = false;
            place();
        }
    }

    void stop() {
        handler.removeCallbacks(refresh);
        NowPlaying.run(() -> {
            if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
            unwatch();
            return null;
        }, null);
    }

    /** Whether the notification listener is enabled, without which Android lists no sessions. */
    private boolean listenerGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            NotificationManager nm = activity.getSystemService(NotificationManager.class);
            return nm != null && nm.isNotificationListenerAccessGranted(listener);
        }
        String enabled = Settings.Secure.getString(activity.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && Arrays.asList(enabled.split(":")).contains(listener.flattenToString());
    }

    /** Follows every active session, so whichever one starts playing can take the card. */
    private void watch(List<MediaController> list) {
        unwatch();
        if (list != null) {
            for (MediaController c : list) {
                c.registerCallback(sessionCallback, handler);
                watched.add(c);
            }
        }
        refresh();
    }

    private void unwatch() {
        for (MediaController c : watched) {
            try {
                c.unregisterCallback(sessionCallback);
            } catch (Exception ignored) {
                // A session that died already has nothing to unregister.
            }
        }
        watched.clear();
    }

    /** Callbacks can come in bursts (state, then metadata); draw once after them. */
    private void schedule() {
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 120);
    }

    private void refresh() {
        int n = watched.size();
        int[] states = new int[n];
        String[] packages = new String[n];
        for (int i = 0; i < n; i++) {
            PlaybackState st = watched.get(i).getPlaybackState();
            states[i] = st != null ? st.getState() : NowPlaying.STATE_NONE;
            packages[i] = watched.get(i).getPackageName();
        }
        int pick = NowPlaying.choose(states, packages, activity.getPackageName());
        shown = pick >= 0 ? watched.get(pick) : null;
        hasSession = shown != null;
        if (hasSession) bind(shown, states[pick]);
        place();
    }

    private void bind(MediaController c, int state) {
        String pkg = c.getPackageName();
        String appLabel = pkg;
        try {
            appLabel = activity.getPackageManager().getApplicationLabel(
                    activity.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            // A package we can't see keeps its package name.
        }
        MediaMetadata md = c.getMetadata();
        String line1 = NowPlaying.title(text(md, MediaMetadata.METADATA_KEY_TITLE),
                text(md, MediaMetadata.METADATA_KEY_DISPLAY_TITLE), appLabel);
        title.setText(line1);
        String line2 = NowPlaying.subtitle(text(md, MediaMetadata.METADATA_KEY_ARTIST),
                text(md, MediaMetadata.METADATA_KEY_ALBUM), appLabel, line1);
        subtitle.setText(line2);
        subtitle.setVisibility(line2.isEmpty() ? View.GONE : View.VISIBLE);
        stateIcon.setImageResource(NowPlaying.isActive(state) ? R.drawable.ic_pause : R.drawable.ic_play);

        Bitmap picture = null;
        if (md != null) {
            for (String key : new String[]{MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                picture = md.getBitmap(key);
                if (picture != null) break;
            }
        }
        // Metadata comes again with every state change; scale a new picture only when it changed.
        if (picture != null && (picture != artSource || !pkg.equals(artPkg))) {
            art.setImageBitmap(squareThumb(picture, (int) (44 * density)));
        } else if (picture == null && (artSource != null || !pkg.equals(artPkg))) {
            Drawable icon = null;
            try {
                // The square icon: a wide banner cropped to a square cuts the app's name off.
                icon = activity.getPackageManager().getApplicationIcon(pkg);
            } catch (Exception ignored) {
                // No picture: the card shows the title alone.
            }
            art.setImageDrawable(icon);
        }
        artSource = picture;
        artPkg = pkg;
    }

    private static String text(MediaMetadata md, String key) {
        if (md == null) return null;
        CharSequence t = md.getText(key);
        return t != null ? t.toString() : null;
    }

    /** A centre square of the artwork at the card's size, so a 1000 px cover isn't drawn at 44 dp. */
    private static Bitmap squareThumb(Bitmap src, int size) {
        int side = Math.min(src.getWidth(), src.getHeight());
        if (side <= 0) return src;
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        Bitmap square = side == src.getWidth() && side == src.getHeight() ? src : Bitmap.createBitmap(src, x, y, side, side);
        return side > size ? Bitmap.createScaledBitmap(square, size, size, true) : square;
    }

    /**
     * Shows the card just above the dock when there is a session, room under the top bar, and the grid
     * sits at its top; otherwise fades it out. The top shelf name is shortened so it never runs under it.
     */
    private void place() {
        boolean want = hasSession && NowPlaying.working() && !grid.canScrollVertically(-1) && grid.getHeight() > 0;
        float top = 0;
        if (want) {
            if (card.getVisibility() != View.VISIBLE) {
                card.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
            }
            int h = card.getVisibility() == View.VISIBLE && card.getHeight() > 0 ? card.getHeight() : card.getMeasuredHeight();
            Rect g = new Rect(0, 0, grid.getWidth(), grid.getHeight());
            root.offsetDescendantRectToMyCoords(grid, g);
            Rect bar = new Rect(0, 0, topBar.getWidth(), topBar.getHeight());
            root.offsetDescendantRectToMyCoords(topBar, bar);
            float dockTop = g.top + grid.getPaddingTop();
            top = dockTop - 22 * density - h;
            want = top >= bar.bottom + 14 * density;   // no room with rows under the dock: leave it out
        }
        if (want) {
            card.setY(top);
            if (!visible) {
                visible = true;
                card.setVisibility(View.VISIBLE);
                if (animations) card.animate().alpha(1f).setDuration(240).start();
                else card.setAlpha(1f);
            }
            card.post(() -> {
                Rect c = new Rect(0, 0, card.getWidth(), card.getHeight());
                root.offsetDescendantRectToMyCoords(card, c);
                Rect hero = new Rect(0, 0, heroName.getWidth(), heroName.getHeight());
                root.offsetDescendantRectToMyCoords(heroName, hero);
                // The name and the card sit at opposite ends of the row (mirrored in right-to-left).
                int room = c.left > hero.left ? c.left - hero.left : hero.right - c.right;
                heroName.setMaxWidth(Math.max(0, room - (int) (24 * density)));
            });
        } else if (visible) {
            visible = false;
            if (card.hasFocus()) host.focusDockEnd();
            if (animations) {
                card.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                    if (!visible) card.setVisibility(View.GONE);
                }).start();
            } else {
                card.setAlpha(0f);
                card.setVisibility(View.GONE);
            }
            heroName.setMaxWidth(Integer.MAX_VALUE);
        }
    }

    /** Hold OK on the card: the app's own player screen, or failing that its launcher entry. */
    private void openApp() {
        MediaController c = shown;
        if (c == null) return;
        PendingIntent pi = c.getSessionActivity();
        if (pi != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    // Android 14 lets a foreground app start another app's PendingIntent only when it says so.
                    ActivityOptions o = ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pi.send(activity, 0, null, null, null, null, o.toBundle());
                } else {
                    pi.send();
                }
                return;
            } catch (PendingIntent.CanceledException ignored) {
                // Fall back to the app's launcher entry.
            }
        }
        Intent i = activity.getPackageManager().getLeanbackLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = activity.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i != null) host.open(i);
    }
}
