package com.yhl.autotap.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AutomationScript {
    public String id = UUID.randomUUID().toString();
    public String name = "新建脚本";
    public int repeatCount = 10;
    public long loopIntervalMs = 500;
    public long updatedAt = System.currentTimeMillis();
    public final List<AutomationAction> actions = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("repeatCount", repeatCount);
        json.put("loopIntervalMs", loopIntervalMs);
        json.put("updatedAt", updatedAt);
        JSONArray actionArray = new JSONArray();
        for (AutomationAction action : actions) {
            actionArray.put(action.toJson());
        }
        json.put("actions", actionArray);
        return json;
    }

    public static AutomationScript fromJson(JSONObject json) {
        AutomationScript script = new AutomationScript();
        script.id = json.optString("id", UUID.randomUUID().toString());
        script.name = json.optString("name", "未命名脚本");
        script.repeatCount = Math.max(0, json.optInt("repeatCount", 10));
        script.loopIntervalMs = Math.max(0, json.optLong("loopIntervalMs", 500));
        script.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
        JSONArray array = json.optJSONArray("actions");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject actionJson = array.optJSONObject(i);
                if (actionJson != null) script.actions.add(AutomationAction.fromJson(actionJson));
            }
        }
        return script;
    }
}

