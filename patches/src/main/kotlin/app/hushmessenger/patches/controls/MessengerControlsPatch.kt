package app.hushmessenger.patches.controls

import app.hushmessenger.patches.MessengerTarget
import app.hushmessenger.patches.coexist.validateVersionCode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.iface.Method
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

private var discoveredControls: Map<String, List<Method>> = emptyMap()

private val settingsExtension = bytecodePatch {
    dependsOn(settingsResources)
    extendWith("extensions/messenger.mpe")
    execute {
        val classes = mutableListOf<com.android.tools.smali.dexlib2.iface.ClassDef>()
        classDefForEach { classes.add(it) }
        discoveredControls = findControls(classes)
    }
    finalize { discoveredControls = emptyMap() }
}

internal fun Document.addFeature(key: String) {
    val application = getElementsByTagName("application").item(0) as Element
    val name = "hush.feature.$key"
    val metadata = application.getElementsByTagName("meta-data")
    if ((0 until metadata.length).any { (metadata.item(it) as Element).getAttribute("android:name") == name }) {
        throw PatchException("HushMessenger: $key is already installed. Start with the stock APK.")
    }
    application.appendChild(createElement("meta-data").apply {
        setAttribute("android:name", name)
        setAttribute("android:value", "true")
    })
}

private fun controlPatch(key: String, title: String, summary: String, group: String, vararg hooks: String) = bytecodePatch(
    name = title,
    description = "$summary Optional switch in app drawer > HushMessenger settings. Starts off.",
    default = true,
) {
    category(group)
    compatibleWith(MessengerTarget.COMPATIBILITY)
    dependsOn(settingsExtension, resourcePatch {
        dependsOn(settingsResources)
        execute { document("AndroidManifest.xml").use { it.addFeature(key) } }
    })
    execute {
        val selected = hooks.toSet().ifEmpty { setOf(key) }
        validateControls(discoveredControls, selected)
        for (hook in selected) for (original in discoveredControls.getValue(hook)) {
            val method = mutableClassDefBy(original.definingClass).methods.single { it.hookId() == original.hookId() }
            if (hook in pluginGates) method.validatePluginGate()
            when (hook) {
                "subtabs" -> method.injectSubtabs()
                "browser" -> method.injectBrowserPreference()
                "ads" -> method.injectAdFilter()
                "stories" -> method.injectSwitch("hideStories", "0x0")
                "facebook" -> method.injectSwitch("hideFacebook", "0x0")
                "ai_menu", "ai_fab", "ai_toolbar" -> method.injectSwitch("hideMetaAi", "0x0")
                "typing" -> method.injectSwitch("suppressTyping", "0x0")
                "bubbles" -> method.injectSwitch("enableBubbles", "0x1")
                else -> method.injectFeatureSwitch(key)
            }
        }
    }
}

@Suppress("unused")
val hideInboxAdsPatch = controlPatch("ads", "Hide inbox ads", "Filters typed inbox ad items. Live ad removal still needs an affected-account check.", "Inbox")
@Suppress("unused")
val hidePeoplePatch = controlPatch("people", "Hide People You May Know", "Hides suggested people in the inbox.", "Inbox")
@Suppress("unused")
val hideFriendRequestsPatch = controlPatch("friend_requests", "Hide friend request cards", "Hides friend request cards inside the inbox.", "Inbox")
@Suppress("unused")
val hideGrowthPatch = controlPatch("growth", "Hide growth prompts", "Hides the inbox's add-more-people promotion unit.", "Inbox")
@Suppress("unused")
val hideInboxPromotionsPatch = controlPatch("inbox_promotions", "Hide inbox promotions", "Hides Messenger quick-promotion banners in the chat list.", "Inbox")
@Suppress("unused")
val hideStoriesPatch = controlPatch("stories", "Hide stories and notes", "Hides the horizontal tray above chats.", "Inbox")
@Suppress("unused")
val hideSubtabsPatch = controlPatch("subtabs", "Hide inbox tabs", "Hides the Home and Channels subtabs.", "Inbox")
@Suppress("unused")
val hideFacebookPatch = controlPatch("facebook", "Hide Facebook shortcuts", "Hides Facebook toolbar, profile and sharing shortcuts.", "Navigation")
@Suppress("unused")
val hideMetaAiPatch = controlPatch("meta_ai", "Hide Meta AI buttons", "Hides the floating button, toolbar button and AI menu entries. Search stays available.", "Navigation", "ai_menu", "ai_fab", "ai_toolbar")
@Suppress("unused")
val hideMomentsPatch = controlPatch("moments", "Hide Chat Moments", "Hides the Chat Moments entry in the menu.", "Navigation")
@Suppress("unused")
val hideReelsBadgePatch = controlPatch("reels_badge", "Hide Reels badge", "Hides the Reels notification badge.", "Navigation")
@Suppress("unused")
val hideAiStickersPatch = controlPatch("ai_stickers", "Hide AI sticker tools", "Hides the generated-sticker tab and AI sticker suggestions.", "Stickers")
@Suppress("unused")
val hideAvatarStickersPatch = controlPatch("avatar_stickers", "Hide avatar stickers", "Hides the avatar tab in the sticker keyboard.", "Stickers")
@Suppress("unused")
val hideChatPromotionsPatch = controlPatch("chat_promotions", "Hide chat promotions", "Hides Messenger quick-promotion banners inside conversations.", "Conversations")
@Suppress("unused")
val hideSuggestedRepliesPatch = controlPatch("suggested_replies", "Hide business reply suggestions", "Hides suggested replies in business conversations.", "Conversations")
@Suppress("unused")
val hideBusinessSuggestionsPatch = controlPatch("business_suggestions", "Hide business typing suggestions", "Hides business suggestions as you type.", "Conversations")
@Suppress("unused")
val hideEventPromptsPatch = controlPatch("event_prompts", "Hide event prompts", "Hides event quick-promotion prompts inside chats.", "Conversations")
@Suppress("unused")
val suppressTypingPatch = controlPatch("typing", "Hide typing indicator", "Suppresses your outgoing active-typing signal.", "Conversations")
@Suppress("unused")
val externalBrowserPatch = controlPatch("external_browser", "Open web links externally", "Uses Messenger's external-browser branch for HTTP and HTTPS links.", "Links and bubbles", "browser")
@Suppress("unused")
val enableBubblesPatch = controlPatch("bubbles", "Allow chat bubbles", "Removes the low-memory eligibility limit on Android 11 and newer.", "Links and bubbles")
