package com.tonystarklabs.hideme;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;

public class YoutubeBlockService extends AccessibilityService {

    public static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final long HOME_GAP_MS = 500L;

    private static WeakReference<YoutubeBlockService> running;

    private long lastHomeAt;

    public static boolean isEnabled(Context context) {
        return AccessSwitch.isEnabled(context);
    }

    static boolean disableIfRunning() {
        YoutubeBlockService service = running == null ? null : running.get();
        if (service == null) {
            return false;
        }
        service.disableSelf();
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = new WeakReference<>(this);
    }

    @Override
    public void onDestroy() {
        if (running != null && running.get() == this) {
            running = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null || !YOUTUBE_PACKAGE.contentEquals(packageName)) {
            return;
        }
        if (!new ScheduleStore(this).blocksNow()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastHomeAt < HOME_GAP_MS) {
            return;
        }
        lastHomeAt = now;
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    @Override
    public void onInterrupt() {
    }
}
