package com.yhl.autotap.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.yhl.autotap.model.AutomationScript;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScriptStore {
    private static final String PREFS = "automation_scripts";
    private static final String KEY_SCRIPTS = "scripts";

    private final SharedPreferences preferences;

    public ScriptStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<AutomationScript> getAll() {
        List<AutomationScript> scripts = new ArrayList<>();
        String raw = preferences.getString(KEY_SCRIPTS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null) scripts.add(AutomationScript.fromJson(json));
            }
        } catch (JSONException ignored) {
            // A malformed local entry must not prevent the app from opening.
        }
        Collections.sort(scripts, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return scripts;
    }

    public synchronized AutomationScript find(String id) {
        for (AutomationScript script : getAll()) {
            if (script.id.equals(id)) return script;
        }
        return null;
    }

    public synchronized void save(AutomationScript script) {
        List<AutomationScript> scripts = getAll();
        boolean replaced = false;
        script.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i).id.equals(script.id)) {
                scripts.set(i, script);
                replaced = true;
                break;
            }
        }
        if (!replaced) scripts.add(script);
        write(scripts);
    }

    public synchronized void delete(String id) {
        List<AutomationScript> scripts = getAll();
        scripts.removeIf(script -> script.id.equals(id));
        write(scripts);
    }

    private void write(List<AutomationScript> scripts) {
        JSONArray array = new JSONArray();
        for (AutomationScript script : scripts) {
            try {
                array.put(script.toJson());
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY_SCRIPTS, array.toString()).apply();
    }
}

