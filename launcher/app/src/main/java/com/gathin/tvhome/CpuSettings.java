package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

final class CpuSettings implements AutoCloseable {
    private final Activity activity;
    private final TextView toggle;
    private final Shizuku.OnRequestPermissionResultListener result = (code, grant) -> {
        if (code == 7301) refresh();
    };
    CpuSettings(Activity activity) {
        this.activity = activity;
        toggle = activity.findViewById(R.id.cpuAccessChoice);
        Shizuku.addRequestPermissionResultListener(result);
        toggle.setOnClickListener(v -> {
            boolean enabled = !ShizukuCpu.enabled(activity);
            activity.getSharedPreferences(ShizukuCpu.PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(ShizukuCpu.ENABLED, enabled).apply();
            if (enabled) request();
            refresh();
        });
        activity.findViewById(R.id.cpuAuthorizeChoice).setOnClickListener(v -> request());
        activity.findViewById(R.id.cpuSetupChoice).setOnClickListener(v -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/"))); }
            catch (ActivityNotFoundException e) { Toast.makeText(activity, R.string.cpu_setup_help, Toast.LENGTH_LONG).show(); }
        });
        refresh();
    }
    private void request() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
                Toast.makeText(activity, R.string.cpu_setup_help, Toast.LENGTH_LONG).show();
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(7301);
            }
        } catch (RuntimeException e) { Toast.makeText(activity, R.string.cpu_setup_help, Toast.LENGTH_LONG).show(); }
        refresh();
    }
    private boolean shizukuInstalled() {
        try {
            activity.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    void refresh() {
        boolean installed = shizukuInstalled();
        activity.findViewById(R.id.cpuSettingsGroup).setVisibility(installed ? View.VISIBLE : View.GONE);
        if (!installed) return;
        toggle.setText(activity.getString(R.string.cpu_access_state,
                activity.getString(ShizukuCpu.enabled(activity) ? R.string.on : R.string.off)));
        ((TextView) activity.findViewById(R.id.cpuAuthorizeChoice)).setText(ShizukuCpu.authorized()
                ? R.string.cpu_authorized : R.string.cpu_authorize);
    }
    @Override public void close() { Shizuku.removeRequestPermissionResultListener(result); }
}
