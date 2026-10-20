package com.gathin.tvhome;

import android.app.Activity;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.recyclerview.widget.RecyclerView;

/**
 * compact_ui: a quieter Home for TVs where the background is worth seeing.
 * <ul>
 * <li>Header: no AM/PM, a short date ("Tue 20 Oct" in the TV's language), Settings as an icon.</li>
 * <li>Up on the top bar slides the dock and the rows off the bottom; Down or Back brings them back.</li>
 * <li>Magnification: tiles rest smaller (AppAdapter.REST) and the focused one grows, its row neighbours a little.</li>
 * <li>Idle: after {@link #IDLE_MS} without a key, everything but the clock corner fades; the next key only wakes.</li>
 * </ul>
 * All motion runs frame by frame ({@link Tween}), so it still moves on TVs with system animations turned off,
 * and with reduced_motion it jumps instead.
 */
final class CompactHome {

    static final long IDLE_MS = 5 * 60_000L;
    private static final long IDLE_FADE_MS = 1500, WAKE_MS = 300, SLIDE_MS = 340, MAGNIFY_MS = 220;

    private final RecyclerView grid;
    private final View[] fading;
    private final Handler handler;
    private final boolean reducedMotion;
    private boolean dockHidden, idle, resumed;
    private Tween slide, fade, magnifier;
    private float[] restAlpha;
    private final List<View> magCards = new ArrayList<>();
    private float[] magFrom = new float[0], magTo = new float[0];
    private final Runnable goIdle = () -> setIdle(true);

    /** fading: what goes when Home is idle (the dock and rows, the top bar, the shelf name, the highlight, the glow). */
    CompactHome(RecyclerView grid, View[] fading, Handler handler, boolean reducedMotion) {
        this.grid = grid;
        this.fading = fading;
        this.handler = handler;
        this.reducedMotion = reducedMotion;
    }

    /** The shorter header: AM/PM goes, the date is the short skeleton for the TV's language, Settings is its icon. */
    static void applyHeader(Activity a) {
        View ampm = a.findViewById(R.id.ampm);
        if (ampm != null) ampm.setVisibility(View.GONE);
        TextClock date = a.findViewById(R.id.date);
        if (date != null) {
            String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM");
            date.setFormat12Hour(pattern);
            date.setFormat24Hour(pattern);
        }
        TextView settings = a.findViewById(R.id.btnSettings);
        if (settings != null) iconOnly(settings);
    }

    /** A pill reduced to its icon, padded evenly; its label stays as the description. */
    static void iconOnly(TextView pill) {
        if (pill.getContentDescription() == null) pill.setContentDescription(pill.getText());
        pill.setText("");
        pill.setCompoundDrawablePadding(0);
        int pad = Math.round(14 * pill.getResources().getDisplayMetrics().density);
        pill.setPaddingRelative(pad, pill.getPaddingTop(), pad, pill.getPaddingBottom());
    }

    // ---- dock out of the way -------------------------------------------------------------------

    boolean dockHidden() {
        return dockHidden;
    }

    /** Slides the dock and the rows below the screen (true) or back. */
    void setDockHidden(boolean hide) {
        if (hide == dockHidden) return;
        dockHidden = hide;
        final float dy = grid.getHeight() + grid.getPaddingBottom();
        if (slide != null) slide.cancel();
        final float from = grid.getTranslationY(), to = hide ? dy : 0f;
        slide = new Tween(0f, t -> grid.setTranslationY(from + (to - from) * t));
        slide.to(1f, reducedMotion ? 0 : SLIDE_MS);
    }

    // ---- magnification -------------------------------------------------------------------------

    /**
     * Grows the focused tile and, less, its neighbours in the row; every other tile on screen settles back to
     * its resting size. -1: none (focus left the grid).
     */
    void magnify(int focusedPos, int columns) {
        if (!AppAdapter.magnify) return;
        magCards.clear();
        List<Float> from = new ArrayList<>(), to = new ArrayList<>();
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(child);
            View card = child.findViewById(R.id.card);
            if (pos == RecyclerView.NO_POSITION || card == null) continue;
            float target = 1f;
            if (focusedPos >= 0 && pos / columns == focusedPos / columns) {
                int d = Math.abs(pos - focusedPos);
                target = d == 0 ? AppAdapter.MAGNIFIED : d == 1 ? AppAdapter.NEAR : 1f;
            }
            if (card.getScaleX() == target) continue;
            magCards.add(card);
            from.add(card.getScaleX());
            to.add(target);
        }
        if (magCards.isEmpty()) return;
        magFrom = new float[from.size()];
        magTo = new float[to.size()];
        for (int i = 0; i < magFrom.length; i++) {
            magFrom[i] = from.get(i);
            magTo[i] = to.get(i);
        }
        if (magnifier == null) {
            magnifier = new Tween(1f, v -> {
                for (int i = 0; i < magCards.size() && i < magFrom.length; i++) {
                    float s = magFrom[i] + (magTo[i] - magFrom[i]) * v;
                    magCards.get(i).setScaleX(s);
                    magCards.get(i).setScaleY(s);
                }
            });
        }
        magnifier.cancel();
        magnifier.to(0f, 0);
        magnifier.to(1f, MAGNIFY_MS);
    }

    // ---- idle ----------------------------------------------------------------------------------

    void onResume() {
        resumed = true;
        poke();
    }

    /** Leaving Home: the countdown stops, and Home is all there (dock included) when it comes back. */
    void onPause() {
        resumed = false;
        handler.removeCallbacks(goIdle);
        if (idle) {
            if (fade != null) fade.cancel();
            for (int i = 0; i < fading.length; i++) fading[i].setAlpha(restAlpha[i]);
            idle = false;
        }
        if (dockHidden) {
            if (slide != null) slide.cancel();
            dockHidden = false;
            grid.setTranslationY(0f);
        }
    }

    /** Every key: starts the countdown again; true when it only woke an idle Home and should go no further. */
    boolean onKey(KeyEvent event) {
        poke();
        if (!idle) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) setIdle(false);
        return true;
    }

    private void poke() {
        handler.removeCallbacks(goIdle);
        if (resumed) handler.postDelayed(goIdle, IDLE_MS);
    }

    private void setIdle(boolean on) {
        if (on == idle || (on && !resumed)) return;
        idle = on;
        final int n = fading.length;
        final float[] from = new float[n], to = new float[n];
        if (on) restAlpha = new float[n];
        for (int i = 0; i < n; i++) {
            from[i] = fading[i].getAlpha();
            if (on) restAlpha[i] = from[i];
            to[i] = on ? 0f : restAlpha[i];
        }
        if (fade != null) fade.cancel();
        fade = new Tween(0f, t -> {
            for (int i = 0; i < n; i++) fading[i].setAlpha(from[i] + (to[i] - from[i]) * t);
        });
        fade.to(1f, reducedMotion ? 0 : on ? IDLE_FADE_MS : WAKE_MS);
    }
}
