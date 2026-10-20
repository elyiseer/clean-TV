package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    /** Poll no slower than once a minute when the interval is long. */
    private static final long MAX_POLL_MS = 60_000L;

    private final FrameLayout root;
    private final ImageView image;
    private final ImageView imageAlt;
    private ImageView front;
    private final List<File> files;
    private final long interval;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final int width;
    private final int height;
    private final String transition;
    private final int transitionMs;
    private final boolean animateTransitions;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private boolean transitioning;
    private String displayed = "";
    private Bitmap shown;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            update();
            schedulePoll();
        }
    };

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationSeconds * 1000L;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        transition = Config.normalizeWallpaperTransition(config.wallpaperTransition);
        transitionMs = config.wallpaperTransitionMs > 0
                ? config.wallpaperTransitionMs : Config.WALLPAPER_TRANSITION_MS_DEFAULT;
        animateTransitions = config.animations && !config.reducedMotion
                && !Config.TRANSITION_NONE.equals(transition);
        image = newImage(context);
        imageAlt = newImage(context);
        imageAlt.setAlpha(0f);
        front = image;
        if (config.wallpaperEnabled && !files.isEmpty()) {
            int insert = 0;
            root.addView(image, insert++, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (Config.TRANSITION_CROSSFADE.equals(transition)) {
                root.addView(imageAlt, insert++, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            // No shade layer at all when the theme doesn't dim: a full-screen view is overdraw on 1 GB TVs.
            if (config.wallpaperDim > 0) {
                View shade = new View(context);
                shade.setBackgroundColor((config.wallpaperDim * 255 / 100) << 24);
                shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(shade, insert, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            showFirst();
        }
    }

    private static ImageView newImage(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        File first = WallpaperRotation.find(files, previous);
        if (first == null) first = WallpaperRotation.pick(files, previous, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                front.setImageBitmap(bitmap);
                rememberShown(bitmap);
                displayed = first.getName();
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            first = WallpaperRotation.pick(files, previous, random);
        }
    }

    void start() {
        active = true;
        handler.removeCallbacks(poll);
        update();
        schedulePoll();
    }

    void stop() {
        active = false;
        handler.removeCallbacks(poll);
    }

    void close() {
        active = false;
        closed = true;
        handler.removeCallbacks(poll);
        image.animate().cancel();
        imageAlt.animate().cancel();
        decoder.shutdownNow();
    }

    private void schedulePoll() {
        if (!active || closed || interval <= 0) return;
        long delay = Math.min(Math.max(interval, 1000L), MAX_POLL_MS);
        handler.postDelayed(poll, delay);
    }

    void update() {
        if (!active || closed || loading || transitioning || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        File selected = WallpaperRotation.find(files, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(files, previous, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                applyBitmap(bitmap, () -> {
                    rememberShown(bitmap);
                    displayed = next.getName();
                    if (!displayed.equals(previous) || changed == 0) {
                        preferences.edit().putString("image", displayed)
                                .putLong("changed", System.currentTimeMillis()).apply();
                    }
                });
            });
        });
    }

    private void rememberShown(Bitmap bitmap) {
        Bitmap previous = shown;
        shown = bitmap;
        recycleQuietly(previous, bitmap);
    }

    private static void recycleQuietly(Bitmap old, Bitmap keep) {
        if (old == null || old == keep || old.isRecycled()) return;
        try {
            old.recycle();
        } catch (Exception ignored) {
            // bitmap já libertado noutro caminho
        }
    }

    private void applyBitmap(Bitmap bitmap, Runnable after) {
        if (!animateTransitions) {
            front.setImageBitmap(bitmap);
            if (after != null) after.run();
            return;
        }
        transitioning = true;
        // crossfade: swap A/B
        ImageView back = front == image ? imageAlt : image;
        back.setImageBitmap(bitmap);
        back.setAlpha(0f);
        back.animate().cancel();
        front.animate().cancel();
        back.animate().alpha(1f).setDuration(transitionMs).start();
        ImageView outgoing = front;
        front = back;
        outgoing.animate().alpha(0f).setDuration(transitionMs).withEndAction(() -> {
            transitioning = false;
            if (after != null) after.run();
            outgoing.setImageDrawable(null);
        }).start();
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
