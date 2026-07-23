package com.yhl.autotap.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.yhl.autotap.model.AutomationAction;
import com.yhl.autotap.model.AutomationScript;

public class AutoTapAccessibilityService extends AccessibilityService {
    private static AutoTapAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private OverlayController overlayController;
    private AutomationScript playingScript;
    private boolean playing;
    private boolean paused;
    private int actionIndex;
    private int finishedLoops;
    private long playbackGeneration;

    public static AutoTapAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isConnected() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        overlayController = new OverlayController(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Window content is intentionally not inspected.
    }

    @Override
    public void onInterrupt() {
        stopPlayback();
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        if (overlayController != null) overlayController.destroy();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public void showController(AutomationScript script) {
        if (overlayController != null) overlayController.show(script);
    }

    public void hideController() {
        stopPlayback();
        if (overlayController != null) overlayController.hide();
    }

    public void play(AutomationScript script) {
        if (script == null || script.actions.isEmpty()) {
            Toast.makeText(this, "脚本里还没有动作", Toast.LENGTH_SHORT).show();
            return;
        }
        playbackGeneration++;
        playingScript = script;
        actionIndex = 0;
        finishedLoops = 0;
        playing = true;
        paused = false;
        if (overlayController != null) overlayController.onPlaybackStateChanged(true, false);
        runNext(playbackGeneration);
    }

    public void togglePause() {
        if (!playing) return;
        paused = !paused;
        if (overlayController != null) overlayController.onPlaybackStateChanged(true, paused);
        if (!paused) runNext(playbackGeneration);
    }

    public void stopPlayback() {
        playbackGeneration++;
        playing = false;
        paused = false;
        playingScript = null;
        handler.removeCallbacksAndMessages(null);
        if (overlayController != null) overlayController.onPlaybackStateChanged(false, false);
    }

    private void runNext(long generation) {
        if (generation != playbackGeneration || !playing || paused || playingScript == null) return;
        if (actionIndex >= playingScript.actions.size()) {
            finishedLoops++;
            if (playingScript.repeatCount > 0 && finishedLoops >= playingScript.repeatCount) {
                stopPlayback();
                Toast.makeText(this, "脚本执行完成", Toast.LENGTH_SHORT).show();
                return;
            }
            actionIndex = 0;
            handler.postDelayed(() -> runNext(generation), playingScript.loopIntervalMs);
            return;
        }

        AutomationAction action = playingScript.actions.get(actionIndex);
        dispatchAction(action, () -> {
            if (generation != playbackGeneration || !playing) return;
            actionIndex++;
            handler.postDelayed(() -> runNext(generation), action.delayAfterMs);
        });
    }

    public void dispatchAction(AutomationAction action, Runnable completion) {
        Path path = new Path();
        path.moveTo(action.startX, action.startY);
        if (AutomationAction.TYPE_SWIPE.equals(action.type)) {
            path.lineTo(action.endX, action.endY);
        }
        long duration = AutomationAction.TYPE_TAP.equals(action.type)
                ? Math.max(40, Math.min(action.durationMs, 200))
                : Math.max(100, action.durationMs);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (completion != null) completion.run();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (completion != null) completion.run();
            }
        }, handler);
        if (!accepted && completion != null) handler.post(completion);
    }
}

