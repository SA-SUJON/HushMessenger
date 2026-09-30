package app.hushmessenger.extension;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class SettingsTranslationTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    @Before public void reset() {
        Settings.initialize(RuntimeEnvironment.getApplication());
        Settings.preferences.edit().clear().commit();
    }

    @After public void removeTestLocales() {
        SettingsTranslations.LOCALES.remove("es");
    }

    /** Every id a locale table has to cover, with its English. */
    static Map<String, String> englishIds() {
        Map<String, String> ids = new LinkedHashMap<>(SettingsText.ENGLISH);
        for (String[] spec : SettingsActivity.CONTROLS) {
            ids.put(spec[0] + ".title", spec[1]);
            ids.put(spec[0] + ".description", spec[2]);
        }
        return ids;
    }

    /** Each argument the text formats, as argument number and conversion, so reordering with %2$s is allowed. */
    static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        for (int next = 1; m.find(); ) {
            String conversion = m.group(2);
            if (conversion.equals("%") || conversion.equals("n")) continue;
            int argument = m.group(1) != null ? Integer.parseInt(m.group(1).substring(0, m.group(1).length() - 1)) : next++;
            found.add(argument + conversion);
        }
        Collections.sort(found);
        return found;
    }

    /** What's wrong with a locale table: ids it lacks or doesn't know, empty text, and changed placeholders. */
    static List<String> problems(Map<String, String> table) {
        Map<String, String> english = englishIds();
        List<String> problems = new ArrayList<>();
        for (String id : english.keySet()) if (!table.containsKey(id)) problems.add("missing " + id);
        for (Map.Entry<String, String> entry : table.entrySet()) {
            String source = english.get(entry.getKey());
            if (source == null) problems.add("unknown " + entry.getKey());
            else if (entry.getValue().trim().isEmpty()) problems.add("empty " + entry.getKey());
            else if (!placeholders(source).equals(placeholders(entry.getValue()))) problems.add("placeholders differ in " + entry.getKey());
        }
        Collections.sort(problems);
        return problems;
    }

    private static String[][] marked(Map<String, String> english) {
        List<String[]> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : english.entrySet()) pairs.add(new String[] {entry.getKey(), "ES " + entry.getValue()});
        return pairs.toArray(new String[0][]);
    }

    private static boolean showsText(View view, String text) {
        if (view instanceof TextView && text.equals(((TextView) view).getText().toString()) && view.isShown()) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (showsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    @Test public void everyShippedLocaleCoversEveryIdAndKeepsItsPlaceholders() {
        for (Map.Entry<String, Map<String, String>> locale : SettingsTranslations.LOCALES.entrySet())
            assertEquals(locale.getKey(), List.of(), problems(locale.getValue()));
    }

    @Test public void theCheckFindsAMissingIdAnUnknownIdEmptyTextAndAChangedPlaceholder() {
        Map<String, String> table = new HashMap<>();
        for (String[] pair : marked(englishIds())) table.put(pair[0], pair[1]);
        assertEquals(List.of(), problems(table));
        // Reordering arguments by number is fine.
        table.put("results_many", "%2$d controls installed, %1$d shown");
        assertEquals(List.of(), problems(table));
        table.remove("controls");
        table.put("results_one", "%d control installed");
        table.put("update_available", "Version %d is available");
        table.put("people.title", " ");
        table.put("no_such_text", "x");
        assertEquals(List.of("empty people.title", "missing controls", "placeholders differ in results_one",
            "placeholders differ in update_available", "unknown no_such_text"), problems(table));
    }

    @Test public void aLocaleTableTranslatesTheScreenAndKeepsPreferenceKeysAndEnglishSearch() {
        SettingsTranslations.add("es", marked(englishIds()));
        assertEquals(List.of(), problems(SettingsTranslations.LOCALES.get("es")));
        // A regional locale uses its language's table.
        RuntimeEnvironment.setQualifiers("es-rMX-w400dp-h800dp-mdpi");
        try (var screen = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            View root = screen.get().getWindow().getDecorView();
            assertEquals("ES Controls", ((TextView) root.findViewWithTag("tab_controls")).getText().toString());
            assertTrue(showsText(root, "ES Hide People You May Know"));
            assertTrue(showsText(root, "ES Removes suggested people from chats and Notifications."));
            assertFalse(showsText(root, "Hide People You May Know"));
            root.findViewWithTag("people").performClick();
            assertEquals(Map.of("people", true), Settings.preferences.getAll());
            EditText search = root.findViewWithTag("find_control");
            for (String query : new String[] {"People You", "ES Hide People"}) {
                search.setText(query);
                assertTrue(query, root.findViewWithTag("people").isShown());
                assertFalse(query, root.findViewWithTag("stories").isShown());
            }
        }
        assertEquals("ES 3 of 20 installed controls", new SettingsText(java.util.Locale.forLanguageTag("es-MX")).get("results_many", 3, 20));
        assertEquals("3 of 20 installed controls", new SettingsText(java.util.Locale.forLanguageTag("fr-FR")).get("results_many", 3, 20));
    }
}
