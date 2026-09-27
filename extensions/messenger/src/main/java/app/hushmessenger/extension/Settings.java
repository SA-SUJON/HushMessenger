package app.hushmessenger.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

/** Runtime switches default to the original Messenger behavior. */
public final class Settings {
    static volatile SharedPreferences preferences;

    public static void initialize(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("hushmessenger", Context.MODE_PRIVATE);
    }

    static boolean enabled(String key) {
        SharedPreferences prefs = preferences;
        return prefs != null && !prefs.getBoolean("paused", false) && prefs.getBoolean(key, false);
    }

    public static boolean hideStories() { return enabled("stories"); }
    public static boolean hideFacebook() { return enabled("facebook"); }
    public static boolean hideMetaAi() { return enabled("meta_ai"); }
    public static boolean showSubtabs(boolean original) { return original && !enabled("subtabs"); }
    public static boolean suppressTyping() { return enabled("typing"); }
    public static boolean enableBubbles() { return Build.VERSION.SDK_INT >= 30 && enabled("bubbles"); }

    /** Keep non-web routes and Messenger's surrounding link handling intact. */
    public static boolean preferExternalBrowser(boolean original, Uri uri) {
        if (uri == null || !enabled("external_browser")) return original;
        String scheme = uri.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || original;
    }

    private Settings() { }
}
