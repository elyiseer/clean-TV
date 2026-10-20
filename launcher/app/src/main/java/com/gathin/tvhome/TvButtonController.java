package com.gathin.tvhome;

import android.content.*;
import android.os.Build;
import android.view.KeyEvent;
import android.widget.Toast;

/** Shared by existing key services; never reads config from the key-event path. */
final class TvButtonController implements AutoCloseable {
    static final String PREFS = "remote_buttons";
    static final String CHOICE = "tv_button";
    static final String FOLLOW = "follow", INPUTS = "inputs", LIVE = "live";
    private final Context context;
    private final boolean defaultInputs;
    private final TvButtonPress press = new TvButtonPress();
    private final SharedPreferences prefs;
    private Intent destination;
    private boolean handles;
    private boolean override;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (p, key) -> refresh();
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    TvButtonController(Context context, boolean defaultInputs) {
        this.context = context;
        this.defaultInputs = defaultInputs;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(changed);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(packages, filter);
        refresh();
    }

    private void refresh() {
        Config config = Config.load(context);
        String choice = prefs.getString(CHOICE, FOLLOW);
        override = !FOLLOW.equals(choice);
        String target = TvButtonChoice.resolve(choice,
                RemoteKeys.parseAppKeys(config.appKeys).get(KeyEvent.KEYCODE_TV), defaultInputs, config.liveTvPackage);
        handles = target != null;
        destination = INPUTS.equals(target) ? new Intent("com.android.tv.action.VIEW_INPUTS")
                : appIntent(target != null && target.startsWith("app:") ? target.substring(4) : null);
        if (LIVE.equals(choice) && destination == null && !config.liveTvAction.isEmpty() && !config.liveTvPackage.isEmpty()) {
            Intent fallback = new Intent(config.liveTvAction).setPackage(config.liveTvPackage);
            if (fallback.resolveActivity(context.getPackageManager()) != null) destination = fallback;
        }
    }

    private Intent appIntent(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        Intent i = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : context.getPackageManager().getLaunchIntentForPackage(pkg);
    }

    boolean hasOverride() { return override; }

    boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_TV || !handles) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return true;
        if (press.update(event.getAction() == KeyEvent.ACTION_DOWN, event.getRepeatCount(), event.isCanceled())) {
            try {
                if (destination == null) throw new ActivityNotFoundException();
                context.startActivity(new Intent(destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(context, R.string.tv_button_unavailable, Toast.LENGTH_LONG).show();
            }
        }
        return true;
    }

    @Override public void close() {
        prefs.unregisterOnSharedPreferenceChangeListener(changed);
        context.unregisterReceiver(packages);
    }
}
