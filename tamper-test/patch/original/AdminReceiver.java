package com.example.soalpintar;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/* loaded from: classes.dex */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override // android.app.admin.DeviceAdminReceiver
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
    }

    @Override // android.app.admin.DeviceAdminReceiver
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        try {
            ((DevicePolicyManager) context.getSystemService("device_policy")).setLockTaskPackages(new ComponentName(context, (Class<?>) AdminReceiver.class), new String[]{context.getPackageName()});
        } catch (Exception unused) {
        }
    }
}
