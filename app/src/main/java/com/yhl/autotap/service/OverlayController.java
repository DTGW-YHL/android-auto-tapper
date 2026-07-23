package com.yhl.autotap.service;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yhl.autotap.model.AutomationAction;
import com.yhl.autotap.model.AutomationScript;
import com.yhl.autotap.storage.ScriptStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class OverlayController {
    private final AutoTapAccessibilityService service;
    private final WindowManager windowManager;
    private final ScriptStore store;
    private final List<Marker> markers = new ArrayList<>();
    private final int density;

    private LinearLayout controller;
    private WindowManager.LayoutParams controllerParams;
    private TextView playButton;
    private TextView countLabel;
    private AutomationScript currentScript;
    private FrameLayout recorder;
    private WindowManager.LayoutParams recorderParams;
    private boolean recorderAdded;
    private View settingsPanel;
    private boolean playbackActive;

    OverlayController(AutoTapAccessibilityService service) {
        this.service = service;
        this.windowManager = (WindowManager) service.getSystemService(AutoTapAccessibilityService.WINDOW_SERVICE);
        this.store = new ScriptStore(service);
        this.density = Math.max(1, Math.round(service.getResources().getDisplayMetrics().density));
    }

    void show(AutomationScript script) {
        currentScript = script == null ? new AutomationScript() : script;
        if (controller == null) createController();
        try {
            windowManager.addView(controller, controllerParams);
        } catch (IllegalStateException ignored) {
        }
        renderMarkers();
        updateCount();
    }

    void hide() {
        stopRecording();
        removeSettingsPanel();
        removeMarkers();
        if (controller != null && controller.isAttachedToWindow()) {
            windowManager.removeView(controller);
        }
    }

    void destroy() {
        hide();
        controller = null;
    }

    private void createController() {
        controller = new LinearLayout(service);
        controller.setOrientation(LinearLayout.VERTICAL);
        controller.setPadding(dp(6), dp(4), dp(6), dp(5));
        controller.setBackground(rounded(0xEE111827, 14));

        LinearLayout row = new LinearLayout(service);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TouchTextView drag = controlButton("≡", "拖动控制条");
        TextView add = controlButton("＋", "添加点击点");
        TextView record = controlButton("●", "开始录制");
        playButton = controlButton("▶", "播放");
        TextView stop = controlButton("■", "停止");
        TextView settings = controlButton("⚙", "参数设置");
        TextView close = controlButton("×", "关闭悬浮窗");
        record.setTextColor(0xFFFB7185);

        row.addView(drag);
        row.addView(add);
        row.addView(record);
        row.addView(playButton);
        row.addView(stop);
        row.addView(settings);
        row.addView(close);
        controller.addView(row);

        countLabel = new TextView(service);
        countLabel.setTextColor(0xFFCBD5E1);
        countLabel.setTextSize(11);
        countLabel.setGravity(Gravity.CENTER);
        countLabel.setPadding(0, dp(2), 0, 0);
        controller.addView(countLabel, new LinearLayout.LayoutParams(-1, dp(20)));

        controllerParams = baseParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        controllerParams.gravity = Gravity.TOP | Gravity.START;
        controllerParams.x = dp(12);
        controllerParams.y = dp(180);

        final int[] origin = new int[2];
        drag.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                origin[0] = controllerParams.x - (int) event.getRawX();
                origin[1] = controllerParams.y - (int) event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                controllerParams.x = origin[0] + (int) event.getRawX();
                controllerParams.y = origin[1] + (int) event.getRawY();
                windowManager.updateViewLayout(controller, controllerParams);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                return true;
            }
            return false;
        });

        add.setOnClickListener(v -> addTapAtCenter());
        record.setOnClickListener(v -> startRecording());
        playButton.setOnClickListener(v -> {
            if (playbackActive) {
                service.togglePause();
            } else {
                saveCurrent();
                setMarkersTouchable(false);
                service.play(currentScript);
            }
        });
        stop.setOnClickListener(v -> service.stopPlayback());
        settings.setOnClickListener(v -> showSettingsPanel());
        close.setOnClickListener(v -> service.hideController());
    }

    private TouchTextView controlButton(String text, String description) {
        TouchTextView view = new TouchTextView();
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(20);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setBackground(selectableRounded(0x00111827));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return view;
    }

    private void addTapAtCenter() {
        DisplayMetrics metrics = service.getResources().getDisplayMetrics();
        AutomationAction action = AutomationAction.tap(metrics.widthPixels / 2f, metrics.heightPixels / 2f);
        currentScript.actions.add(action);
        saveCurrent();
        addMarker(action, currentScript.actions.size());
        updateCount();
    }

    private void renderMarkers() {
        removeMarkers();
        for (int i = 0; i < currentScript.actions.size(); i++) {
            addMarker(currentScript.actions.get(i), i + 1);
        }
    }

    private void addMarker(AutomationAction action, int number) {
        TextView view = new TouchTextView();
        view.setText(AutomationAction.TYPE_SWIPE.equals(action.type) ? number + "↗" : String.valueOf(number));
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(0xDD2563EB, 24));

        int size = dp(46);
        WindowManager.LayoutParams params = baseParams(size, size);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.round(action.startX) - size / 2;
        params.y = Math.round(action.startY) - size / 2;
        Marker marker = new Marker(view, params, action);
        markers.add(marker);

        final float[] delta = new float[2];
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                delta[0] = params.x - event.getRawX();
                delta[1] = params.y - event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float oldX = action.startX;
                float oldY = action.startY;
                params.x = Math.round(event.getRawX() + delta[0]);
                params.y = Math.round(event.getRawY() + delta[1]);
                action.startX = params.x + size / 2f;
                action.startY = params.y + size / 2f;
                if (AutomationAction.TYPE_SWIPE.equals(action.type)) {
                    action.endX += action.startX - oldX;
                    action.endY += action.startY - oldY;
                } else {
                    action.endX = action.startX;
                    action.endY = action.startY;
                }
                windowManager.updateViewLayout(view, params);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                saveCurrent();
                v.performClick();
                return true;
            }
            return false;
        });
        windowManager.addView(view, params);
    }

    private void removeMarkers() {
        for (Marker marker : markers) {
            if (marker.view.isAttachedToWindow()) windowManager.removeView(marker.view);
        }
        markers.clear();
    }

    private void setMarkersTouchable(boolean touchable) {
        for (Marker marker : markers) {
            if (touchable) marker.params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else marker.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (marker.view.isAttachedToWindow()) windowManager.updateViewLayout(marker.view, marker.params);
        }
    }

    void onPlaybackStateChanged(boolean playing, boolean paused) {
        if (playButton == null) return;
        playbackActive = playing;
        if (!playing) {
            playButton.setText("▶");
            playButton.setTextSize(20);
            playButton.setContentDescription("播放");
            setMarkersTouchable(true);
        } else if (paused) {
            playButton.setText("▶ 继续");
            playButton.setTextSize(10);
            playButton.setContentDescription("继续");
        } else {
            playButton.setText("Ⅱ");
            playButton.setTextSize(20);
            playButton.setContentDescription("暂停");
        }
    }

    private void startRecording() {
        if (recorderAdded) return;
        service.stopPlayback();
        if (currentScript == null) currentScript = new AutomationScript();
        if (currentScript.name.equals("新建脚本")) {
            currentScript.name = "录制 " + new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        }
        if (controller != null && controller.isAttachedToWindow()) windowManager.removeView(controller);
        removeMarkers();

        recorder = new FrameLayout(service);
        recorder.setBackgroundColor(0x08000000);
        GestureCaptureView capture = new GestureCaptureView();
        recorder.addView(capture, new FrameLayout.LayoutParams(-1, -1));

        TextView hint = new TextView(service);
        hint.setText("正在录制：点击或滑动屏幕\n动作会同步转发到下层应用");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(14);
        hint.setPadding(dp(14), dp(10), dp(14), dp(10));
        hint.setBackground(rounded(0xDD111827, 10));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hintParams.setMargins(dp(12), dp(36), 0, 0);
        recorder.addView(hint, hintParams);

        Button done = new Button(service);
        done.setText("停止并保存");
        done.setTextSize(13);
        done.setTextColor(Color.WHITE);
        done.setBackground(rounded(0xEEEF4444, 20));
        FrameLayout.LayoutParams doneParams = new FrameLayout.LayoutParams(dp(128), dp(44), Gravity.TOP | Gravity.END);
        doneParams.setMargins(0, dp(35), dp(12), 0);
        recorder.addView(done, doneParams);
        done.setOnClickListener(v -> stopRecording());

        recorderParams = baseParams(-1, -1);
        recorderParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(recorder, recorderParams);
        recorderAdded = true;
    }

    private void stopRecording() {
        if (recorderAdded && recorder != null && recorder.isAttachedToWindow()) {
            windowManager.removeView(recorder);
        }
        recorderAdded = false;
        recorder = null;
        saveCurrent();
        if (controller != null && !controller.isAttachedToWindow() && currentScript != null) {
            try {
                windowManager.addView(controller, controllerParams);
            } catch (IllegalStateException ignored) {
            }
            renderMarkers();
            updateCount();
            Toast.makeText(service, "录制已保存", Toast.LENGTH_SHORT).show();
        }
    }

    private void replayRecordedAction(AutomationAction action) {
        if (!recorderAdded || recorder == null) return;
        recorderParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        recorder.setAlpha(0f);
        windowManager.updateViewLayout(recorder, recorderParams);
        service.dispatchAction(action, () -> {
            if (!recorderAdded || recorder == null || !recorder.isAttachedToWindow()) return;
            recorderParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            recorder.setAlpha(1f);
            windowManager.updateViewLayout(recorder, recorderParams);
        });
    }

    private void showSettingsPanel() {
        if (settingsPanel != null || currentScript == null) return;
        LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(rounded(0xFAFFFFFF, 16));

        TextView title = label("脚本参数", 20, 0xFF111827);
        EditText name = edit(currentScript.name, InputType.TYPE_CLASS_TEXT);
        EditText repeats = edit(String.valueOf(currentScript.repeatCount), InputType.TYPE_CLASS_NUMBER);
        EditText interval = edit(String.valueOf(currentScript.loopIntervalMs), InputType.TYPE_CLASS_NUMBER);
        EditText delay = edit(currentScript.actions.isEmpty() ? "300" : String.valueOf(currentScript.actions.get(0).delayAfterMs), InputType.TYPE_CLASS_NUMBER);
        panel.addView(title);
        panel.addView(label("名称", 12, 0xFF475569));
        panel.addView(name);
        panel.addView(label("循环次数（0 = 无限）", 12, 0xFF475569));
        panel.addView(repeats);
        panel.addView(label("每轮间隔（毫秒）", 12, 0xFF475569));
        panel.addView(interval);
        panel.addView(label("每个动作后等待（毫秒）", 12, 0xFF475569));
        panel.addView(delay);

        LinearLayout buttons = new LinearLayout(service);
        Button cancel = new Button(service);
        cancel.setText("取消");
        Button save = new Button(service);
        save.setText("保存");
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(buttons);

        settingsPanel = panel;
        WindowManager.LayoutParams params = baseParams(dp(310), -2);
        params.gravity = Gravity.CENTER;
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        panel.setTag(params);
        windowManager.addView(panel, params);

        cancel.setOnClickListener(v -> removeSettingsPanel());
        save.setOnClickListener(v -> {
            currentScript.name = name.getText().toString().trim().isEmpty() ? "未命名脚本" : name.getText().toString().trim();
            currentScript.repeatCount = parseInt(repeats, 10, 0, 1000000);
            currentScript.loopIntervalMs = parseInt(interval, 500, 0, 3600000);
            long actionDelay = parseInt(delay, 300, 0, 3600000);
            for (AutomationAction action : currentScript.actions) action.delayAfterMs = actionDelay;
            saveCurrent();
            updateCount();
            removeSettingsPanel();
            Toast.makeText(service, "参数已保存", Toast.LENGTH_SHORT).show();
        });
    }

    private void removeSettingsPanel() {
        if (settingsPanel != null && settingsPanel.isAttachedToWindow()) {
            InputMethodManager imm = (InputMethodManager) service.getSystemService(AutoTapAccessibilityService.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(settingsPanel.getWindowToken(), 0);
            windowManager.removeView(settingsPanel);
        }
        settingsPanel = null;
    }

    private EditText edit(String value, int inputType) {
        EditText editText = new EditText(service);
        editText.setText(value);
        editText.setTextColor(0xFF111827);
        editText.setTextSize(15);
        editText.setSingleLine(true);
        editText.setInputType(inputType);
        editText.setPadding(dp(10), 0, dp(10), 0);
        editText.setBackground(roundedStroke(0xFFF8FAFC, 8, 0xFFCBD5E1));
        editText.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(46)));
        return editText;
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(service);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setPadding(0, dp(7), 0, dp(3));
        return label;
    }

    private int parseInt(EditText field, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(field.getText().toString().trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void saveCurrent() {
        if (currentScript != null) store.save(currentScript);
    }

    private void updateCount() {
        if (countLabel != null && currentScript != null) {
            String repeat = currentScript.repeatCount == 0 ? "无限循环" : "循环 " + currentScript.repeatCount + " 次";
            countLabel.setText(service.getString(
                    com.yhl.autotap.R.string.overlay_script_summary,
                    currentScript.name,
                    currentScript.actions.size(),
                    repeat
            ));
        }
    }

    private WindowManager.LayoutParams baseParams(int width, int height) {
        return new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedStroke(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable selectableRounded(int color) {
        return rounded(color, 10);
    }

    private int dp(int value) {
        return value * density;
    }

    private final class GestureCaptureView extends View {
        private float downX;
        private float downY;
        private long downAt;

        GestureCaptureView() {
            super(service);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX();
                downY = event.getRawY();
                downAt = SystemClock.uptimeMillis();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float upX = event.getRawX();
                float upY = event.getRawY();
                float distance = (float) Math.hypot(upX - downX, upY - downY);
                long duration = Math.max(60, SystemClock.uptimeMillis() - downAt);
                AutomationAction action = distance < dp(18)
                        ? AutomationAction.tap(upX, upY)
                        : AutomationAction.swipe(downX, downY, upX, upY, duration);
                if (distance < dp(18)) performClick();
                currentScript.actions.add(action);
                saveCurrent();
                replayRecordedAction(action);
                return true;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private final class TouchTextView extends TextView {
        TouchTextView() {
            super(service);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private static final class Marker {
        final View view;
        final WindowManager.LayoutParams params;
        final AutomationAction action;

        Marker(View view, WindowManager.LayoutParams params, AutomationAction action) {
            this.view = view;
            this.params = params;
            this.action = action;
        }
    }
}
