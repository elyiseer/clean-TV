package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    /** Segundos; -1 = seguir tema. Inclui 5 s para teste rápido. */
    private static final int[] ROTATION_PRESETS = { -1, 0, 5, 5 * 60, 15 * 60, 30 * 60, 60 * 60, 120 * 60 };
    private static final int[] OPACITY_PRESETS = { 30, 55, 70, 85 };

    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView wallpaperRotationChoice;
    private TextView wallpaperTransitionChoice;
    private TextView ambientTintChoice;
    private TextView dockGlassChoice;
    private TextView dockGlassOpacityChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;
    private boolean ambientTint;
    private boolean dockGlass;
    private int dockGlassOpacity;
    private final ExecutorService downloads = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        ambientTint = config.ambientTint;
        dockGlass = config.dockGlass;
        dockGlassOpacity = config.dockGlassOpacity;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        wallpaperRotationChoice = findViewById(R.id.wallpaperRotationChoice);
        wallpaperRotationChoice.setOnClickListener(v -> showRotationPicker());
        wallpaperTransitionChoice = findViewById(R.id.wallpaperTransitionChoice);
        wallpaperTransitionChoice.setOnClickListener(v -> showTransitionPicker());
        ambientTintChoice = findViewById(R.id.ambientTintChoice);
        ambientTintChoice.setOnClickListener(v -> {
            ambientTint = !ambientTint;
            choices.edit().putBoolean(Config.AMBIENT_TINT_KEY, ambientTint).apply();
            setResult(RESULT_OK);
            refresh();
        });
        dockGlassChoice = findViewById(R.id.dockGlassChoice);
        dockGlassChoice.setOnClickListener(v -> {
            dockGlass = !dockGlass;
            choices.edit().putBoolean(Config.DOCK_GLASS_KEY, dockGlass).apply();
            setResult(RESULT_OK);
            refresh();
        });
        dockGlassOpacityChoice = findViewById(R.id.dockGlassOpacityChoice);
        dockGlassOpacityChoice.setOnClickListener(v -> showOpacityPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            Config.clearOnTvChoices(choices.edit()).commit();
            WallpaperUrls.deleteCache(this);
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            ambientTint = current.ambientTint;
            dockGlass = current.dockGlass;
            dockGlassOpacity = current.dockGlassOpacity;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v -> open(tvSettingsIntent()));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    @Override
    protected void onDestroy() {
        downloads.shutdownNow();
        super.onDestroy();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        // Theme files, then Bing / From URL…, then None.
        String[] labels = new String[images.size() + 3];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        int bingIndex = images.size();
        int urlIndex = images.size() + 1;
        int noneIndex = images.size() + 2;
        labels[bingIndex] = getString(R.string.wallpaper_bing);
        labels[urlIndex] = getString(R.string.wallpaper_from_url);
        labels[noneIndex] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = BingWallpaper.CHOICE.equals(current) ? bingIndex
                : WallpaperUrls.CHOICE.equals(current) ? urlIndex
                : "none".equals(current) ? noneIndex
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == noneIndex) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                        setResult(RESULT_OK);
                        Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                        refresh();
                    } else if (which == bingIndex) {
                        applySource(BingWallpaper.CHOICE, () -> BingWallpaper.fetchAndCache(this));
                    } else if (which == urlIndex) {
                        showUrlEditor();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                        setResult(RESULT_OK);
                        Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).remove(WallpaperUrls.URLS_KEY).apply();
                    WallpaperUrls.deleteCache(this);
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface FetchCache {
        String run();
    }

    private void applySource(String choice, FetchCache fetch) {
        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, choice).apply();
        downloads.execute(() -> {
            String err = fetch.run();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (err != null) {
                    Toast.makeText(this, getString(R.string.wallpaper_url_failed, err), Toast.LENGTH_LONG).show();
                    refresh();
                    return;
                }
                setResult(RESULT_OK);
                Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void showRotationPicker() {
        String[] labels = new String[ROTATION_PRESETS.length];
        for (int i = 0; i < ROTATION_PRESETS.length; i++) {
            labels[i] = rotationPresetLabel(ROTATION_PRESETS[i]);
        }
        int checked = 0;
        if (choices.contains(Config.WALLPAPER_ROTATION_KEY)) {
            int cur = choices.getInt(Config.WALLPAPER_ROTATION_KEY, 0);
            checked = -1;
            for (int i = 1; i < ROTATION_PRESETS.length; i++) {
                if (ROTATION_PRESETS[i] == cur) {
                    checked = i;
                    break;
                }
            }
            if (checked < 0) checked = 1; // Off as fallback if stored value is not a preset
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_rotation_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (ROTATION_PRESETS[which] < 0) {
                        choices.edit().remove(Config.WALLPAPER_ROTATION_KEY).apply();
                    } else {
                        choices.edit().putInt(Config.WALLPAPER_ROTATION_KEY, ROTATION_PRESETS[which]).apply();
                    }
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String rotationPresetLabel(int seconds) {
        if (seconds < 0) return getString(R.string.wallpaper_theme_default);
        if (seconds == 0) return getString(R.string.wallpaper_rotation_off);
        if (seconds < 60) return getString(R.string.wallpaper_rotation_seconds, seconds);
        return getString(R.string.wallpaper_rotation_minutes, seconds / 60);
    }

    private void showTransitionPicker() {
        String[] values = {
                Config.TRANSITION_NONE,
                Config.TRANSITION_CROSSFADE
        };
        String[] labels = {
                getString(R.string.wallpaper_transition_none),
                getString(R.string.wallpaper_transition_crossfade)
        };
        String current = Config.normalizeWallpaperTransition(
                choices.getString(Config.WALLPAPER_TRANSITION_KEY, Config.TRANSITION_NONE));
        int checked = Config.TRANSITION_CROSSFADE.equals(current) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_transition_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (Config.TRANSITION_NONE.equals(values[which])) {
                        choices.edit().remove(Config.WALLPAPER_TRANSITION_KEY).apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_TRANSITION_KEY, values[which]).apply();
                    }
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showOpacityPicker() {
        String[] labels = new String[OPACITY_PRESETS.length];
        for (int i = 0; i < OPACITY_PRESETS.length; i++) {
            labels[i] = OPACITY_PRESETS[i] + "%";
        }
        int checked = 1;
        for (int i = 0; i < OPACITY_PRESETS.length; i++) {
            if (OPACITY_PRESETS[i] == dockGlassOpacity) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dock_glass_opacity_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    dockGlassOpacity = OPACITY_PRESETS[which];
                    choices.edit().putInt(Config.DOCK_GLASS_OPACITY_KEY, dockGlassOpacity).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showUrlEditor() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setText(WallpaperUrls.saved(this));
        input.setHint(R.string.wallpaper_url_hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(pad, pad / 2, pad, 0);
        TextView hint = new TextView(this);
        hint.setText(R.string.wallpaper_url_hint);
        hint.setTextColor(0xFFACB8C1);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        wrap.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_url_title)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> applyUrls(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private void applyUrls(String text) {
        List<String> urls = WallpaperUrls.parse(text);
        if (urls.isEmpty()) {
            Toast.makeText(this, getString(R.string.wallpaper_url_failed, "empty"), Toast.LENGTH_LONG).show();
            return;
        }
        WallpaperUrls.save(this, text);
        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, WallpaperUrls.CHOICE).apply();
        downloads.execute(() -> {
            String err = WallpaperUrls.downloadAll(this);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (err != null) {
                    Toast.makeText(this, getString(R.string.wallpaper_url_failed, err), Toast.LENGTH_LONG).show();
                    refresh();
                    return;
                }
                setResult(RESULT_OK);
                Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                finish();   // Home recreates and shows the cached image(s)
            });
        });
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        ambientTintChoice.setText(getString(R.string.ambient_tint_state,
                getString(ambientTint ? R.string.on : R.string.off)));
        dockGlassChoice.setText(getString(R.string.dock_glass_state,
                getString(dockGlass ? R.string.on : R.string.off)));
        dockGlassOpacityChoice.setText(getString(R.string.dock_glass_opacity_state, dockGlassOpacity));
        dockGlassOpacityChoice.setVisibility(dockGlass ? View.VISIBLE : View.GONE);
        wallpaperRotationChoice.setText(getString(R.string.wallpaper_rotation_state, rotationLabel()));
        wallpaperTransitionChoice.setText(getString(R.string.wallpaper_transition_state, transitionLabel()));
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        String wallpaperLabel;
        if ("none".equals(wallpaperCurrent)) {
            wallpaperLabel = getString(R.string.wallpaper_none);
        } else if (BingWallpaper.CHOICE.equals(wallpaperCurrent)) {
            wallpaperLabel = getString(R.string.wallpaper_bing);
        } else if (WallpaperUrls.CHOICE.equals(wallpaperCurrent)) {
            int n = WallpaperUrls.parse(WallpaperUrls.saved(this)).size();
            wallpaperLabel = getString(R.string.wallpaper_url_state, n);
        } else if (wallpaperCurrent != null) {
            wallpaperLabel = wallpaperCurrent;
        } else {
            wallpaperLabel = getString(R.string.wallpaper_theme_default);
        }
        wallpaperChoice.setText(getString(R.string.wallpaper_state, wallpaperLabel));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private String rotationLabel() {
        if (!choices.contains(Config.WALLPAPER_ROTATION_KEY)) {
            return getString(R.string.wallpaper_theme_default);
        }
        return rotationPresetLabel(choices.getInt(Config.WALLPAPER_ROTATION_KEY, 0));
    }

    private String transitionLabel() {
        String t = Config.normalizeWallpaperTransition(
                choices.getString(Config.WALLPAPER_TRANSITION_KEY, Config.TRANSITION_NONE));
        if (Config.TRANSITION_CROSSFADE.equals(t)) return getString(R.string.wallpaper_transition_crossfade);
        return getString(R.string.wallpaper_transition_none);
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY)
                || choices.contains(WallpaperUrls.URLS_KEY)
                || choices.contains(Config.WALLPAPER_ROTATION_KEY)
                || choices.contains(Config.WALLPAPER_TRANSITION_KEY)
                || choices.contains(Config.AMBIENT_TINT_KEY)
                || choices.contains(Config.DOCK_GLASS_KEY)
                || choices.contains(Config.DOCK_GLASS_OPACITY_KEY);
    }

    /** Mesma ordem do botão Settings na Home: Fire TV → Android TV settings → ACTION_SETTINGS. */
    private Intent tvSettingsIntent() {
        if (getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage("com.amazon.tv.launcher"), 0) != null) {
            Intent fire = new Intent().setComponent(new ComponentName(
                    "com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity"));
            if (fire.resolveActivity(getPackageManager()) != null) return fire;
        }
        Intent tv = new Intent().setComponent(new ComponentName(
                "com.android.tv.settings", "com.android.tv.settings.MainSettings"));
        if (tv.resolveActivity(getPackageManager()) != null) return tv;
        Intent standard = new Intent(Settings.ACTION_SETTINGS);
        if (standard.resolveActivity(getPackageManager()) != null) return standard;
        return standard;
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
