package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outfit weights below Android 9. android:fontVariationSettings only exists from API 28 (and @font from 26), so
 * on older sets such as Fire OS 6 (Android 7) every TextView or TextClock that asks for @font/outfit gets a static
 * instance of its weight from assets/fonts instead. Styles also carry the weight in app:outfitWeight, because
 * aapt2 moves the API 28 fontVariationSettings item out of the base style.
 */
final class Fonts implements LayoutInflater.Factory2 {

    /** Sorted by attribute id, as obtainStyledAttributes requires. Read from the XML, so fine on older APIs. */
    @SuppressLint("InlinedApi")
    private static final int[] ANDROID_ATTRS = {android.R.attr.fontFamily, android.R.attr.fontVariationSettings};
    private static final int[] WEIGHTS = {200, 300, 400, 500};
    private static final Pattern WGHT = Pattern.compile("'wght'\\s+(\\d+)");
    private static final SparseArray<Typeface> CACHE = new SparseArray<>();

    static void install(LayoutInflater inflater) {
        if (Build.VERSION.SDK_INT >= 28 || inflater.getFactory() != null) return;
        inflater.setFactory2(new Fonts());
    }

    @SuppressLint("ResourceType")   // ANDROID_ATTRS is a hand-built styleable
    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        TextView view;
        if ("TextView".equals(name)) view = new TextView(context, attrs);
        else if ("TextClock".equals(name)) view = new TextClock(context, attrs);
        else return null;

        TypedArray a = context.obtainStyledAttributes(attrs, ANDROID_ATTRS);
        boolean outfit = a.getResourceId(0, 0) == R.font.outfit;
        String variation = a.getString(1);
        a.recycle();
        if (!outfit) return view;

        TypedArray b = context.obtainStyledAttributes(attrs, R.styleable.Outfit);
        int weight = b.getInt(R.styleable.Outfit_outfitWeight, 400);
        b.recycle();
        if (variation != null) {
            Matcher m = WGHT.matcher(variation);
            if (m.find()) weight = Integer.parseInt(m.group(1));
        }
        view.setTypeface(typeface(context, weight));
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    private static Typeface typeface(Context context, int weight) {
        int best = WEIGHTS[0];
        for (int w : WEIGHTS) if (Math.abs(w - weight) < Math.abs(best - weight)) best = w;
        Typeface tf = CACHE.get(best);
        if (tf == null) {
            tf = Typeface.createFromAsset(context.getAssets(), "fonts/outfit-" + best + ".ttf");
            CACHE.put(best, tf);
        }
        return tf;
    }
}
