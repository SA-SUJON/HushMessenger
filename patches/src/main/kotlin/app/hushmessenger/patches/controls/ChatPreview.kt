package app.hushmessenger.patches.controls

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/**
 * Messenger's own chat preview: press and hold a chat and it opens a peek at the conversation with the chat's menu
 * under it, instead of the menu alone. The chat list's long-press handler builds it, but only when Meta's server says
 * so, and falls back to the plain menu otherwise. Some accounts also get the plain menu until a chat has opened since
 * Messenger started. The preview's thread view is built not to mark the chat read.
 */
internal const val CHAT_PREVIEW = "chat_preview"

internal const val CHAT_PREVIEW_FRAGMENT = "Lcom/facebook/messaging/threadlist/threaditemmenu/preview/InboxThreadPreviewDialogFragment;"

/** Meta's server flag for the preview, as 588 numbers it (587 read 0x1010536000042fd). */
internal const val CHAT_PREVIEW_FLAG = 0x10105370000448dL

/** The second flag the long-press handler asks for end-to-end encrypted chats, as 588 numbers it (587 read 0x101055e00004588). */
internal const val CHAT_PREVIEW_ENCRYPTED_FLAG = 0x10105610000473bL

/**
 * The two flags that hold the preview back while Messenger is cold, as 588 numbers them: until a chat has opened or a
 * preview has drawn since Messenger started, either one gives a long press the plain menu. Turning the switch on
 * means a restart, so on an account with either flag on the first press always landed there.
 */
internal val CHAT_PREVIEW_COLD_FLAGS = setOf(0x2101053700044490L, 0x2101053700084493L)

internal const val CHAT_PREVIEW_HELPER = "$SETTINGS->chatPreview(Z)Z"

internal const val CHAT_PREVIEW_COLD_HELPER = "$SETTINGS->chatPreviewCold(I)Z"

private val CHAT_PREVIEW_FLAGS = setOf(CHAT_PREVIEW_FLAG, CHAT_PREVIEW_ENCRYPTED_FLAG)

/**
 * One flag read: which flag, the move-result that takes the answer and the register it fills. [again] marks a read of
 * a register pair an earlier load of the flag filled (#261).
 */
internal class ChatPreviewSite(val flag: Long, val result: Int, val register: Int, val again: Boolean = false) {
    /** A preview flag's no can turn into a yes, a cold-start flag's yes into a no. */
    val helper get() = if (flag in CHAT_PREVIEW_COLD_FLAGS) CHAT_PREVIEW_COLD_HELPER else CHAT_PREVIEW_HELPER
}

private fun Method.buildsChatPreview() = implementation?.instructions?.any {
    it.opcode == Opcode.NEW_INSTANCE && ((it as ReferenceInstruction).reference as TypeReference).type == CHAT_PREVIEW_FRAGMENT
} == true

private fun Method.readsChatPreviewFlag() = implementation?.instructions?.any {
    it.opcode == Opcode.CONST_WIDE && (it as WideLiteralInstruction).wideLiteral == CHAT_PREVIEW_FLAG
} == true

/**
 * Each read loads its flag into a register pair, passes the pair as the last two arguments of a boolean (…J) check,
 * a static one or the config interface's right after a cast to it, and moves the answer into a register. Nothing may
 * jump into that sequence, so the answer the switch sees is the flag's. 586 branches on it at once, 587 sometimes a few
 * instructions later or with if-nez. The cold-start flags are read the same way, and a later check of a pair that still
 * holds its flag counts as another read of it (see askedAgain). The preview flag is loaded once. Any other shape
 * refuses the switch. Sites come in the order their answers do.
 */
internal fun Method.chatPreviewSites(): List<ChatPreviewSite> {
    fun refuse(): Nothing = throw PatchException("Messenger controls: the chat preview check in ${hookId()} no longer matches the tested build")
    val code = implementation?.instructions?.toList() ?: refuse()
    val targets = jumpTargets()
    val sites = code.indices.filter { at ->
        code[at].opcode == Opcode.CONST_WIDE && (code[at] as WideLiteralInstruction).wideLiteral.let {
            it in CHAT_PREVIEW_FLAGS || it in CHAT_PREVIEW_COLD_FLAGS
        }
    }.flatMap { at ->
        val pair = (code[at] as OneRegisterInstruction).registerA
        val cast = code.getOrNull(at + 1)?.takeIf { it.opcode == Opcode.CHECK_CAST }
        val callAt = if (cast == null) at + 1 else at + 2
        val invoke = code.getOrNull(callAt) as? FiveRegisterInstruction ?: refuse()
        val check = (invoke as ReferenceInstruction).reference as? MethodReference ?: refuse()
        val arguments = listOf(invoke.registerC, invoke.registerD, invoke.registerE, invoke.registerF, invoke.registerG).take(invoke.registerCount)
        val result = code.getOrNull(callAt + 1)
        val form = if (cast == null) invoke.opcode == Opcode.INVOKE_STATIC else invoke.opcode == Opcode.INVOKE_INTERFACE &&
            arguments.firstOrNull() == (cast as OneRegisterInstruction).registerA &&
            (cast as ReferenceInstruction).reference.toString() == check.definingClass
        if (!form || check.returnType != "Z" || check.parameterTypes.lastOrNull()?.toString() != "J" ||
            arguments.size < 2 || arguments.takeLast(2) != listOf(pair, pair + 1) || result?.opcode != Opcode.MOVE_RESULT ||
            (at + 1..callAt + 1).any { it in targets }) refuse()
        val flag = (code[at] as WideLiteralInstruction).wideLiteral
        val load = ChatPreviewSite(flag, callAt + 1, (result as OneRegisterInstruction).registerA)
        listOf(load) + (askedAgain(code, flag, at, pair, callAt, targets) ?: refuse())
    }
    if (sites.count { it.flag == CHAT_PREVIEW_FLAG && !it.again } != 1) refuse()
    return sites.sortedBy { it.result }
}

/**
 * Later reads of the flag a load at [at] put in [pair], past its own check at [call]. 588's long-press handler asks the
 * preview flag again from the same pair before it adopts a preview the press made ready, and a no there gives the
 * plain menu (#261). A boolean (…J) check of the pair counts wherever the pair holds what the load put there on every
 * way in (see chatPreviewPairHolds). Null when such a read has another shape than a load's own: static, or the config
 * interface's right after a cast to it, with its answer moved straight into a register, and nothing jumping onto that
 * move or the instruction after it.
 */
private fun Method.askedAgain(code: List<Instruction>, flag: Long, at: Int, pair: Int, call: Int, targets: Set<Int>): List<ChatPreviewSite>? {
    val holds = chatPreviewPairHolds(code, at, pair)
    val found = mutableListOf<ChatPreviewSite>()
    for (i in code.indices) {
        if (i == call || !holds[i]) continue
        val check = (code[i] as? ReferenceInstruction)?.reference as? MethodReference
        val arguments = when (val invoke = code[i]) {
            is FiveRegisterInstruction -> listOf(invoke.registerC, invoke.registerD, invoke.registerE, invoke.registerF, invoke.registerG).take(invoke.registerCount)
            is RegisterRangeInstruction -> (invoke.startRegister until invoke.startRegister + invoke.registerCount).toList()
            else -> emptyList()
        }
        if (check != null && arguments.size >= 2 && arguments.takeLast(2) == listOf(pair, pair + 1) &&
            check.returnType == "Z" && check.parameterTypes.lastOrNull()?.toString() == "J") {
            val cast = code[i - 1].takeIf { it.opcode == Opcode.CHECK_CAST }
            val form = code[i].opcode == Opcode.INVOKE_STATIC || code[i].opcode == Opcode.INVOKE_INTERFACE && cast != null &&
                arguments.first() == (cast as OneRegisterInstruction).registerA &&
                (cast as ReferenceInstruction).reference.toString() == check.definingClass
            val result = code.getOrNull(i + 1)
            if (!form || result?.opcode != Opcode.MOVE_RESULT || i + 1 in targets || i + 2 in targets) return null
            found += ChatPreviewSite(flag, i + 1, (result as OneRegisterInstruction).registerA, again = true)
        }
    }
    return found
}

/** Whether [this] writes either register of [pair], counting the high half of a wide write. */
private fun Instruction.writesChatPreviewPair(pair: Int): Boolean {
    val destination = (this as? OneRegisterInstruction)?.registerA ?: return false
    return opcode.setsRegister() && (destination == pair || destination == pair + 1 || opcode.setsWideRegister() && destination + 1 == pair)
}

/**
 * Which instructions see [pair] holding what the load at [at] put there on every way in: falling through, a branch, a
 * switch case, or a catch handler, which sees what each instruction of its try block saw. Loops count (588's handler
 * jumps back between its two preview reads), and the method's own entry never holds it.
 */
private fun Method.chatPreviewPairHolds(code: List<Instruction>, at: Int, pair: Int): BooleanArray {
    val addresses = code.runningFold(0) { address, instruction -> address + instruction.codeUnits }
    val indexAt = code.indices.associateBy { addresses[it] }
    val from = List(code.size) { mutableListOf<Int>() }
    code.forEachIndexed { i, instruction ->
        if (instruction.opcode.format.isPayloadFormat) return@forEachIndexed
        if (i + 1 < code.size && instruction.opcode.canContinue()) from[i + 1] += i
        if (instruction !is OffsetInstruction || instruction.opcode == Opcode.FILL_ARRAY_DATA) return@forEachIndexed
        val landing = addresses[i] + instruction.codeOffset
        if (instruction.opcode == Opcode.PACKED_SWITCH || instruction.opcode == Opcode.SPARSE_SWITCH) {
            // Case offsets count from the switch instruction, not from its payload.
            (indexAt[landing]?.let(code::get) as? SwitchPayload)?.switchElements
                ?.forEach { case -> indexAt[addresses[i] + case.offset]?.let { from[it] += i } }
        } else indexAt[landing]?.let { from[it] += i }
    }
    val caught = List(code.size) { mutableListOf<Int>() }
    implementation?.tryBlocks?.forEach { block ->
        val end = block.startCodeAddress + block.codeUnitCount
        val covered = code.indices.filter { addresses[it] >= block.startCodeAddress && addresses[it] < end }
        block.exceptionHandlers.forEach { handler -> indexAt[handler.handlerCodeAddress]?.let { caught[it] += covered } }
    }
    val holds = BooleanArray(code.size) { it != 0 }
    fun after(i: Int) = i == at || holds[i] && !code[i].writesChatPreviewPair(pair)
    var changed = true
    while (changed) {
        changed = false
        for (i in 1 until code.size) {
            if (!holds[i] || from[i].all(::after) && caught[i].all { holds[it] && after(it) }) continue
            holds[i] = false
            changed = true
        }
    }
    return holds
}

/**
 * Every method that reads the preview flag, but only when the one method that builds the preview reads it too, as
 * 586's long-press handler does, or a reader calls it, as 587's do: it builds the preview in a static method that the
 * long-press handler and the chat list's other preview path call after asking the flag. Taken only when every read has
 * the tested shape and one of them asks the encrypted-chat flag too.
 */
internal val chatPreviewBuilders = methodCheck { _, method -> method.buildsChatPreview() }

internal val chatPreviewReaders = methodCheck { _, method -> method.readsChatPreviewFlag() }

internal fun findChatPreview(classes: Iterable<ClassDef>): List<Method> {
    val builders = chatPreviewBuilders.methods(classes)
    val readers = chatPreviewReaders.methods(classes)
    val builder = builders.singleOrNull()?.hookId() ?: return emptyList()
    val linked = readers.any { reader ->
        reader.hookId() == builder || reader.implementation!!.instructions.any {
            (it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.INVOKE_STATIC_RANGE) &&
                (it as ReferenceInstruction).reference.toString() == builder
        }
    }
    val sites = readers.map { runCatching { it.chatPreviewSites() }.getOrNull() ?: return emptyList() }.flatten()
    // The long-press handler also asks the encrypted-chat flag, which ties the readers to the chat list.
    if (!linked || sites.none { it.flag == CHAT_PREVIEW_ENCRYPTED_FLAG }) return emptyList()
    return readers.distinctBy { it.hookId() }
}

internal fun MutableMethod.validateChatPreview() {
    chatPreviewSites()
}

/**
 * Each answer passes through the extension, which can only turn a preview flag's no into a yes and a cold-start
 * flag's yes into a no. The rest of the handler still runs as Messenger wrote it, so a chat it can't preview still
 * gets the plain menu. Later reads go first.
 */
internal fun MutableMethod.injectChatPreview() {
    for (site in chatPreviewSites().asReversed()) addInstructions(site.result + 1, """
        invoke-static/range {v${site.register} .. v${site.register}}, ${site.helper}
        move-result v${site.register}
    """.trimIndent())
}
