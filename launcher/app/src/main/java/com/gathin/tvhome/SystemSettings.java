package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** 标准设置入口不可用时，尝试配置中声明的厂商设置入口。 */
final class SystemSettings {
    private SystemSettings() {}

    static Intent intent(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        if (pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY) != null) return standard;

        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            Intent fallback = new Intent().setComponent(component);
            // 显式 Intent 的 resolveActivity() 会直接返回指定组件，不能用它判断是否已安装。
            if (pm.resolveActivity(fallback, 0) != null) return fallback;
        }
        return standard;
    }
}
