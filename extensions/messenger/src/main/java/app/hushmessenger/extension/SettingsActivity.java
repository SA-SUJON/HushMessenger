package app.hushmessenger.extension;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** A launcher entry keeps settings discoverable without replacing a Messenger menu action. */
public final class SettingsActivity extends Activity {
    private LinearLayout content;
    private int foreground;
    private boolean light;

    @Override public void onCreate(Bundle state) {
        Settings.initialize(this);
        light = Settings.preferences.getBoolean("light", false);
        setTheme(light ? android.R.style.Theme_Material_Light_NoActionBar : android.R.style.Theme_Material_NoActionBar);
        super.onCreate(state);
        setTitle("HushMessenger settings");
        foreground = light ? Color.rgb(24, 28, 36) : Color.WHITE;
        getWindow().setNavigationBarColor(light ? Color.WHITE : Color.BLACK);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(light ? Color.WHITE : Color.BLACK);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(32));
        scroll.addView(content);
        setContentView(scroll);
        label("HushMessenger", 28, true);
        label("Settings  ·  v" + BuildConfig.VERSION_NAME, 14, false);
        label("Choose what changes. Everything starts off. Close and reopen Messenger after changing inbox options.", 16, false);
        toggle("paused", "Pause all changes", "Keeps your choices and temporarily restores stock behavior.");
        section("Inbox");
        toggle("stories", "Hide stories and notes", "Removes the horizontal tray above your chats.");
        toggle("subtabs", "Hide inbox tabs", "Hides the Home and Channels tabs inside the inbox.");
        toggle("facebook", "Hide Facebook shortcuts", "Hides Facebook buttons, profile shortcuts and sharing shortcuts.");
        toggle("meta_ai", "Hide Meta AI buttons", "Hides the floating AI button and AI menu entries. Search and existing AI chats stay available.");
        section("Links and conversations");
        toggle("external_browser", "Open web links externally", "Uses your default browser for HTTP and HTTPS links. Other link types keep their original behavior.");
        toggle("typing", "Hide typing indicator", "Stops the outgoing typing signal. Messages and read receipts are separate.");
        toggle("bubbles", "Allow chat bubbles", "Removes Messenger's low-memory restriction on Android 11 or newer. Enable bubbles in Android notification settings too.");
        section("Appearance");
        toggle("light", "Light theme", "Changes this settings screen.");
        Button open = new Button(this);
        open.setTag("open_messenger");
        open.setText("Open Messenger");
        open.setAllCaps(false);
        open.setOnClickListener(view -> openMessenger());
        content.addView(open, new LinearLayout.LayoutParams(-1, dp(52)));
        label("Ad blocking isn't included yet. The older ad loader is absent from these Messenger builds.", 14, false);
        label("Source and licenses: GPL-3.0. Messenger hooks adapted from De-Vanced / ReVanced and Doom's patches. HushMessenger is independent of Meta and Morphe.", 13, false);
        Button source = new Button(this);
        source.setText("Source and licenses");
        source.setAllCaps(false);
        source.setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/SysAdminDoc/HushMessenger#research-and-credits"))); }
            catch (android.content.ActivityNotFoundException error) { Toast.makeText(this, "No browser is available", Toast.LENGTH_LONG).show(); }
        });
        content.addView(source);
    }

    private void section(String text) { label(text, 19, true); }

    private void label(String text, int size, boolean heading) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(heading ? foreground : (light ? 0xff4d5562 : 0xffb9c2d0));
        label.setPadding(0, dp(heading ? 22 : 8), 0, dp(10));
        if (heading) { label.setTypeface(null, android.graphics.Typeface.BOLD); label.setAccessibilityHeading(true); }
        content.addView(label);
    }

    @SuppressWarnings("deprecation")
    private void toggle(String key, String title, String description) {
        Switch control = new Switch(this);
        control.setTag(key);
        control.setText(title);
        control.setTextSize(17);
        control.setTextColor(foreground);
        control.setMinHeight(dp(56));
        control.setChecked(Settings.preferences.getBoolean(key, false));
        control.setThumbTintList(new ColorStateList(new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} }, new int[] { 0xff57a7ff, light ? 0xff777777 : 0xffbbbbbb }));
        control.setOnCheckedChangeListener((button, checked) -> {
            Settings.preferences.edit().putBoolean(key, checked).apply();
            Toast.makeText(this, checked ? title + " on" : title + " off", Toast.LENGTH_SHORT).show();
            if ("light".equals(key)) recreate();
        });
        content.addView(control);
        label(description, 14, false);
        View divider = new View(this);
        divider.setBackgroundColor(light ? 0xffe5e7eb : 0xff23262e);
        content.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void openMessenger() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(getPackageName());
        for (android.content.pm.ResolveInfo match : getPackageManager().queryIntentActivities(query, 0)) {
            if (match.activityInfo.name.equals(getClass().getName())) continue;
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    .setComponent(new ComponentName(getPackageName(), match.activityInfo.name)));
                return;
            } catch (android.content.ActivityNotFoundException error) {
                android.util.Log.e("HushMessenger", "Messenger launcher is unavailable", error);
            }
        }
        Toast.makeText(this, "Open Messenger from your app drawer", Toast.LENGTH_LONG).show();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
