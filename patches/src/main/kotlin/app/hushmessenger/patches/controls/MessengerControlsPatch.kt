package app.hushmessenger.patches.controls

import app.hushmessenger.patches.MessengerTarget
import app.hushmessenger.patches.coexist.validateVersionCode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

internal fun Document.addSettingsEntry() {
    val applications = getElementsByTagName("application")
    if (applications.length != 1) throw PatchException("Messenger controls: expected one application")
    val application = applications.item(0) as Element
    for (tag in listOf("activity", "provider")) {
        val nodes = getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            if ((nodes.item(i) as Element).getAttribute("android:name").startsWith("app.hushmessenger.extension.")) {
                throw PatchException("Messenger controls: settings are already installed. Start with the stock APK.")
            }
        }
    }
    fun Element.child(tag: String, vararg attributes: Pair<String, String>): Element = createElement(tag).also { node ->
        attributes.forEach { (name, value) -> node.setAttribute("android:$name", value) }
        appendChild(node)
    }
    application.child("provider", "name" to "app.hushmessenger.extension.SettingsProvider",
        "authorities" to "com.facebook.orca.hush.settings", "exported" to "false")
    val activity = application.child("activity", "name" to "app.hushmessenger.extension.SettingsActivity",
        "label" to "HushMessenger settings", "exported" to "true",
        "icon" to "@android:drawable/ic_menu_preferences", "taskAffinity" to "app.hushmessenger.settings")
    val filter = activity.child("intent-filter")
    filter.child("action", "name" to "android.intent.action.MAIN")
    filter.child("category", "name" to "android.intent.category.LAUNCHER")
}

private val settingsResources = resourcePatch {
    execute {
        validateVersionCode(packageMetadata.versionCode)
        document("AndroidManifest.xml").use { it.addSettingsEntry() }
    }
}

@Suppress("unused")
val messengerControlsPatch = bytecodePatch(
    name = "Messenger controls and settings",
    description = "Adds seven optional controls and a HushMessenger settings icon in your app drawer. All switches start off.",
    default = true,
) {
    category("Settings")
    compatibleWith(MessengerTarget.COMPATIBILITY)
    dependsOn(settingsResources)
    extendWith("extensions/messenger.mpe")

    execute {
        val classes = mutableListOf<com.android.tools.smali.dexlib2.iface.ClassDef>()
        classDefForEach { classes.add(it) }
        val found = findControls(classes)
        validateControls(found)
        val methods = found.mapValues { (_, matches) -> matches.map { original ->
            mutableClassDefBy(original.definingClass).methods.single { it.hookId() == original.hookId() }
        } }
        methods.values.flatten().forEach { it.validateScratch() }
        methods.getValue("stories").forEach { it.injectSwitch("hideStories", "0x0") }
        methods.getValue("facebook").forEach { it.injectSwitch("hideFacebook", "0x0") }
        methods.getValue("ai_menu").forEach { it.injectSwitch("hideMetaAi", "0x0") }
        methods.getValue("ai_fab").single().injectSwitch("hideMetaAi", "0x0")
        methods.getValue("typing").single().injectSwitch("suppressTyping", "0x0")
        methods.getValue("bubbles").single().injectSwitch("enableBubbles", "0x1")
        methods.getValue("subtabs").single().injectSubtabs()
        methods.getValue("browser").single().injectBrowserPreference()
    }
}
