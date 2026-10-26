package com.tonystarklabs.hideme;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final int[] DAY_ORDER = {
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
    };

    private static final int[] DAY_NAMES = {
            0,
            R.string.sunday,
            R.string.monday,
            R.string.tuesday,
            R.string.wednesday,
            R.string.thursday,
            R.string.friday,
            R.string.saturday
    };

    private static final int[] KEY_IDS = {
            R.id.key_0,
            R.id.key_1,
            R.id.key_2,
            R.id.key_3,
            R.id.key_4,
            R.id.key_5,
            R.id.key_6,
            R.id.key_7,
            R.id.key_8,
            R.id.key_9
    };

    private ScheduleStore schedule;
    private PinStore pins;
    private TextView statusTitle;
    private TextView statusDetail;
    private MaterialButton enableButton;
    private final DayRow[] rows = new DayRow[8];
    private boolean rendering;
    private boolean unlocked;
    private boolean lockVisible;
    private boolean mainReady;
    private boolean showingWait;
    private long lastToggleAt;
    private final StringBuilder entry = new StringBuilder(PinStore.PIN_LENGTH);
    private String firstPin;
    private TextView pinPrompt;
    private TextView pinMessage;
    private final View[] dots = new View[PinStore.PIN_LENGTH];
    private final MaterialButton[] keys = new MaterialButton[10];
    private MaterialButton deleteKey;
    private final Handler countdown = new Handler(Looper.getMainLooper());
    private final Runnable countdownTick = this::refreshLock;
    private final Runnable refreshStatus = this::render;
    private final AccessibilityManager.AccessibilityStateChangeListener accessListener =
            enabled -> render();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        schedule = new ScheduleStore(this);
        pins = new PinStore(this);
        showLock();
    }

    private void bindMain() {
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);
        enableButton = findViewById(R.id.enable_button);
        enableButton.setOnClickListener(view -> toggleAccess());

        LinearLayout dayList = findViewById(R.id.day_list);
        LayoutInflater inflater = getLayoutInflater();
        for (int index = 0; index < DAY_ORDER.length; index++) {
            int day = DAY_ORDER[index];
            View rowView = inflater.inflate(R.layout.item_day, dayList, false);
            DayRow row = new DayRow(
                    rowView.findViewById(R.id.day_name),
                    rowView.findViewById(R.id.day_switch),
                    rowView.findViewById(R.id.day_start),
                    rowView.findViewById(R.id.day_end),
                    rowView.findViewById(R.id.day_note),
                    rowView.findViewById(R.id.day_divider));
            rows[day] = row;
            if (index == 0) {
                row.divider.setVisibility(View.GONE);
            }
            String dayName = getString(DAY_NAMES[day]);
            row.name.setText(dayName);
            row.toggle.setContentDescription(getString(R.string.day_switch, dayName));
            row.toggle.setOnCheckedChangeListener((button, checked) -> {
                if (rendering) {
                    return;
                }
                schedule.setEnabled(day, checked);
                render();
            });
            row.start.setOnClickListener(view -> pickTime(day, true));
            row.end.setOnClickListener(view -> pickTime(day, false));
            dayList.addView(rowView);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        manager.addAccessibilityStateChangeListener(accessListener);
        if (!unlocked) {
            showLock();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onStop() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        manager.removeAccessibilityStateChangeListener(accessListener);
        unlocked = false;
        lockVisible = false;
        firstPin = null;
        entry.setLength(0);
        countdown.removeCallbacks(countdownTick);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        countdown.removeCallbacksAndMessages(null);
        if (enableButton != null) {
            enableButton.removeCallbacks(refreshStatus);
        }
        super.onDestroy();
    }

    private void showLock() {
        lockVisible = true;
        mainReady = false;
        enableButton = null;
        statusTitle = null;
        entry.setLength(0);
        setContentView(R.layout.pin_lock);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        pinPrompt = findViewById(R.id.pin_prompt);
        pinMessage = findViewById(R.id.pin_message);
        dots[0] = findViewById(R.id.dot_1);
        dots[1] = findViewById(R.id.dot_2);
        dots[2] = findViewById(R.id.dot_3);
        dots[3] = findViewById(R.id.dot_4);
        for (int digit = 0; digit < KEY_IDS.length; digit++) {
            int value = digit;
            MaterialButton key = findViewById(KEY_IDS[digit]);
            keys[digit] = key;
            key.setOnClickListener(view -> appendDigit(value));
        }
        deleteKey = findViewById(R.id.key_delete);
        deleteKey.setOnClickListener(view -> deleteDigit());
        refreshLock();
    }

    private void showMain() {
        unlocked = true;
        lockVisible = false;
        countdown.removeCallbacks(countdownTick);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        bindMain();
        mainReady = true;
        render();
    }

    private void appendDigit(int digit) {
        if (isLockedOut() || entry.length() >= PinStore.PIN_LENGTH) {
            return;
        }
        if (!showingWait) {
            pinMessage.setText("");
        }
        entry.append(digit);
        paintDots();
        if (entry.length() == PinStore.PIN_LENGTH) {
            submit(entry.toString());
        }
    }

    private void deleteDigit() {
        if (isLockedOut() || entry.length() == 0) {
            return;
        }
        entry.deleteCharAt(entry.length() - 1);
        paintDots();
    }

    private void submit(String pin) {
        if (!pins.hasPin()) {
            if (firstPin == null) {
                firstPin = pin;
                entry.setLength(0);
                paintDots();
                pinMessage.setText("");
                refreshLock();
                return;
            }
            if (pins.confirm(firstPin, pin) == PinStore.Result.ACCEPTED) {
                firstPin = null;
                showMain();
                return;
            }
            firstPin = null;
            entry.setLength(0);
            paintDots();
            pinMessage.setText(R.string.pin_mismatch);
            refreshLock();
            return;
        }

        PinStore.Result result = pins.verify(pin, System.currentTimeMillis());
        entry.setLength(0);
        paintDots();
        if (result == PinStore.Result.ACCEPTED) {
            showMain();
            return;
        }
        if (result == PinStore.Result.LOCKED) {
            refreshLock();
            return;
        }
        pinMessage.setText(R.string.pin_wrong);
    }

    private void refreshLock() {
        if (!lockVisible || pinPrompt == null) {
            return;
        }
        boolean locked = isLockedOut();
        setKeysEnabled(!locked);
        if (locked) {
            showingWait = true;
            entry.setLength(0);
            paintDots();
            pinPrompt.setText(R.string.pin_unlock);
            long remaining = pins.lockRemainingMillis(System.currentTimeMillis());
            int seconds = (int) ((remaining + 999L) / 1000L);
            pinMessage.setText(getString(R.string.pin_wait, Math.max(seconds, 1)));
            countdown.removeCallbacks(countdownTick);
            countdown.postDelayed(countdownTick, 250L);
            return;
        }
        countdown.removeCallbacks(countdownTick);
        if (showingWait) {
            showingWait = false;
            pinMessage.setText("");
        }
        if (!pins.hasPin()) {
            pinPrompt.setText(firstPin == null ? R.string.pin_create : R.string.pin_confirm);
        } else {
            pinPrompt.setText(R.string.pin_unlock);
        }
        paintDots();
    }

    private boolean isLockedOut() {
        return pins.hasPin() && pins.lockRemainingMillis(System.currentTimeMillis()) > 0L;
    }

    private void setKeysEnabled(boolean enabled) {
        for (MaterialButton key : keys) {
            if (key != null) {
                key.setEnabled(enabled);
            }
        }
        if (deleteKey != null) {
            deleteKey.setEnabled(enabled);
        }
    }

    private void paintDots() {
        for (int index = 0; index < dots.length; index++) {
            if (dots[index] == null) {
                return;
            }
            dots[index].setBackgroundResource(index < entry.length()
                    ? R.drawable.pin_dot_filled
                    : R.drawable.pin_dot_empty);
        }
    }

    private void toggleAccess() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastToggleAt < 800) {
            return;
        }
        lastToggleAt = now;
        if (AccessSwitch.isEnabled(this)) {
            AccessSwitch.turnOff(this);
        } else {
            AccessSwitch.turnOn(this);
        }
        render();
        enableButton.postDelayed(refreshStatus, 400);
    }

    private void pickTime(int day, boolean choosingStart) {
        QuietHours.Day window = schedule.day(day);
        int minutes = choosingStart ? window.startMinutes : window.endMinutes;
        String dayName = getString(DAY_NAMES[day]);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(minutes / 60)
                .setMinute(minutes % 60)
                .setTitleText(getString(
                        choosingStart ? R.string.pick_start_day : R.string.pick_end_day,
                        dayName))
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .build();
        picker.addOnPositiveButtonClickListener(view -> {
            int chosen = picker.getHour() * 60 + picker.getMinute();
            if (choosingStart) {
                schedule.setStart(day, chosen);
            } else {
                schedule.setEnd(day, chosen);
            }
            render();
        });
        picker.show(getSupportFragmentManager(), "day-" + day);
    }

    private void render() {
        if (!mainReady || statusTitle == null) {
            return;
        }
        rendering = true;
        boolean watching = YoutubeBlockService.isEnabled(this);
        boolean hiddenNow = schedule.blocksNow();

        if (!watching) {
            statusTitle.setText(R.string.status_off_title);
            statusTitle.setTextColor(getColor(R.color.ink));
            int detail = AccessSwitch.canWriteSecureSettings(this)
                    ? R.string.status_off_detail
                    : R.string.status_off_detail_settings;
            statusDetail.setText(detail);
            enableButton.setText(R.string.turn_on);
        } else if (hiddenNow) {
            statusTitle.setText(R.string.status_hidden_title);
            statusTitle.setTextColor(getColor(R.color.hidden));
            int allowAgain = schedule.allowAgainMinute();
            if (allowAgain < 0) {
                statusDetail.setText(R.string.status_hidden_always);
            } else {
                statusDetail.setText(getString(
                        R.string.status_hidden_detail,
                        ScheduleStore.formatMinutes(this, allowAgain)));
            }
            enableButton.setText(R.string.pause);
        } else {
            statusTitle.setText(R.string.status_allowed_title);
            statusTitle.setTextColor(getColor(R.color.allowed));
            statusDetail.setText(R.string.status_allowed_detail);
            enableButton.setText(R.string.pause);
        }

        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            bindRow(rows[day], schedule.day(day));
        }
        rendering = false;
    }

    private void bindRow(DayRow row, QuietHours.Day window) {
        row.toggle.setChecked(window.enabled);
        int nameColor = window.enabled ? R.color.ink : R.color.muted;
        row.name.setTextColor(getColor(nameColor));
        int times = window.enabled ? View.VISIBLE : View.GONE;
        row.start.setVisibility(times);
        row.end.setVisibility(times);
        row.start.setText(getString(
                R.string.from_time,
                ScheduleStore.formatMinutes(this, window.startMinutes)));
        if (window.startMinutes > window.endMinutes) {
            row.end.setText(getString(
                    R.string.until_time_next,
                    ScheduleStore.formatMinutes(this, window.endMinutes)));
        } else {
            row.end.setText(getString(
                    R.string.until_time,
                    ScheduleStore.formatMinutes(this, window.endMinutes)));
        }
        boolean sameTime = window.enabled && window.startMinutes == window.endMinutes;
        row.note.setVisibility(sameTime ? View.VISIBLE : View.GONE);
    }

    private static final class DayRow {
        final TextView name;
        final MaterialSwitch toggle;
        final MaterialButton start;
        final MaterialButton end;
        final TextView note;
        final View divider;

        DayRow(
                TextView name,
                MaterialSwitch toggle,
                MaterialButton start,
                MaterialButton end,
                TextView note,
                View divider) {
            this.name = name;
            this.toggle = toggle;
            this.start = start;
            this.end = end;
            this.note = note;
            this.divider = divider;
        }
    }
}
