package com.yhl.autotap.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yhl.autotap.model.AutomationAction;
import com.yhl.autotap.model.AutomationScript;
import com.yhl.autotap.service.AutoTapAccessibilityService;
import com.yhl.autotap.storage.ScriptStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScriptStore store;
    private AutomationScript selected;
    private LinearLayout content;
    private LinearLayout scriptList;
    private TextView permissionStatus;
    private TextView editorTitle;
    private TextView actionSummary;
    private EditText nameField;
    private EditText repeatField;
    private EditText intervalField;
    private EditText delayField;
    private int density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
        store = new ScriptStore(this);
        buildUi();
        List<AutomationScript> scripts = store.getAll();
        if (scripts.isEmpty()) {
            selected = new AutomationScript();
            selected.name = "我的第一个脚本";
            store.save(selected);
        } else {
            selected = scripts.get(0);
        }
        bindSelected();
        refreshScriptList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
        handler.postDelayed(this::refreshPermissionStatus, 600);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF1F5F9);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(20), dp(18), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        TextView title = text("轻触连点器", 30, 0xFF0F172A);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);
        TextView subtitle = text("本地脚本 · 无需 Root · 点击与滑动录制", 14, 0xFF64748B);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        content.addView(subtitle);

        LinearLayout permissionCard = card();
        permissionCard.addView(sectionTitle("第 1 步：启用手势服务"));
        permissionStatus = text("正在检查…", 15, 0xFF475569);
        permissionStatus.setPadding(0, dp(4), 0, dp(10));
        permissionCard.addView(permissionStatus);
        Button openSettings = primaryButton("打开无障碍设置");
        openSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开系统无障碍设置", Toast.LENGTH_SHORT).show();
            }
        });
        permissionCard.addView(openSettings);
        Button battery = secondaryButton("打开电池优化设置（iQOO 推荐）");
        battery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        permissionCard.addView(battery);
        TextView iqooHint = text("iQOO：还建议在「设置 → 应用 → 权限管理/耗电管理」中允许后台高耗电，并把本应用加入后台白名单。菜单名称会随 OriginOS 版本略有不同。", 12, 0xFF64748B);
        iqooHint.setPadding(0, dp(9), 0, 0);
        permissionCard.addView(iqooHint);
        content.addView(permissionCard);

        LinearLayout editor = card();
        editorTitle = sectionTitle("第 2 步：设置脚本");
        editor.addView(editorTitle);
        editor.addView(fieldLabel("脚本名称"));
        nameField = input();
        editor.addView(nameField);
        editor.addView(fieldLabel("循环次数（0 表示一直循环）"));
        repeatField = numberInput();
        editor.addView(repeatField);
        editor.addView(fieldLabel("每轮间隔（毫秒）"));
        intervalField = numberInput();
        editor.addView(intervalField);
        editor.addView(fieldLabel("每个动作后的等待（毫秒）"));
        delayField = numberInput();
        editor.addView(delayField);
        actionSummary = text("0 个动作", 13, 0xFF64748B);
        actionSummary.setPadding(0, dp(12), 0, dp(8));
        editor.addView(actionSummary);

        LinearLayout saveRow = new LinearLayout(this);
        saveRow.setGravity(Gravity.CENTER_VERTICAL);
        Button save = primaryButton("保存参数");
        Button clear = dangerButton("清空动作");
        saveRow.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        clearParams.setMargins(dp(8), 0, 0, 0);
        saveRow.addView(clear, clearParams);
        editor.addView(saveRow);
        save.setOnClickListener(v -> saveEditor());
        clear.setOnClickListener(v -> confirmClear());

        Button launch = primaryButton("显示悬浮控制条并返回桌面");
        LinearLayout.LayoutParams launchParams = new LinearLayout.LayoutParams(-1, dp(52));
        launchParams.setMargins(0, dp(10), 0, 0);
        editor.addView(launch, launchParams);
        launch.setOnClickListener(v -> launchOverlay());
        content.addView(editor);

        LinearLayout library = card();
        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView libraryTitle = sectionTitle("脚本记录");
        Button create = secondaryButton("＋ 新建");
        libraryHeader.addView(libraryTitle, new LinearLayout.LayoutParams(0, -2, 1));
        libraryHeader.addView(create, new LinearLayout.LayoutParams(dp(96), dp(42)));
        library.addView(libraryHeader);
        scriptList = new LinearLayout(this);
        scriptList.setOrientation(LinearLayout.VERTICAL);
        library.addView(scriptList);
        create.setOnClickListener(v -> createScript());
        content.addView(library);

        LinearLayout usage = card();
        usage.addView(sectionTitle("悬浮条说明"));
        usage.addView(text("≡ 拖动　＋ 添加点击点　● 录制　▶ 播放/暂停　■ 停止　⚙ 参数　× 关闭\n\n蓝色编号点可拖动微调坐标。录制支持点击和单指滑动；完成后点右上角「停止并保存」。音量键与多指手势暂未录制。", 13, 0xFF475569));
        content.addView(usage);
    }

    private void refreshPermissionStatus() {
        boolean connected = AutoTapAccessibilityService.isConnected();
        permissionStatus.setText(connected ? "✓ 手势服务已连接，可以使用悬浮控制条" : "未连接：请在无障碍页面找到「轻触连点器手势服务」并开启");
        permissionStatus.setTextColor(connected ? 0xFF059669 : 0xFFDC2626);
    }

    private void launchOverlay() {
        saveEditor();
        AutoTapAccessibilityService service = AutoTapAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "请先开启手势服务", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        service.showController(selected);
        Toast.makeText(this, "悬浮控制条已显示", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private void createScript() {
        saveEditor();
        selected = new AutomationScript();
        selected.name = "脚本 " + new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        store.save(selected);
        bindSelected();
        refreshScriptList();
    }

    private void bindSelected() {
        if (selected == null) return;
        nameField.setText(selected.name);
        repeatField.setText(String.valueOf(selected.repeatCount));
        intervalField.setText(String.valueOf(selected.loopIntervalMs));
        long delay = selected.actions.isEmpty() ? 300 : selected.actions.get(0).delayAfterMs;
        delayField.setText(String.valueOf(delay));
        actionSummary.setText(getString(com.yhl.autotap.R.string.action_count_summary, selected.actions.size()));
        editorTitle.setText("第 2 步：设置脚本");
    }

    private void saveEditor() {
        if (selected == null) return;
        String name = nameField.getText().toString().trim();
        selected.name = name.isEmpty() ? "未命名脚本" : name;
        selected.repeatCount = parse(repeatField, 10, 0, 1000000);
        selected.loopIntervalMs = parse(intervalField, 500, 0, 3600000);
        long delay = parse(delayField, 300, 0, 3600000);
        for (AutomationAction action : selected.actions) action.delayAfterMs = delay;
        store.save(selected);
        refreshScriptList();
        Toast.makeText(this, "脚本已保存", Toast.LENGTH_SHORT).show();
    }

    private void confirmClear() {
        if (selected == null || selected.actions.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("清空所有动作？")
                .setMessage("脚本名称和循环参数会保留。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    selected.actions.clear();
                    store.save(selected);
                    bindSelected();
                    refreshScriptList();
                })
                .show();
    }

    private void refreshScriptList() {
        scriptList.removeAllViews();
        List<AutomationScript> scripts = store.getAll();
        for (AutomationScript script : scripts) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(9), 0, dp(9));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(script.name, 15, 0xFF0F172A);
            name.setTypeface(null, Typeface.BOLD);
            String loops = script.repeatCount == 0 ? "无限循环" : script.repeatCount + " 次循环";
            info.addView(name);
            info.addView(text(script.actions.size() + " 个动作 · " + loops + " · 间隔 " + script.loopIntervalMs + "ms", 12, 0xFF64748B));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            Button load = compactButton("编辑");
            Button run = compactButton("运行");
            Button delete = compactButton("删除");
            delete.setTextColor(0xFFDC2626);
            row.addView(load);
            row.addView(run);
            row.addView(delete);
            load.setOnClickListener(v -> {
                selected = store.find(script.id);
                bindSelected();
                content.getParent().requestChildFocus(content, editorTitle);
            });
            run.setOnClickListener(v -> {
                selected = store.find(script.id);
                bindSelected();
                launchOverlay();
            });
            delete.setOnClickListener(v -> confirmDelete(script));
            scriptList.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(0xFFE2E8F0);
            scriptList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void confirmDelete(AutomationScript script) {
        new AlertDialog.Builder(this)
                .setTitle("删除「" + script.name + "」？")
                .setMessage("删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    store.delete(script.id);
                    List<AutomationScript> left = store.getAll();
                    if (selected != null && selected.id.equals(script.id)) {
                        if (left.isEmpty()) {
                            selected = new AutomationScript();
                            store.save(selected);
                        } else selected = left.get(0);
                        bindSelected();
                    }
                    refreshScriptList();
                })
                .show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(rounded(Color.WHITE, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(params);
        card.setElevation(dp(2));
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 18, 0xFF0F172A);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(5));
        return title;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 12, 0xFF475569);
        label.setPadding(0, dp(8), 0, dp(4));
        return label;
    }

    private EditText input() {
        EditText field = new EditText(this);
        field.setTextColor(0xFF111827);
        field.setTextSize(15);
        field.setSingleLine(true);
        field.setPadding(dp(11), 0, dp(11), 0);
        field.setBackground(roundedStroke(0xFFF8FAFC, 8, 0xFFCBD5E1));
        field.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(46)));
        return field;
    }

    private EditText numberInput() {
        EditText field = input();
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return field;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(0xFF2563EB, 9));
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(48)));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = primaryButton(value);
        button.setTextColor(0xFF1D4ED8);
        button.setBackground(roundedStroke(0xFFEFF6FF, 9, 0xFFBFDBFE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button dangerButton(String value) {
        Button button = primaryButton(value);
        button.setTextColor(0xFFDC2626);
        button.setBackground(roundedStroke(0xFFFEF2F2, 9, 0xFFFECACA));
        return button;
    }

    private Button compactButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTextColor(0xFF1D4ED8);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(40)));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        return view;
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

    private int parse(EditText field, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(field.getText().toString().trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int dp(int value) {
        return value * density;
    }
}
