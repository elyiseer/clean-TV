package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on:
 * - On the launcher screen: shows current quick settings options (TV settings, Display, Network, Apps, Launcher settings).
 * - On other applications: shows full sidebar settings including inputs (HDMI 1, HDMI 2, Live TV, etc.)
 *   followed by the quick settings options.
 * Translucent and in a task of its own, so the app behind keeps running and Back returns to it.
 * Pressing Settings again closes the panel.
 */
public final class QuickSettingsActivity extends Activity {

    public static final String EXTRA_ON_LAUNCHER = "extra_on_launcher";

    private int[] settingsKeycodes = new int[0];

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());

        Config cfg = Config.load(this);
        settingsKeycodes = cfg.settingsKeycodes;

        boolean onLauncher = getIntent().getBooleanExtra(EXTRA_ON_LAUNCHER, false);
        if (!getIntent().hasExtra(EXTRA_ON_LAUNCHER)) {
            long now = System.currentTimeMillis();
            onLauncher = MainActivity.isForeground || (MainActivity.lastPausedTimestamp > 0 && (now - MainActivity.lastPausedTimestamp < 600));
        }

        ScrollView scroll = findViewById(R.id.scroll);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) scroll.getLayoutParams();
        float density = getResources().getDisplayMetrics().density;
        if (onLauncher) {
            lp.topMargin = (int) (105 * density);
        } else {
            lp.topMargin = (int) (40 * density);
        }
        scroll.setLayoutParams(lp);

        LinearLayout rows = findViewById(R.id.rows);

        if (onLauncher) {
            // Home screen mode: clean options aligned beneath Settings pill
            addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, new Intent(Settings.ACTION_SETTINGS));
            addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, pictureIntent());
            addRow(rows, R.drawable.ic_sound, R.string.quick_sound, 0, soundIntent());
            addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, new Intent(Settings.ACTION_WIFI_SETTINGS));
            addRow(rows, R.drawable.ic_screensaver, R.string.quick_screensaver, 0, screensaverIntent());
            addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                    new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
            addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                    new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        } else {
            // Inside another app (YouTube, Netflix, HDMI, etc.): full Skyworth Quick Access sidebar
            addRow(rows, R.drawable.ic_screensaver, R.string.quick_screensaver, 0, screensaverIntent());
            addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, pictureIntent());
            addRow(rows, R.drawable.ic_sound, R.string.quick_sound, 0, soundIntent());
            addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, new Intent(Settings.ACTION_WIFI_SETTINGS));

            int beforeInputs = rows.getChildCount();
            populateInputs(rows, cfg);
            if (rows.getChildCount() > beforeInputs) {
                View line = createSeparator();
                rows.addView(line, beforeInputs);
                addSeparator(rows);
            } else {
                addSeparator(rows);
            }

            addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, new Intent(Settings.ACTION_SETTINGS));
            addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                    new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
            addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                    new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        }

        if (rows.getChildCount() > 0) {
            rows.getChildAt(0).requestFocus();
        }
    }

    private void populateInputs(LinearLayout rows, Config cfg) {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        boolean tunerIsAnalog = false;

        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });

            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;

            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    boolean analog = info.getId().toLowerCase(Locale.ROOT).contains("analog");
                    boolean better = tunerId == null
                            || (hw && !tunerIsHw)
                            || (hw == tunerIsHw && tunerIsAnalog && !analog)
                            || (hw == tunerIsHw && analog == tunerIsAnalog
                                    && info.getId().compareTo(tunerId) < 0);
                    if (better) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                        tunerIsAnalog = analog;
                    }
                }
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                CharSequence custom = info.loadCustomLabel(this);
                if (custom != null && custom.length() > 0 && !custom.toString().equals(label)) {
                    label = custom + " \u00B7 " + label;
                }
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> openInput(id, cfg.inputAction));
            }
        }

        if (rows.getChildCount() == 0 && !cfg.sourcePanelService.isEmpty()) {
            addInputRow(rows, R.drawable.ic_input, getString(R.string.inputs), -1,
                    () -> openSourcePanel(cfg.sourcePanelService, cfg.sourcePanelAction));
        }

        Intent liveTv = liveTvIntent(cfg.liveTvPackage, cfg.liveTvAction);
        if (liveTv != null && tunerId != null) {
            Intent tuned = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId),
                            "vnd.android.cursor.item/channel")
                    .setPackage(cfg.liveTvPackage);
            if (tuned.resolveActivity(getPackageManager()) != null) liveTv = tuned;
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, R.drawable.ic_livetv, getString(R.string.live_tv), -1,
                    () -> openIntent(liveTvIntent));
        }
    }

    private View createSeparator() {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        line.setLayoutParams(lp);
        return line;
    }

    private void addSeparator(LinearLayout rows) {
        rows.addView(createSeparator());
    }

    private Intent pictureIntent() {
        Intent skyworth = new Intent();
        skyworth.setComponent(new ComponentName("com.android.tv.settings",
                "com.android.tv.settings.partnercustomizer.picture.PictureActivity"));
        if (getPackageManager().resolveActivity(skyworth, 0) != null) {
            return skyworth;
        }
        return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
    }

    private Intent soundIntent() {
        Intent sound = new Intent();
        sound.setComponent(new ComponentName("com.android.tv.settings",
                "com.android.tv.settings.device.sound.SoundActivity"));
        if (getPackageManager().resolveActivity(sound, 0) != null) {
            return sound;
        }
        Intent displaysound = new Intent();
        displaysound.setComponent(new ComponentName("com.android.tv.settings",
                "com.android.tv.settings.device.displaysound.DisplaySoundActivity"));
        if (getPackageManager().resolveActivity(displaysound, 0) != null) {
            return displaysound;
        }
        return new Intent(Settings.ACTION_SOUND_SETTINGS);
    }

    private Intent screensaverIntent() {
        Intent sleep = new Intent("com.google.android.pano.action.SLEEP");
        if (getPackageManager().resolveActivity(sleep, 0) != null) {
            return sleep;
        }
        Intent voice = new Intent();
        voice.setComponent(new ComponentName("com.android.tv.settings",
                "com.android.tv.settings.device.display.daydream.DaydreamVoiceAction"));
        if (getPackageManager().resolveActivity(voice, 0) != null) {
            return voice;
        }
        return new Intent("android.settings.DREAM_SETTINGS");
    }

    private void addInputRow(LinearLayout rows, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            action.run();
            finish();
        });
        rows.addView(row);
    }

    private void openInput(String inputId, String inputAction) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (!inputAction.isEmpty()) {
                Intent custom = new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(custom);
                    return;
                } catch (ActivityNotFoundException ignored) {}
            }
        }
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    private void openSourcePanel(String service, String action) {
        Intent i = new Intent(action);
        ComponentName c = ComponentName.unflattenFromString(service);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private Intent liveTvIntent(String liveTvPkg, String liveTvAction) {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    private void openIntent(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_NOTIFICATION || keyCode == KeyEvent.KEYCODE_MENU || keyCode == 308) {
            finish();
            return true;
        }
        if (settingsKeycodes != null) {
            for (int code : settingsKeycodes) {
                if (keyCode == code) {
                    finish();
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}
