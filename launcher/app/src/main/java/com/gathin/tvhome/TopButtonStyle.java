package com.gathin.tvhome;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * How one top-bar pill (Inputs, network, Settings) shows: config.xml inputs_button / wifi_button / settings_button,
 * with the matching _width keys, or the same keys in a theme's config.json.
 */
final class TopButtonStyle {

    static final String BOTH = "both", ICON = "icon", TEXT = "text";
    /** Widest label a theme can ask for, in dp; wider values are ignored. */
    static final int MAX_TEXT_WIDTH_DP = 600;

    /** {@link #BOTH}, {@link #ICON} or {@link #TEXT}. */
    final String show;
    /** The label's maximum width in dp (longer labels end in an ellipsis); 0 keeps the layout's own limit. */
    final int textWidthDp;

    TopButtonStyle(String show, int textWidthDp) {
        this.show = show(show);
        this.textWidthDp = textWidthDp >= 0 && textWidthDp <= MAX_TEXT_WIDTH_DP ? textWidthDp : 0;
    }

    /** A known value, or both (the default) for anything else. */
    static String show(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return ICON.equals(v) || TEXT.equals(v) ? v : BOTH;
    }

    boolean showsIcon() {
        return !TEXT.equals(show);
    }

    boolean showsText() {
        return !ICON.equals(show);
    }

    /**
     * Restyles a pill laid out with the TopButton style (icon, then label). Icon only becomes the same 42dp circle
     * as the reorder button, with the label kept as its content description; text only drops the icon and evens
     * the padding. Call once, after the layout is inflated and before the label changes.
     */
    void apply(TextView pill, float density) {
        if (!showsIcon()) {
            pill.setCompoundDrawablesRelative(null, null, null, null);
            int pad = Math.round(20 * density);
            pill.setPaddingRelative(pad, pill.getPaddingTop(), pad, pill.getPaddingBottom());
        } else if (!showsText()) {
            pill.setContentDescription(pill.getText());
            pill.setText("");
            ViewGroup.LayoutParams lp = pill.getLayoutParams();
            lp.width = Math.round(42 * density);
            pill.setLayoutParams(lp);
            int pad = Math.round(10 * density);
            pill.setPaddingRelative(pad, pill.getPaddingTop(), pad, pill.getPaddingBottom());
            pill.setCompoundDrawablePadding(0);
            return;
        }
        if (textWidthDp > 0) {
            // The label itself gets textWidthDp; the pill adds its padding and the icon with the gap after it.
            // Not getCompoundPaddingStart(): a drawableStart only counts there once the view is attached.
            Drawable icon = pill.getCompoundDrawablesRelative()[0];
            int iconRoom = icon != null ? icon.getIntrinsicWidth() + pill.getCompoundDrawablePadding() : 0;
            pill.setMaxWidth(pill.getPaddingStart() + pill.getPaddingEnd() + iconRoom
                    + Math.round(textWidthDp * density));
            pill.setSingleLine(true);
            pill.setEllipsize(TextUtils.TruncateAt.END);
        }
    }
}
