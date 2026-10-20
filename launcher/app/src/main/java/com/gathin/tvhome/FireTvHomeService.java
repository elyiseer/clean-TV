package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Shared stock-HOME fallback. Component name is retained for installed Fire TV builds. */
public final class FireTvHomeService extends AccessibilityService {
    private static final String PREFS = "fire_tv_home";
    private static volatile boolean bound;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FireTvHomePolicy policy;
    private final Runnable redirect = () -> {
        if (!bound || policy == null || !policy.request(enabled(this), SystemClock.elapsedRealtime())) return;
        Intent home = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            PendingIntent.getActivity(this, 1, home,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
            Log.i("FireTvHome", "Home requested; firmware may defer the activity start");
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            Log.w("FireTvHome", "Home request refused", error);
        }
    };

    private static boolean enabled(Context context) {
        // The service itself is disabled until the owner enables it in Accessibility.
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("enabled", true);
    }

    private static void enabled(Context context, boolean value) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", value).apply();
    }

    private static String targetPackage(Context context) {
        String configured = Config.load(context).homeRedirectPackage;
        return configured.isEmpty() ? context.getString(R.string.fire_tv_stock_package) : configured;
    }

    private static Intent stockHome(String pkg) {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
    }

    /** Match only declared HOME activities, never every window from the configured package. */
    private static String[] homeClasses(Context context, String pkg) {
        List<String> classes = new ArrayList<>();
        // HOME filters need not declare CATEGORY_DEFAULT. Keep package/export checks explicit.
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(
                stockHome(pkg), 0)) {
            if (info.activityInfo != null && info.activityInfo.enabled && info.activityInfo.exported
                    && pkg.equals(info.activityInfo.packageName)) {
                classes.add(info.activityInfo.name);
                // Accessibility reports the target class when HOME is declared by an activity-alias.
                if (info.activityInfo.targetActivity != null) classes.add(info.activityInfo.targetActivity);
            }
        }
        if (pkg.equals(context.getString(R.string.fire_tv_stock_package))) {
            for (String cls : context.getResources().getStringArray(R.array.fire_tv_home_classes)) {
                if (!classes.contains(cls)) classes.add(cls);
            }
        }
        return classes.toArray(new String[0]);
    }

    static boolean available(Context context) {
        try {
            String pkg = targetPackage(context);
            return !pkg.equals(context.getPackageName())
                    && context.getPackageManager().getApplicationInfo(pkg, 0).enabled
                    && homeClasses(context, pkg).length > 0;
        } catch (PackageManager.NameNotFoundException unavailable) { return false; }
    }

    /** Used by both existing default-Home entry points; other TVs keep their system picker. */
    static boolean showHomeOptions(Activity activity) {
        if (!available(activity)) return false;
        String pkg = targetPackage(activity);
        boolean amazon = pkg.equals(activity.getString(R.string.fire_tv_stock_package));
        new AlertDialog.Builder(activity).setTitle(R.string.home_redirect_name)
                .setMessage(amazon ? R.string.fire_tv_home_description : R.string.home_redirect_description)
                .setPositiveButton(R.string.enable, (dialog, which) -> {
                    enabled(activity, true);
                    if (!bound) {
                        try { activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                        catch (RuntimeException unavailable) {
                            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNeutralButton(amazon ? R.string.fire_tv_stock_home : R.string.home_redirect_stock_home, (dialog, which) -> {
                    enabled(activity, false);
                    try {
                        String cls = homeClasses(activity, pkg)[0];
                        activity.startActivity(new Intent().setClassName(pkg, cls)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(activity, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
        return true;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        String stockPackage = targetPackage(this);
        policy = new FireTvHomePolicy(stockPackage, homeClasses(this, stockPackage));
        bound = available(this);
        Log.i("FireTvHome", bound ? "Home service bound" : "No supported stock Home installed");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!bound || policy == null || event == null
                || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        int action = policy.window(pkg, cls, enabled(this));
        if (action != 0) handler.removeCallbacks(redirect);
        if (action == 1) handler.postDelayed(redirect, 200);
    }

    @Override public void onInterrupt() { handler.removeCallbacks(redirect); }
    @Override public boolean onUnbind(Intent intent) {
        bound = false;
        handler.removeCallbacks(redirect);
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        bound = false;
        handler.removeCallbacks(redirect);
        super.onDestroy();
    }
}
