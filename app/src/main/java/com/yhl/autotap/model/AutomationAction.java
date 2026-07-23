package com.yhl.autotap.model;

import org.json.JSONException;
import org.json.JSONObject;

public class AutomationAction {
    public static final String TYPE_TAP = "tap";
    public static final String TYPE_SWIPE = "swipe";

    public String type = TYPE_TAP;
    public float startX;
    public float startY;
    public float endX;
    public float endY;
    public long durationMs = 60;
    public long delayAfterMs = 300;

    public static AutomationAction tap(float x, float y) {
        AutomationAction action = new AutomationAction();
        action.startX = x;
        action.startY = y;
        action.endX = x;
        action.endY = y;
        return action;
    }

    public static AutomationAction swipe(float startX, float startY, float endX, float endY, long durationMs) {
        AutomationAction action = new AutomationAction();
        action.type = TYPE_SWIPE;
        action.startX = startX;
        action.startY = startY;
        action.endX = endX;
        action.endY = endY;
        action.durationMs = Math.max(100, durationMs);
        return action;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("startX", startX);
        json.put("startY", startY);
        json.put("endX", endX);
        json.put("endY", endY);
        json.put("durationMs", durationMs);
        json.put("delayAfterMs", delayAfterMs);
        return json;
    }

    public static AutomationAction fromJson(JSONObject json) {
        AutomationAction action = new AutomationAction();
        action.type = json.optString("type", TYPE_TAP);
        action.startX = (float) json.optDouble("startX", 0);
        action.startY = (float) json.optDouble("startY", 0);
        action.endX = (float) json.optDouble("endX", action.startX);
        action.endY = (float) json.optDouble("endY", action.startY);
        action.durationMs = Math.max(1, json.optLong("durationMs", 60));
        action.delayAfterMs = Math.max(0, json.optLong("delayAfterMs", 300));
        return action;
    }
}

