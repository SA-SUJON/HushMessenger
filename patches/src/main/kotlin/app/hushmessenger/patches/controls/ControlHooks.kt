/*
 * Messenger anchors adapted from RookieEnough/De-Vanced at 0d01e3dd5ec82b6796b28b82c33fc6af4944b2e6
 * (including ReVanced contributions) and rushiranpise/morphe-patches at
 * 55ca6a05ea3559e95a0876316dcb7caad1f3c9cf. GPL-3.0. See NOTICE.
 */
package app.hushmessenger.patches.controls

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

internal const val SETTINGS = "Lapp/hushmessenger/extension/Settings;"

private val facebookPlugins = setOf(
    "Lcom/facebook/messaging/inbox/tab/plugins/core/tabtoolbarbutton/facebookbutton/facebooktoolbarbutton/FacebookButtonTabButtonImplementation;",
    "Lcom/facebook/messaging/marketplace/plugins/folder/navbarmenuitem/NavBarMenuItemImplementation;",
    "Lcom/facebook/messaging/profile/plugins/core/threadsettingsactionbutton/facebookprofile/ThreadSettingsFacebookProfileActionButton;",
    "Lcom/facebook/messaging/navigation/plugins/drawerfoldersections/fbshortcutsfoldersection/FacebookShortcutsFolderSection;",
    "Lcom/facebook/messaging/communitymessaging/plugins/channelinvite/sharetofacebookbutton/ShareToFacebookButtonImplementation;",
    "Lcom/facebook/messaging/publicchats/plugins/externalsharehscrollbuttons/sharetofacebook/ShareToFacebookHScrollButtonImplementation;",
)

internal val expectedHooks = mapOf(
    "stories" to setOf("LX/1mi;->A00()Z"),
    "facebook" to setOf(
        "LX/Sc2;->A06()Z", "LX/YFi;->A04()Z", "LX/2aP;->A0C()Z", "LX/3Ec;->A00()Z",
        "LX/3me;->A00()Z", "LX/HFd;->A02()Z", "LX/HRL;->A06()Z", "LX/HRM;->A02()Z",
        "LX/JiY;->A06()Z", "LX/Jir;->A02()Z", "LX/JjE;->A00()Z", "LX/JjM;->A01()Z",
        "LX/JjO;->A02()Z", "LX/JjQ;->A02()Z", "LX/JjV;->A03()Z", "LX/JjW;->A03()Z",
        "LX/JjY;->A01()Z", "LX/JjZ;->A01()Z", "LX/Jjb;->A06()Z", "LX/Jjc;->A06()Z", "LX/Jjd;->A06()Z",
    ),
    "ai_menu" to setOf("LX/HFe;->A00()Z", "LX/HFe;->A01()Z", "LX/Jiu;->A00()Z", "LX/Jiu;->A01()Z"),
    "ai_fab" to setOf("LX/6k8;->render(LX/2MZ;)LX/1GG;"),
    "subtabs" to setOf("LX/2UL;->run()V"),
    "typing" to setOf("LX/Ahp;->run()V"),
    "bubbles" to setOf("LX/2ZW;->A00()Z"),
    "browser" to setOf("Lcom/facebook/messaging/browser/util/MessengerBrowserLauncher;->A0L(Landroid/net/Uri;Lcom/facebook/auth/usersession/FbUserSession;)Z"),
)

internal fun Method.hookId() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

/** Match semantics first, then require the complete set from both tested APKs. */
internal fun findControls(classes: Iterable<ClassDef>): Map<String, List<Method>> {
    val found = expectedHooks.keys.associateWith { mutableListOf<Method>() }
    for (cls in classes) {
        val original = cls.fields.firstOrNull { it.name == "__redex_internal_original_name" }
            ?.initialValue.let { (it as? StringEncodedValue)?.value }
        for (method in cls.methods) {
            val instructions = method.implementation?.instructions?.toList() ?: continue
            val refs = instructions.mapNotNull { (it as? ReferenceInstruction)?.reference }
            val strings = refs.filterIsInstance<StringReference>().map { it.string }.toSet()
            val gate = method.returnType == "Z" && method.parameterTypes.isEmpty()
            fun add(key: String) { found.getValue(key).add(method) }
            if (gate && "com.facebook.messaging.friendsinboxunit.plugins.inboxunit.FriendsInboxUnitKillSwitch" in strings) add("stories")
            if (gate && instructions.any {
                it.opcode == Opcode.NEW_INSTANCE &&
                    ((it as? ReferenceInstruction)?.reference as? TypeReference)?.type in facebookPlugins
            }) add("facebook")
            if (gate && strings.any {
                it == "com.facebook.messaging.navigation.plugins.aicreationfolder.folderitem.AiCreationFolderItem" ||
                    it == "com.facebook.messaging.navigation.plugins.aihomefolder.folderitem.AiHomeFolderItem"
            }) add("ai_menu")
            if ("AiFabComponent" in strings && instructions.any { it.opcode == Opcode.RETURN_OBJECT }) add("ai_fab")
            if (method.name == "run" && method.returnType == "V" && method.parameterTypes.isEmpty()) {
                if (original == "InboxSubtabsItemSupplierImplementation\$onSubscribe\$1") add("subtabs")
                if (original == "ConversationTypingContext\$sendActiveStateRunnable\$1") add("typing")
            }
            if (gate && refs.any { it.toString() == "Landroid/os/Build\$VERSION;->SDK_INT:I" } &&
                refs.any { it.toString() == "Landroid/app/ActivityManager;->isLowRamDevice()Z" }) add("bubbles")
            if (method.returnType == "Z" && strings.containsAll(setOf("iab_skipped_reason", "user_prefers_external"))) add("browser")
        }
    }
    return found
}

internal fun validateControls(found: Map<String, List<Method>>) {
    for ((feature, expected) in expectedHooks) {
        val actual = found[feature].orEmpty().map { it.hookId() }
        if (actual.size != expected.size || actual.toSet() != expected) {
            throw PatchException("Messenger controls: $feature hooks differ from the tested build. " +
                "Use an unmodified arm64 Messenger 580.0.0.49.91 (346013387 or 346013440).")
        }
    }
}

/** v0 must be a local register, never a parameter overwritten on the stock branch. */
internal fun MutableMethod.validateScratch() {
    val parameterWords = parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 } +
        if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    if ((implementation?.registerCount ?: 0) <= parameterWords) {
        throw PatchException("Messenger controls: no local register in ${hookId()}")
    }
}

internal fun MutableMethod.injectSwitch(getter: String, result: String) {
    validateScratch()
    val returnCode = when (returnType) {
        "V" -> "return-void"
        "Z" -> "const/4 v0, $result\nreturn v0"
        else -> {
            if (!returnType.startsWith("L")) throw PatchException("Unexpected hook return type: $returnType")
            "const/4 v0, 0x0\nreturn-object v0"
        }
    }
    addInstructionsWithLabels(0, """
        invoke-static {}, $SETTINGS->$getter()Z
        move-result v0
        if-eqz v0, :stock_behavior
        $returnCode
    """.trimIndent(), ExternalLabel("stock_behavior", getInstruction(0)))
}

internal fun MutableMethod.injectSubtabs() {
    val instructions = implementation!!.instructions
    val literal = instructions.getOrNull(2)
    val supplier = instructions.getOrNull(0) as? TwoRegisterInstruction
    val flag = instructions.getOrNull(1) as? TwoRegisterInstruction
    val setter = instructions.getOrNull(3) as? FiveRegisterInstruction
    if (implementation!!.registerCount != 3 || instructions.map { it.opcode } !=
        listOf(Opcode.IGET_OBJECT, Opcode.IGET_OBJECT, Opcode.CONST_4, Opcode.INVOKE_VIRTUAL, Opcode.RETURN_VOID) ||
        supplier?.registerA != 0 || supplier.registerB != 2 || flag?.registerA != 1 || flag.registerB != 0 ||
        setter?.registerCount != 2 || setter.registerC != 1 || setter.registerD != 0 ||
        (literal as? OneRegisterInstruction)?.registerA != 0 ||
        (literal as? WideLiteralInstruction)?.wideLiteral != 1L ||
        (instructions[0] as? ReferenceInstruction)?.reference.toString() !=
            "LX/2UL;->A00:Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;" ||
        (instructions[1] as? ReferenceInstruction)?.reference.toString() !=
            "Lcom/facebook/messaging/inboxsubtabs/plugins/subtabs/itemsupplier/InboxSubtabsItemSupplierImplementation;->A05:Ljava/util/concurrent/atomic/AtomicBoolean;" ||
        (instructions[3] as? ReferenceInstruction)?.reference.toString() != "Ljava/util/concurrent/atomic/AtomicBoolean;->set(Z)V") {
        throw PatchException("Messenger controls: inbox tabs no longer use the checked visibility flag")
    }
    addInstructions(3, "invoke-static {v0}, $SETTINGS->showSubtabs(Z)Z\nmove-result v0")
}

internal fun MutableMethod.injectBrowserPreference() {
    val instructions = implementation!!.instructions
    val getter = instructions.getOrNull(61) as? FiveRegisterInstruction
    if (instructions.getOrNull(60)?.opcode != Opcode.SGET_OBJECT ||
        (instructions[60] as? OneRegisterInstruction)?.registerA != 0 ||
        instructions.getOrNull(61)?.opcode != Opcode.INVOKE_INTERFACE ||
        getter?.registerCount != 3 || getter.registerC != 1 || getter.registerD != 0 || getter.registerE != 3 ||
        (instructions.getOrNull(60) as? ReferenceInstruction)?.reference.toString() != "LX/1D1;->A1U:LX/1BK;" ||
        (instructions.getOrNull(61) as? ReferenceInstruction)?.reference.toString() !=
            "Lcom/facebook/prefs/shared/FbSharedPreferences;->AhC(LX/1BK;Z)Z" ||
        instructions.getOrNull(62)?.opcode != Opcode.MOVE_RESULT ||
        (instructions[62] as? OneRegisterInstruction)?.registerA != 0 ||
        instructions.getOrNull(63)?.opcode != Opcode.IF_EQZ ||
        (instructions[63] as? OneRegisterInstruction)?.registerA != 0 || implementation!!.registerCount != 9) {
        throw PatchException("Messenger controls: external-browser preference no longer matches the tested build")
    }
    // p1 is Uri (v7). Use the same stock preference branch, preserving surrounding handling.
    addInstructions(63, "invoke-static {v0, p1}, $SETTINGS->preferExternalBrowser(ZLandroid/net/Uri;)Z\nmove-result v0")
}
