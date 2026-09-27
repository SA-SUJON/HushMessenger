package app.hushmessenger.extension;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A launcher entry keeps settings discoverable without replacing a Messenger menu action. */
public final class SettingsActivity extends Activity {
    private SettingsUi ui;
    private LinearLayout controlsPage, appPage;
    private ScrollView controlsScroll, appScroll;
    private EditText search;
    private String category = "All", page = "Controls";
    private final List<View> controlRows = new ArrayList<>();
    private final List<String[]> installedControls = new ArrayList<>();
    private final List<LinearLayout> groups = new ArrayList<>();
    private final List<Button> categories = new ArrayList<>();
    private final List<Button> tabs = new ArrayList<>();
    private final List<View> tabLines = new ArrayList<>();
    private TextView searchStatus, enabledCount, setupNote;
    private LinearLayout emptyState;
    private Button clearSearch;
    static final String[][] CONTROLS = {
        {"ads", "Hide inbox ads", "Supported inbox ad cards. Live removal isn't verified yet.", "Inbox"},
        {"people", "Hide People You May Know", "Removes suggested people from the inbox.", "Inbox"},
        {"friend_requests", "Hide friend request cards", "Hides cards without accepting or rejecting requests.", "Inbox"},
        {"growth", "Hide growth prompts", "Removes add-more-people prompts.", "Inbox"},
        {"inbox_promotions", "Hide inbox promotions", "Hides Messenger's quick-promotion banners in the chat list.", "Inbox"},
        {"stories", "Hide stories and notes", "Removes the horizontal tray above your chats.", "Inbox"},
        {"subtabs", "Hide inbox tabs", "Hides the Home and Channels tabs inside the inbox.", "Inbox"},
        {"facebook", "Hide Facebook shortcuts", "Hides Facebook buttons, profile shortcuts and sharing shortcuts.", "Navigation"},
        {"meta_ai", "Hide Meta AI buttons", "Hides the floating button, toolbar button and AI menu entries. Search and existing AI chats stay available.", "Navigation"},
        {"moments", "Hide Chat Moments", "Hides Chat Moments from the menu.", "Navigation"},
        {"reels_badge", "Hide Reels badge", "Hides the Reels notification badge.", "Navigation"},
        {"ai_stickers", "Hide AI sticker tools", "Hides the generated-sticker tab and AI sticker suggestions.", "Stickers"},
        {"avatar_stickers", "Hide avatar stickers", "Hides the avatar tab in the sticker keyboard.", "Stickers"},
        {"chat_promotions", "Hide chat promotions", "Hides Messenger's quick-promotion banners inside conversations.", "Conversations"},
        {"suggested_replies", "Hide business reply suggestions", "Hides suggested replies in business conversations.", "Conversations"},
        {"business_suggestions", "Hide business typing suggestions", "Hides business suggestions as you type.", "Conversations"},
        {"event_prompts", "Hide event prompts", "Hides event quick-promotion prompts inside chats.", "Conversations"},
        {"typing", "Hide typing indicator", "Stops your outgoing active-typing signal. Messages and read receipts are separate.", "Conversations"},
        {"external_browser", "Open web links externally", "Uses your default browser for HTTP and HTTPS links. Other link types keep their original behavior.", "Links and bubbles"},
        {"bubbles", "Allow chat bubbles", "Removes the low-memory restriction on Android 11 or newer. Enable bubbles in Android notification settings too.", "Links and bubbles"},
    };

    @Override @SuppressWarnings("deprecation") public void onCreate(Bundle state) {
        Settings.initialize(this);
        boolean light = Settings.preferences.getBoolean("light", false);
        setTheme(light ? android.R.style.Theme_Material_Light_NoActionBar : android.R.style.Theme_Material_NoActionBar);
        super.onCreate(state);
        ui = new SettingsUi(this, light);
        setTitle("HushMessenger settings");
        getWindow().setNavigationBarColor(ui.background);
        getWindow().setStatusBarColor(ui.background);
        getWindow().getDecorView().setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (state != null) {
            page = state.getString("page", "Controls");
            category = state.getString("category", "All");
        }
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.background);
        root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        buildHeader(root);
        controlsPage = ui.column();
        controlsPage.setTag("controls_page");
        root.addView(controlsPage, new LinearLayout.LayoutParams(-1, 0, 1));
        controlsScroll = scrollPage(controlsPage);
        LinearLayout controls = pageContent(controlsScroll);
        buildControls(controls);
        TextView reminder = ui.text("Reopen Messenger after changing inbox controls.", 12, ui.muted, false);
        reminder.setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(16));
        ui.add(controlsPage, reminder, 0);
        appPage = ui.column();
        appPage.setTag("app_page");
        root.addView(appPage, new LinearLayout.LayoutParams(-1, 0, 1));
        appScroll = scrollPage(appPage);
        buildApp(pageContent(appScroll));
        search.setText(state == null ? "" : state.getString("query", ""));
        filterControls(search.getText().toString());
        showPage(page);
        updateSetup();
        if (state != null) {
            controlsScroll.post(() -> controlsScroll.scrollTo(0, state.getInt("controls_scroll")));
            appScroll.post(() -> appScroll.scrollTo(0, state.getInt("app_scroll")));
        }
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout header = ui.column();
        header.setPadding(ui.dp(24), ui.dp(50), ui.dp(24), 0);
        ui.add(root, header, 0);
        LinearLayout brand = ui.row();
        if (ui.largeText) brand.setOrientation(LinearLayout.VERTICAL);
        LinearLayout wordmark = ui.column();
        TextView title = ui.text("HushMessenger", 28, ui.text, true);
        title.setAccessibilityHeading(true);
        ui.add(wordmark, title, 0);
        ui.add(wordmark, ui.text("Make Messenger yours.", 14, ui.muted, false), 5);
        brand.addView(wordmark, new LinearLayout.LayoutParams(ui.largeText ? -1 : 0, -2, ui.largeText ? 0 : 1));
        Button open = ui.button("Open");
        open.setTag("open_messenger");
        open.setContentDescription("Open Messenger");
        open.setBackground(new android.graphics.drawable.InsetDrawable(ui.interactive(ui.background, ui.accent, 8), 0, ui.dp(8), 0, ui.dp(8)));
        open.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        open.setOnClickListener(view -> openMessenger());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(ui.largeText ? -2 : ui.dp(68), -2);
        openParams.leftMargin = ui.dp(ui.largeText ? 0 : 12);
        openParams.topMargin = ui.dp(ui.largeText ? 12 : 0);
        brand.addView(open, openParams);
        ui.add(header, brand, 0);
        LinearLayout navigation = ui.row();
        for (String name : new String[] {"Controls", "App"}) {
            LinearLayout tab = ui.column();
            Button button = ui.button(name);
            button.setTag("tab_" + name.toLowerCase(Locale.ROOT));
            button.setTextSize(16);
            button.setOnClickListener(view -> showPage(name));
            ui.add(tab, button, 0);
            tabs.add(button);
            View underline = new View(this);
            tab.addView(underline, new LinearLayout.LayoutParams(-1, ui.dp(2)));
            tabLines.add(underline);
            navigation.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        ui.add(header, navigation, 0);
    }

    private ScrollView scrollPage(LinearLayout parent) {
        ScrollView view = new ScrollView(this);
        view.setFillViewport(true);
        parent.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        return view;
    }

    private LinearLayout pageContent(ScrollView scroll) {
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(24));
        scroll.addView(content);
        return content;
    }

    private void buildControls(LinearLayout content) {
        LinearLayout setup = ui.panel();
        setup.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(8));
        ui.add(setup, ui.heading("YOUR SETUP"), 0);
        enabledCount = ui.text("", 22, ui.text, true);
        enabledCount.setTag("enabled_count");
        enabledCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(setup, enabledCount, 8);
        setupNote = ui.text("", 13, ui.muted, false);
        ui.add(setup, setupNote, 6);
        ui.rule(setup, 12);
        ui.add(setup, controlRow("paused", "Pause all changes", "", false), 0);
        ui.add(content, setup, 0);
        search = new EditText(this);
        search.setTag("find_control");
        search.setHint("Find a control");
        search.setContentDescription("Find a control");
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setTextColor(ui.text);
        search.setHintTextColor(ui.muted);
        search.setMinHeight(ui.dp(48));
        search.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        search.setBackground(ui.interactive(ui.surface, ui.outline, 8));
        ui.add(content, search, 16);
        LinearLayout filters = ui.row();
        for (String name : new String[] {"All", "Inbox", "Chats", "More"}) {
            Button button = ui.button(name);
            button.setTag("category_" + name.toLowerCase(Locale.ROOT));
            button.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8));
            button.setOnClickListener(view -> {
                category = name;
                filterControls(search.getText().toString());
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (!categories.isEmpty()) params.leftMargin = ui.dp(8);
            filters.addView(button, params);
            categories.add(button);
        }
        ui.add(content, filters, 6);
        searchStatus = ui.text("", 13, ui.muted, false);
        searchStatus.setTag("search_status");
        searchStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(content, searchStatus, 8);
        LinearLayout group = null;
        String last = "";
        for (String[] spec : CONTROLS) {
            if (!Settings.installed.contains(spec[0])) continue;
            if (!last.equals(spec[3])) {
                last = spec[3];
                group = ui.column();
                group.setTag(last);
                LinearLayout heading = ui.row();
                heading.addView(ui.heading(last.toUpperCase(Locale.ROOT)), new LinearLayout.LayoutParams(0, -2, 1));
                heading.addView(ui.text("", 13, ui.muted, true));
                ui.add(group, heading, 0);
                ui.rule(group, 10);
                ui.add(content, group, 24);
                groups.add(group);
            }
            LinearLayout row = controlRow(spec[0], spec[1], spec[2], true);
            row.setTag(spec[3]);
            ui.add(group, row, 0);
            controlRows.add(row);
            installedControls.add(spec);
        }
        emptyState = ui.panel();
        emptyState.setTag("empty_state");
        ui.add(emptyState, ui.text("Find the controls you need", 18, ui.text, true), 0);
        ui.add(emptyState, ui.text("Try a different search or category. Only patches included in this installation appear here.", 14, ui.muted, false), 10);
        clearSearch = ui.button("Clear filters");
        clearSearch.setTag("clear_filters");
        clearSearch.setOnClickListener(view -> {
            category = "All";
            search.setText("");
            filterControls("");
        });
        ui.add(emptyState, clearSearch, 16);
        ui.add(content, emptyState, 20);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterControls(s.toString()); }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    @SuppressWarnings("deprecation")
    private LinearLayout controlRow(String key, String title, String description, boolean divided) {
        LinearLayout row = ui.row();
        LinearLayout labels = ui.column();
        labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout titleLine = ui.row();
        if (ui.largeText) titleLine.setOrientation(LinearLayout.VERTICAL);
        titleLine.addView(ui.text(title, 16, ui.text, false), new LinearLayout.LayoutParams(-2, -2));
        if ("ads".equals(key)) {
            TextView badge = ui.text("Experimental", 11, ui.warning, false);
            badge.setBackground(ui.shape(ui.warningSurface, 0, 4));
            badge.setPadding(ui.dp(6), ui.dp(3), ui.dp(6), ui.dp(3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.leftMargin = ui.dp(ui.largeText ? 0 : 8);
            titleLine.addView(badge, params);
        }
        ui.add(labels, titleLine, 0);
        if (!description.isEmpty()) ui.add(labels, ui.text(description, 14, ui.muted, false), 6);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = ui.toggle(key, title, ("ads".equals(key) ? "Experimental. " : "") + description, Settings.preferences.getBoolean(key, false));
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(ui.dp(48), -2);
        switchParams.leftMargin = ui.dp(12);
        row.addView(control, switchParams);
        control.setOnCheckedChangeListener((button, checked) -> {
            Settings.preferences.edit().putBoolean(key, checked).apply();
            updateSetup();
            Toast.makeText(this, title + (checked ? " on" : " off"), Toast.LENGTH_SHORT).show();
            if ("light".equals(key)) recreate();
        });
        row.setOnClickListener(view -> control.toggle());
        row.setFocusable(false);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setBackground(ui.interactive(ui.background, 0, 0));
        if (divided) {
            row.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {
                ui.shape(ui.line, 0, 0), new android.graphics.drawable.InsetDrawable(ui.interactive(ui.background, 0, 0), 0, 0, 0, ui.dp(1))
            }));
        } else row.setBackground(ui.interactive(ui.surface, 0, 0));
        row.setPadding(0, ui.dp(divided ? 16 : 0), 0, ui.dp(divided ? 16 : 0));
        return row;
    }

    private void buildApp(LinearLayout content) {
        ui.add(content, ui.heading("APPEARANCE"), 4);
        LinearLayout appearance = ui.panel();
        ui.add(appearance, controlRow("light", "Light theme", "Use a light background in settings.", false), 0);
        ui.rule(appearance, 14);
        ui.add(appearance, ui.text("Dark by default. Your choice stays saved.", 13, ui.muted, false), 14);
        ui.add(content, appearance, 12);
        ui.add(content, ui.heading("ABOUT HUSHMESSENGER"), 22);
        LinearLayout about = ui.panel();
        infoRow(about, "Version", BuildConfig.VERSION_NAME);
        ui.rule(about, 12);
        infoRow(about, "Installed controls", Integer.toString(installedControls.size()));
        ui.add(content, about, 12);
        ui.add(content, ui.heading("USING YOUR CONTROLS"), 22);
        ui.add(content, ui.text("Changes save as you go. Reopen Messenger after changing inbox controls.", 14, ui.muted, false), 16);
        ui.add(content, ui.text("Pause keeps your choices and temporarily restores stock behavior.", 14, ui.muted, false), 14);
        LinearLayout help = ui.panel();
        help.setBackground(ui.shape(ui.infoSurface, ui.infoBorder, 8));
        ui.add(help, ui.text("Missing a control?", 16, ui.accent, true), 0);
        ui.add(help, ui.text("Select it in Morphe, then rebuild Messenger. Updating the source alone doesn't install new controls.", 14, ui.muted, false), 8);
        ui.add(content, help, 18);
        Button source = ui.button("Source and licenses");
        source.setTag("source_licenses");
        source.setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/SysAdminDoc/HushMessenger#research-and-credits"))); }
            catch (android.content.ActivityNotFoundException error) { Toast.makeText(this, "No browser is available", Toast.LENGTH_LONG).show(); }
        });
        ui.add(content, source, 16);
        ui.add(content, ui.text("GPL-3.0. Includes work from De-Vanced, ReVanced, Doom and Messenger Cleaner.", 12, ui.muted, false), 16);
        ui.add(content, ui.text("Independent of Meta and Morphe.", 12, ui.muted, false), 20);
    }

    private void infoRow(LinearLayout parent, String title, String value) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(3), 0, ui.dp(4));
        row.addView(ui.text(title, 16, ui.text, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.text(value, 16, ui.accent, true));
        ui.add(parent, row, 4);
    }

    private void showPage(String name) {
        page = name;
        controlsPage.setVisibility("Controls".equals(page) ? View.VISIBLE : View.GONE);
        appPage.setVisibility("App".equals(page) ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabs.size(); i++) {
            Button tab = tabs.get(i);
            boolean selected = tab.getText().toString().equals(page);
            tab.setSelected(selected);
            tab.setTextColor(selected ? ui.accent : ui.muted);
            tab.setBackground(ui.interactive(ui.background, 0, 4));
            tabLines.get(i).setBackgroundColor(selected ? ui.accent : ui.line);
        }
    }

    private void updateSetup() {
        if (enabledCount == null) return;
        int enabled = 0;
        for (String key : Settings.installed) if (Settings.preferences.getBoolean(key, false)) enabled++;
        boolean paused = Settings.preferences.getBoolean("paused", false);
        enabledCount.setText(paused ? "Changes paused" : enabled + (enabled == 1 ? " control enabled" : " controls enabled"));
        setupNote.setText(paused ? enabled + (enabled == 1 ? " saved choice. Turn pause off to resume." : " saved choices. Turn pause off to resume.") : "Your choices are saved automatically.");
    }

    private void filterControls(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        int visible = 0;
        for (int i = 0; i < controlRows.size(); i++) {
            String[] spec = installedControls.get(i);
            String bucket = "Inbox".equals(spec[3]) ? "Inbox" :
                ("Conversations".equals(spec[3]) || "Stickers".equals(spec[3])) ? "Chats" : "More";
            boolean match = ("All".equals(category) || category.equals(bucket)) &&
                (spec[1] + " " + spec[2] + " " + spec[3]).toLowerCase(Locale.ROOT).contains(needle);
            controlRows.get(i).setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) visible++;
        }
        for (LinearLayout group : groups) {
            int count = 0;
            for (View row : controlRows) if (row.getTag().equals(group.getTag()) && row.getVisibility() == View.VISIBLE) count++;
            group.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            ((TextView) ((LinearLayout) group.getChildAt(0)).getChildAt(1)).setText(Integer.toString(count));
        }
        for (Button button : categories) {
            boolean selected = button.getText().toString().equals(category);
            button.setSelected(selected);
            button.setTextColor(selected ? ui.selectedText : ui.muted);
            button.setBackground(new android.graphics.drawable.InsetDrawable(
                ui.interactive(selected ? ui.selected : ui.background, selected ? 0 : ui.outline, 8), 0, ui.dp(6), 0, ui.dp(6)));
            button.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8));
        }
        searchStatus.setText(controlRows.isEmpty() ? "No optional controls installed. Select patches in Morphe and rebuild Messenger." :
            visible == 0 ? "No matching controls. Try another search." : visible + " of " + controlRows.size() + " installed controls");
        emptyState.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
        clearSearch.setVisibility(controlRows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page", page);
        state.putString("category", category);
        state.putString("query", search.getText().toString());
        state.putInt("controls_scroll", controlsScroll.getScrollY());
        state.putInt("app_scroll", appScroll.getScrollY());
        super.onSaveInstanceState(state);
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
}
