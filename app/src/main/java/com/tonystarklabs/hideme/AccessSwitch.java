package com.tonystarklabs.hideme;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;

public final class AccessSwitch {

    private static final String DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";

    private AccessSwitch() {
    }

    public static boolean isEnabled(Context context) {
        return contains(context, readServices(context));
    }

    public static boolean canWriteSecureSettings(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void turnOff(Context context) {
        if (YoutubeBlockService.disableIfRunning()) {
            return;
        }
        if (!trySetEnabled(context, false)) {
            openSettings(context);
        }
    }

    public static void turnOn(Context context) {
        if (trySetEnabled(context, true)) {
            return;
        }
        openSettings(context);
    }

    private static boolean trySetEnabled(Context context, boolean enabled) {
        if (!canWriteSecureSettings(context)) {
            return false;
        }
        try {
            String updated = rewrite(context, readServices(context), enabled);
            ContentResolver resolver = context.getContentResolver();
            int accessFlag = updated.isEmpty() ? 0 : 1;
            boolean wroteSwitch = true;
            if (enabled) {
                wroteSwitch = Settings.Secure.putInt(
                        resolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        accessFlag);
            }
            boolean wroteServices = Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updated);
            if (!enabled) {
                wroteSwitch = Settings.Secure.putInt(
                        resolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        accessFlag);
            }
            return wroteServices && wroteSwitch;
        } catch (SecurityException error) {
            return false;
        }
    }

    private static String readServices(Context context) {
        String value = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return value == null ? "" : value;
    }

    static String rewrite(Context context, String current, boolean enable) {
        ArrayList<String> kept = new ArrayList<>();
        boolean present = false;
        if (current != null) {
            for (String part : current.split(":")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (isHideMe(context, part)) {
                    present = true;
                    if (enable) {
                        kept.add(part);
                    }
                } else {
                    kept.add(part);
                }
            }
        }
        if (enable && !present) {
            kept.add(new ComponentName(context, YoutubeBlockService.class).flattenToString());
        }
        return TextUtils.join(":", kept);
    }

    private static boolean contains(Context context, String current) {
        if (current.isEmpty()) {
            return false;
        }
        for (String part : current.split(":")) {
            if (isHideMe(context, part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHideMe(Context context, String stored) {
        ComponentName parsed = ComponentName.unflattenFromString(stored);
        ComponentName ours = new ComponentName(context, YoutubeBlockService.class);
        return ours.equals(parsed);
    }

    private static void openSettings(Context context) {
        ComponentName component = new ComponentName(context, YoutubeBlockService.class);
        String flat = component.flattenToString();
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(DETAILS_SETTINGS);
            intent.putExtra(Intent.EXTRA_COMPONENT_NAME, flat);
        }
        Bundle args = new Bundle();
        args.putString(":settings:fragment_args_key", flat);
        intent.putExtra(":settings:fragment_args_key", flat);
        intent.putExtra(":settings:show_fragment_args", args);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Intent fallback = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            if (!(context instanceof Activity)) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(fallback);
        }
    }
}
