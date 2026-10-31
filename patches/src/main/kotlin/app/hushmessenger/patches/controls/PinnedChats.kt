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
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** How many chats Messenger lets you pin above the chat list. */
internal const val PINNED_CHATS = "pinned_chats"

/**
 * The config number Messenger reads its pinned chat limit from. 585 dropped the getter 584 had and reads it in place
 * wherever the pinned count is compared with it: the Pin row's check before a pin, the recount after one, the chat
 * list's fetch of pinned chats and the thread details' Pin state.
 */
internal const val PINNED_CHAT_LIMIT_CONFIG = 0x8205ad0002101eL

internal const val PINNED_CHAT_LIMIT_GATE = "$SETTINGS->pinnedChatLimit(I)I"

private fun pinLimitChanged(detail: String): Nothing =
    throw PatchException("Messenger controls: the pinned chat limit changed ($detail)")

private fun Method.limitLoads(): List<Int> = implementation?.instructions?.withIndex()
    ?.filter { (_, i) -> i.opcode == Opcode.CONST_WIDE && (i as WideLiteralInstruction).wideLiteral == PINNED_CHAT_LIMIT_CONFIG }
    ?.map { it.index }.orEmpty()

/**
 * Index of the `move-result` that takes the limit, or null when this method doesn't read it that way. The read loads
 * the limit's config id once and hands that register pair straight to a static (Object, J)I config read, whose answer
 * the next instruction takes.
 */
internal fun Method.pinnedChatLimitRead(): Int? {
    val loads = limitLoads()
    if (loads.size != 1) return null
    val code = implementation!!.instructions.toList()
    val at = loads.single()
    val id = (code[at] as OneRegisterInstruction).registerA
    val read = code.getOrNull(at + 1) as? FiveRegisterInstruction ?: return null
    val target = (read as ReferenceInstruction).reference as? MethodReference ?: return null
    if (read.opcode != Opcode.INVOKE_STATIC || target.returnType != "I" ||
        target.parameterTypes.map { it.toString() } != listOf("Ljava/lang/Object;", "J")) return null
    if (read.registerCount != 3 || read.registerD != id || read.registerE != id + 1) return null
    return if (code.getOrNull(at + 2)?.opcode == Opcode.MOVE_RESULT) at + 2 else null
}

/** Every method that reads the limit, when each one reads it that way, or none. */
internal fun findPinnedChatLimit(classes: Iterable<ClassDef>): List<Method> {
    val readers = classes.flatMap { it.methods }.filter { it.limitLoads().isNotEmpty() }
    return if (readers.isNotEmpty() && readers.all { it.pinnedChatLimitRead() != null }) readers else emptyList()
}

internal fun MutableMethod.validatePinnedChatLimit(): Int =
    pinnedChatLimitRead() ?: pinLimitChanged("unexpected method ${hookId()}")

/**
 * Each read hands Messenger's own limit to the extension right after taking it, and keeps what comes back. With the
 * switch on that's a higher number, so every check that asks sees the same one. Off, Pause and safe mode give the
 * number back unchanged.
 */
internal fun MutableMethod.injectPinnedChatLimit() {
    val taken = validatePinnedChatLimit()
    val register = (implementation!!.instructions.elementAt(taken) as OneRegisterInstruction).registerA
    addInstructions(taken + 1, """
        invoke-static/range {v$register .. v$register}, $PINNED_CHAT_LIMIT_GATE
        move-result v$register
    """.trimIndent())
}

/** The chat list's long-press menu asks these whether to list Pin and Unpin for a chat. */
internal const val PIN_ROW = "Lcom/facebook/messaging/threadlist/threaditemmenu/plugins/core/pinmenuitem/PinMenuItemImplementation;"
internal const val UNPIN_ROW = "Lcom/facebook/messaging/threadlist/threaditemmenu/plugins/core/unpinmenuitem/UnpinMenuItemImplementation;"
internal const val PIN_ROW_GATE = "$SETTINGS->showPinRows()Z"

private val RETURNS = setOf(Opcode.RETURN, Opcode.RETURN_OBJECT, Opcode.RETURN_VOID, Opcode.RETURN_WIDE)

/** The chat's folder field and Messenger's inbox folder, read by the Pin row's own inbox check. */
internal class PinRowGuard(val folder: String, val inbox: String)

/** Found with the rows, so the Pin row only shows on chats in the main chat list. */
internal var pinRowGuard: PinRowGuard? = null

private fun pinRowChanged(detail: String): Nothing =
    throw PatchException("Messenger controls: the chat menu's Pin row changed ($detail)")

private fun Method.isPinRow() = definingClass == PIN_ROW || definingClass == UNPIN_ROW

/**
 * Index right after the point where a row's own checks are done, or null when the row doesn't read that way. Both rows
 * first rule out what the chat itself forbids (signed-in state, a community chat outside its channel list, already
 * pinned for Pin, not pinned for Unpin) and meet at one static read, from the chat kind branch on the menu's surface and
 * from the pinned branch. What follows is the server's say: a gatekeeper, the chat's capabilities and config flags. The
 * static read's register is written again before anything reads it, and Pin's next instruction is a constant, so both
 * are free for the hook.
 */
internal fun Method.pinRowSite(): Int? {
    if (!isPinRow() || name != "A00" || returnType != "Z" || !AccessFlags.STATIC.isSet(accessFlags)) return null
    if (parameterTypes.map { it.toString() }.let { it.size != 3 || it[0] != FB_USER_SESSION || it[1] != THREAD_SUMMARY }) return null
    val code = implementation?.instructions?.toList() ?: return null
    val surface = parameterRegister(2)
    val jumps = code.indices.filter { code[it] is OffsetInstruction }.associateWith { code.branchTarget(it) }
    val join = code.indices.firstOrNull { i -> code[i].opcode == Opcode.SGET_OBJECT && jumps.values.count { it == i } == 2 &&
        jumps.any { (from, to) -> to == i && code[from].opcode == Opcode.IF_EQ &&
            (code[from] as TwoRegisterInstruction).let { surface == it.registerA || surface == it.registerB } } } ?: return null
    val at = join + 1
    if (at in jumpTargets()) return null
    val scratch = (code[join] as OneRegisterInstruction).registerA
    if (scratch > 15 || parameterRegister(1) > 15) return null
    for (next in code.drop(at).take(4)) {
        if (next is FiveRegisterInstruction && scratch in next.registers()) return null
        if (next.opcode in setOf(Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT) && (next as OneRegisterInstruction).registerA == scratch) {
            if (definingClass == UNPIN_ROW) return at
            val spare = code[at] as? OneRegisterInstruction ?: return null
            return at.takeIf { code[at].opcode in setOf(Opcode.CONST_4, Opcode.CONST_16) && spare.registerA != scratch && spare.registerA <= 15 }
        }
        if (next is OffsetInstruction || next.opcode in RETURNS) return null
    }
    return null
}

private fun FiveRegisterInstruction.registers() =
    listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)

/** The folder field and inbox constant a helper compares first, when it starts `chat.folder == INBOX`. */
private fun Method.inboxCheck(): PinRowGuard? {
    val code = implementation?.instructions?.toList() ?: return null
    if (parameterTypes.map { it.toString() } != listOf(THREAD_SUMMARY) || returnType != "Z") return null
    val read = code.firstOrNull() as? TwoRegisterInstruction ?: return null
    val folder = (read as ReferenceInstruction).reference as? FieldReference ?: return null
    if (read.opcode != Opcode.IGET_OBJECT || folder.definingClass != THREAD_SUMMARY || read.registerB != parameterRegister(0)) return null
    val load = code.take(5).indexOfFirst { it.opcode == Opcode.SGET_OBJECT }.takeIf { it > 0 } ?: return null
    val inbox = (code[load] as ReferenceInstruction).reference as FieldReference
    if (inbox.definingClass != folder.type || inbox.type != folder.type) return null
    val compare = code.getOrNull(load + 1) as? TwoRegisterInstruction ?: return null
    val loaded = (code[load] as OneRegisterInstruction).registerA
    if (compare.opcode != Opcode.IF_NE || setOf(compare.registerA, compare.registerB) != setOf(read.registerA, loaded)) return null
    return PinRowGuard(folder.toString(), inbox.toString())
}

/**
 * Both rows when each reads the expected way and the Pin row's own checks call the inbox helper, or none. The Pin row's
 * inbox check is the first static (ThreadSummary)Z call after its checks that starts by comparing the chat's folder.
 */
internal fun findPinRows(classes: Iterable<ClassDef>): List<Method> {
    pinRowGuard = null
    val byType = classes.associateBy { it.type }
    val pin = byType[PIN_ROW]?.methods?.singleOrNull { it.pinRowSite() != null } ?: return emptyList()
    val unpin = byType[UNPIN_ROW]?.methods?.singleOrNull { it.pinRowSite() != null } ?: return emptyList()
    val thread = pin.parameterRegister(1)
    pinRowGuard = pin.implementation!!.instructions.drop(pin.pinRowSite()!!).asSequence()
        .filter { it.opcode == Opcode.INVOKE_STATIC && (it as FiveRegisterInstruction).registerCount == 1 && it.registerC == thread }
        .mapNotNull { call -> ((call as ReferenceInstruction).reference as MethodReference).let { ref ->
            byType[ref.definingClass]?.methods?.singleOrNull { it.hookId() == ref.callId() } } }
        .firstNotNullOfOrNull { it.inboxCheck() } ?: return emptyList()
    return listOf(pin, unpin)
}

private fun MethodReference.callId() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

/** Every method the switch edits: the limit's readers and both menu rows, all of them or none. */
internal fun findPinnedChats(classes: Iterable<ClassDef>): List<Method> {
    val rows = findPinRows(classes)
    val limit = findPinnedChatLimit(classes)
    return if (rows.isEmpty() || limit.isEmpty()) emptyList() else limit + rows
}

internal fun MutableMethod.validatePinRow(): Int {
    val at = pinRowSite() ?: pinRowChanged("unexpected method ${hookId()}")
    if (definingClass == PIN_ROW && pinRowGuard == null) pinRowChanged("no inbox check")
    return at
}

internal fun MutableMethod.validatePinnedChats(): Int = if (isPinRow()) validatePinRow() else validatePinnedChatLimit()

/**
 * Messenger's server decides per account and per chat whether the menu offers Pin at all (#56), after the row's own
 * checks pass. With the switch on, the Pin row answers yes there for a chat in the main chat list, and the Unpin row
 * for any pinned chat, so a chat pinned this way can always be unpinned. Off, Pause and safe mode leave the server's
 * answer, and so does Pin on an archived chat, a request or another folder.
 */
internal fun MutableMethod.injectPinRow() {
    val at = validatePinRow()
    val code = implementation!!.instructions.toList()
    val scratch = "v${(code[at - 1] as OneRegisterInstruction).registerA}"
    val shown = if (definingClass == UNPIN_ROW) listOf("return $scratch") else {
        val guard = pinRowGuard!!
        val spare = "v${(code[at] as OneRegisterInstruction).registerA}"
        listOf(
            "iget-object $scratch, p1, ${guard.folder}",
            "sget-object $spare, ${guard.inbox}",
            "if-ne $scratch, $spare, :stock_row",
            "const/4 $scratch, 0x1",
            "return $scratch",
        )
    }
    addInstructionsWithLabels(at, (listOf(
        "invoke-static {}, $PIN_ROW_GATE",
        "move-result $scratch",
        "if-eqz $scratch, :stock_row",
    ) + shown).joinToString("\n"), ExternalLabel("stock_row", getInstruction(at)))
}

internal fun MutableMethod.injectPinnedChats() = if (isPinRow()) injectPinRow() else injectPinnedChatLimit()
