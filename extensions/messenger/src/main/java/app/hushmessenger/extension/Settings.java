package app.hushmessenger.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime switches default to the original Messenger behavior. */
public final class Settings {
    static volatile SharedPreferences preferences;
    static volatile Set<String> installed = Collections.emptySet();
    static boolean preview;
    static final ConcurrentHashMap<String, Long> activeAt = new ConcurrentHashMap<>();

    public static void initialize(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("hushmessenger", Context.MODE_PRIVATE);
        Set<String> features = new HashSet<>();
        preview = false;
        try {
            Bundle metadata = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA).metaData;
            preview = metadata != null && metadata.getBoolean("hush.preview", false);
            if (metadata != null) for (String name : metadata.keySet()) {
                if (name.startsWith("hush.feature.") && metadata.getBoolean(name, false)) features.add(name.substring(13));
            }
        } catch (PackageManager.NameNotFoundException error) {
            android.util.Log.e("HushMessenger", "Can't read installed controls", error);
        }
        installed = Collections.unmodifiableSet(features);
    }

    public static boolean enabled(String key) {
        SharedPreferences prefs = preferences;
        boolean on = installed.contains(key) && prefs != null && !prefs.getBoolean("paused", false)
                && !CrashGuard.isSafeMode() && prefs.getBoolean(key, false);
        if (on) activeAt.put(key, System.currentTimeMillis());
        return on;
    }

    public static long lastActive(String key) {
        Long ts = activeAt.get(key);
        return ts != null ? ts : 0;
    }

    public static boolean hideStories() { return enabled("stories"); }
    public static boolean hideFacebook() { return enabled("facebook"); }
    public static boolean hideMetaAi() { return enabled("meta_ai"); }
    public static boolean showSubtabs(boolean original) { return original && !enabled("subtabs"); }
    public static boolean hidePeopleSection(boolean original) { return original || enabled("people"); }
    public static boolean keepPeopleSection(boolean original) { return original && !enabled("people"); }
    public static boolean suppressTyping() { return enabled("typing"); }
    static boolean available(String key) { return !"bubbles".equals(key) || Build.VERSION.SDK_INT >= 30; }
    public static boolean enableBubbles() { return available("bubbles") && enabled("bubbles"); }
    public static boolean allowScreenshot() { return enabled("allow_screenshot"); }
    public static boolean hideReadReceipts() { return enabled("hide_read_receipts"); }
    public static boolean keepUnsent() { return enabled("keep_unsent"); }

    private static final String KEPT_UNSENT_KEY = "kept_unsent_ids";

    public static void recordUnsent(String messageId) {
        if (messageId == null || messageId.isEmpty()) return;
        SharedPreferences prefs = preferences;
        if (prefs == null) return;
        Set<String> ids = new HashSet<>(prefs.getStringSet(KEPT_UNSENT_KEY, Collections.emptySet()));
        ids.add(messageId);
        prefs.edit().putStringSet(KEPT_UNSENT_KEY, ids).apply();
    }

    public static boolean isKeptUnsent(String messageId) {
        if (messageId == null) return false;
        SharedPreferences prefs = preferences;
        if (prefs == null) return false;
        return prefs.getStringSet(KEPT_UNSENT_KEY, Collections.emptySet()).contains(messageId);
    }

    public static String labelKeptUnsent(String text, String messageId) {
        if (!enabled("keep_unsent") || text == null) return text;
        if (isKeptUnsent(messageId)) return "[unsent] " + text;
        return text;
    }

    private static android.graphics.Typeface systemEmoji;
    public static android.graphics.Typeface systemEmojiTypeface() {
        if (!enabled("use_system_emoji")) return null;
        if (systemEmoji != null) return systemEmoji;
        try {
            systemEmoji = android.graphics.Typeface.createFromFile("/system/fonts/NotoColorEmoji.ttf");
        } catch (Exception ignored) { }
        return systemEmoji;
    }

    /** Null means return the exact original list. Only typed ad rows are removed. */
    public static List<?> filterInboxAds(List<?> items) {
        if (!enabled("ads") || items == null || items.isEmpty()) return null;
        List<Object> filtered = null;
        for (int index = 0; index < items.size(); index++) {
            Object item = items.get(index);
            boolean ad = false;
            for (Class<?> type = item == null ? null : item.getClass(); type != null; type = type.getSuperclass()) {
                if ("com.facebook.messaging.business.inboxads.common.InboxAdsItem".equals(type.getName())) { ad = true; break; }
            }
            if (ad && filtered == null) filtered = new ArrayList<>(items.subList(0, index));
            if (!ad && filtered != null) filtered.add(item);
        }
        return filtered;
    }

    /** Keep non-web routes and Messenger's surrounding link handling intact. */
    public static boolean preferExternalBrowser(boolean original, Uri uri) {
        if (uri == null || !enabled("external_browser")) return original;
        String scheme = uri.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || original;
    }

    @SuppressWarnings("unchecked")
    public static void addMenuSettingsEntry(ArrayList list) {
        try {
            if (list == null || list.isEmpty()) return;
            Object original = list.get(0);
            Object clone = shallowClone(original);
            if (clone == null) return;
            for (Class<?> c = clone.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                java.lang.reflect.Field f;
                try { f = c.getDeclaredField("A00"); } catch (NoSuchFieldException ignored) { continue; }
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object sub = f.get(clone);
                if (sub == null) continue;
                java.lang.reflect.Field label;
                try { label = sub.getClass().getDeclaredField("A05"); } catch (NoSuchFieldException ignored) { continue; }
                if (label.getType() != String.class) continue;
                Object subClone = shallowClone(sub);
                if (subClone == null) continue;
                label.setAccessible(true);
                label.set(subClone, "HushMessenger");
                f.set(clone, subClone);
                list.add(clone);
                return;
            }
        } catch (Exception e) {
            android.util.Log.e("HushMessenger", "addMenuSettingsEntry failed", e);
        }
    }

    public static void handleMenuItemBound(Object viewHolder) {
        try {
            java.lang.reflect.Field textField = viewHolder.getClass().getDeclaredField("A06");
            textField.setAccessible(true);
            Object tv = textField.get(viewHolder);
            if (!(tv instanceof android.widget.TextView)) return;
            CharSequence text = ((android.widget.TextView) tv).getText();
            if (!"HushMessenger".equals(text != null ? text.toString() : null)) return;
            java.lang.reflect.Field viewField = null;
            for (Class<?> c = viewHolder.getClass(); c != null; c = c.getSuperclass()) {
                try { viewField = c.getDeclaredField("A0I"); break; }
                catch (NoSuchFieldException ignored) {}
            }
            if (viewField == null) return;
            viewField.setAccessible(true);
            android.view.View itemView = (android.view.View) viewField.get(viewHolder);
            if (itemView == null) return;
            itemView.setOnClickListener(v -> {
                android.content.Context ctx = v.getContext();
                android.content.Intent intent = new android.content.Intent();
                intent.setClassName(ctx.getPackageName(), "app.hushmessenger.extension.SettingsActivity");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            });
        } catch (Exception e) {
            android.util.Log.e("HushMessenger", "handleMenuItemBound failed", e);
        }
    }

    private static java.lang.reflect.Method allocateMethod;
    private static Object unsafeInstance;

    private static Object shallowClone(Object src) {
        try {
            if (allocateMethod == null) {
                Class<?> u = Class.forName("sun.misc.Unsafe");
                java.lang.reflect.Field f = u.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                unsafeInstance = f.get(null);
                allocateMethod = u.getMethod("allocateInstance", Class.class);
            }
            Object dst = allocateMethod.invoke(unsafeInstance, src.getClass());
            for (Class<?> c = src.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    f.set(dst, f.get(src));
                }
            }
            return dst;
        } catch (Exception e) {
            android.util.Log.e("HushMessenger", "shallowClone failed", e);
            return null;
        }
    }

    private Settings() { }
}
