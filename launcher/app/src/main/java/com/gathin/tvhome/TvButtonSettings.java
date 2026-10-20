package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.TextView;
import java.util.*;

/** On-TV chooser, independent of the Inputs menu and theme layout preferences. */
final class TvButtonSettings {
    static void show(Activity activity) {
        android.content.SharedPreferences prefs = activity.getSharedPreferences(TvButtonController.PREFS, Context.MODE_PRIVATE);
        List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        labels.add(activity.getString(R.string.tv_button_follow)); values.add(TvButtonController.FOLLOW);
        labels.add(activity.getString(R.string.tv_button_inputs)); values.add(TvButtonController.INPUTS);
        labels.add(activity.getString(R.string.live_tv)); values.add(TvButtonController.LIVE);
        Map<String, String> apps = new HashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(query, 0)) {
                if (info.activityInfo.enabled && info.activityInfo.applicationInfo.enabled)
                    apps.put(info.activityInfo.packageName, info.loadLabel(activity.getPackageManager()).toString());
            }
        }
        List<String> packages = new ArrayList<>(apps.keySet());
        packages.sort((a,b) -> { int order = apps.get(a).compareToIgnoreCase(apps.get(b)); return order != 0 ? order : a.compareTo(b); });
        for (String pkg : packages) { labels.add(apps.get(pkg)); values.add("app:" + pkg); }
        String current = prefs.getString(TvButtonController.CHOICE, TvButtonController.FOLLOW);
        if (!values.contains(current)) { labels.add(activity.getString(R.string.tv_button_unavailable)); values.add(current); }
        new AlertDialog.Builder(activity).setTitle(R.string.tv_button)
                .setSingleChoiceItems(labels.toArray(new String[0]), values.indexOf(current), (dialog, which) -> {
                    prefs.edit().putString(TvButtonController.CHOICE, values.get(which)).apply();
                    dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    static void refreshStatus(Activity activity, TextView text) {
        android.view.accessibility.AccessibilityManager manager = (android.view.accessibility.AccessibilityManager)
                activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean active = false;
        for (android.accessibilityservice.AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(-1)) {
            String id = info.getId();
            if (id.equals(activity.getPackageName() + "/.InputKeyService") || id.equals(activity.getPackageName() + "/.HomeKeyService")
                    || id.equals(activity.getPackageName() + "/" + InputKeyService.class.getName())
                    || id.equals(activity.getPackageName() + "/" + HomeKeyService.class.getName())) active = true;
        }
        text.setText(active ? R.string.tv_button_helper_enabled : R.string.tv_button_helper_needed);
        text.setOnClickListener(v -> activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }
}
