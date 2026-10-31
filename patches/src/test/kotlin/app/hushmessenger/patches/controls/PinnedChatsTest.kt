package app.hushmessenger.patches.controls

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal const val SYNTHETIC_PINNED_CHAT_LIMIT = "LX/2na;->A00($FB_USER_SESSION)I"
private const val CONFIG_READ = "LX/17P;->A0A(Ljava/lang/Object;J)I"
private val STATIC = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value

/** A read as Messenger writes it in place: the session's config, the limit's id, one read, its answer compared after. */
private val READ_BODY = """
    invoke-static {p0}, LX/2on;->A0F(Ljava/lang/Object;)LX/5bt;
    move-result-object p0
    const-wide v0, 0x8205ad0002101eL
    invoke-static {p0, v0, v1}, $CONFIG_READ
    move-result v0
    const/4 v1, 0x3
    if-ge v0, v1, :done
    const/4 v0, 0x0
    :done
    return v0
""".trimIndent()

private fun reader(body: String = READ_BODY, id: String = SYNTHETIC_PINNED_CHAT_LIMIT, flags: Int = STATIC, registers: Int = 3) =
    fixtureMethod(id, body, registers = registers, flags = flags)

private const val MENU_SURFACE = "LX/1o8;"
internal const val SYNTHETIC_PIN_ROW = "$PIN_ROW->A00($FB_USER_SESSION$THREAD_SUMMARY$MENU_SURFACE)Z"
internal const val SYNTHETIC_UNPIN_ROW = "$UNPIN_ROW->A00($FB_USER_SESSION$THREAD_SUMMARY$MENU_SURFACE)Z"
private const val IN_INBOX = "LX/2kH;->A00($THREAD_SUMMARY)Z"
private const val FOLDER = "$THREAD_SUMMARY->A10:LX/1B7;"
private const val INBOX = "LX/1B7;->A0K:LX/1B7;"

/** The row's own checks as Messenger writes them: signed in, the community chat kind, then pinned or not. */
private fun rowChecks(pinned: String, channel: String) = """
    const/4 v4, 0x0
    invoke-static {p0}, LX/HQw;->A1N($FB_USER_SESSION)Z
    move-result v0
    if-nez v0, :no
    iget-object v6, p1, $THREAD_SUMMARY->A0e:Lcom/facebook/messaging/model/threadkey/ThreadKey;
    invoke-virtual {v6}, Lcom/facebook/messaging/model/threadkey/ThreadKey;->A1L()Z
    move-result v0
    if-eqz v0, :plain
    iget-boolean v0, p1, $THREAD_SUMMARY->A2R:Z
    $channel v0, :no
    sget-object v0, $MENU_SURFACE->A07:$MENU_SURFACE
    if-eq p2, v0, :server
    :no
    return v4
    :plain
    iget-boolean v0, p1, $THREAD_SUMMARY->A2Q:Z
    $pinned v0, :server
    return v4
""".trimIndent()

/** Pin's server half: a gatekeeper read, then the inbox helper, then the answer. */
private val PIN_SERVER = """
    :server
    sget-object v0, LX/2kH;->A00:LX/2zt;
    const/16 v7, 0x189
    invoke-static {v7}, LX/2zj;->A01(I)Lcom/google/common/base/Optional;
    move-result-object v0
    invoke-static {p1}, $IN_INBOX
    move-result v0
    if-eqz v0, :no2
    const/4 v4, 0x1
    :no2
    return v4
""".trimIndent()

private val PIN_BODY = rowChecks(pinned = "if-eqz", channel = "if-nez") + "\n" + PIN_SERVER
private val UNPIN_BODY = rowChecks(pinned = "if-nez", channel = "if-eqz") + "\n" + """
    :server
    sget-object v0, LX/2kH;->A00:LX/2zt;
    invoke-static {}, LX/HQx;->A1V()Z
    move-result v0
    return v0
""".trimIndent()

private val INBOX_BODY = """
    iget-object v1, p0, $FOLDER
    const/4 v3, 0x0
    if-eqz v1, :no
    sget-object v0, $INBOX
    if-ne v1, v0, :no
    const/4 v3, 0x1
    :no
    return v3
""".trimIndent()

private fun pinRow(body: String = PIN_BODY, id: String = SYNTHETIC_PIN_ROW) = fixtureMethod(id, body, registers = 11, flags = STATIC)
private fun unpinRow(body: String = UNPIN_BODY) = fixtureMethod(SYNTHETIC_UNPIN_ROW, body, registers = 9, flags = STATIC)
private fun inboxHelper(body: String = INBOX_BODY) = fixtureMethod(IN_INBOX, body, registers = 5, flags = STATIC)

/** Both menu rows and the inbox helper the Pin row calls. */
internal fun pinRowFixture(pin: List<MutableMethod> = listOf(pinRow()), unpin: List<MutableMethod> = listOf(unpinRow()),
    helper: List<MutableMethod> = listOf(inboxHelper())): List<MutableClass> =
    listOf(PIN_ROW to pin, UNPIN_ROW to unpin, "LX/2kH;" to helper).filter { it.second.isNotEmpty() }.map { (type, methods) -> fixtureClass(type, methods) }

internal fun pinnedChatLimitFixture(vararg methods: MutableMethod = arrayOf(reader())): List<MutableClass> =
    methods.map { fixtureClass(it.definingClass, listOf(it)) } + pinRowFixture()

private fun Method.code() = implementation!!.instructions.toList()

class PinnedChatsTest {
    @AfterTest fun reset() {
        activeProfile = SYNTHETIC_PROFILE
        pinRowGuard = null
    }

    private fun found(classes: List<MutableClass>) = mirroredControls(classes).getValue(PINNED_CHATS).map { it.hookId() }.toSet()

    @Test fun theLimitsReadIsFoundByItsConfigIdAlongWithBothMenuRows() {
        assertEquals(setOf(SYNTHETIC_PINNED_CHAT_LIMIT, SYNTHETIC_PIN_ROW, SYNTHETIC_UNPIN_ROW), found(pinnedChatLimitFixture()))
        validateControls(mirroredControls(pinnedChatLimitFixture()), setOf(PINNED_CHATS))
    }

    @Test fun everyReaderIsFoundWhateverItsShape() {
        val other = "LX/HRN;->A0Q(Ljava/lang/Object;Ljava/lang/Object;I)V"
        val classes = pinnedChatLimitFixture(reader(), reader(READ_BODY.replace("return v0", "return-void"), id = other, flags = AccessFlags.PUBLIC.value, registers = 6))
        assertEquals(setOf(SYNTHETIC_PINNED_CHAT_LIMIT, other, SYNTHETIC_PIN_ROW, SYNTHETIC_UNPIN_ROW), found(classes))
    }

    @Test fun everyBuildReadsTheLimitInItsFourPlacesAndAsksBothRows() {
        for ((code, profile) in controlProfiles) {
            val hooks = profile.hooks.getValue(PINNED_CHATS)
            assertEquals(6, hooks.size, "$code")
            assertEquals(listOf(PIN_ROW, UNPIN_ROW), hooks.filter { it.startsWith(PIN_ROW) || it.startsWith(UNPIN_ROW) }.map { it.substringBefore("->") }.sorted(), "$code")
        }
    }

    // Each row is edited only where its own checks are done, so anything off that shape, or a Pin row with no inbox check
    // to keep it to the main chat list, leaves the whole switch out rather than half of it.
    @Test fun theRowsAreLeftAloneWhenTheyReadAnyOtherWay() {
        val cases = mapOf(
            "no Pin row" to pinRowFixture(pin = emptyList()),
            "no Unpin row" to pinRowFixture(unpin = emptyList()),
            "no inbox helper" to pinRowFixture(helper = emptyList()),
            "a helper that checks something else first" to pinRowFixture(helper = listOf(inboxHelper(INBOX_BODY.replace("if-ne v1, v0", "if-eq v1, v0")))),
            "the folder compared to another type" to pinRowFixture(helper = listOf(inboxHelper(INBOX_BODY.replace(INBOX, "LX/1B8;->A0K:LX/1B8;")))),
            "the surface check gone" to pinRowFixture(pin = listOf(pinRow(PIN_BODY.replace("if-eq p2, v0, :server", "if-eq v6, v0, :server")))),
            "the join's register read before it's written" to pinRowFixture(pin = listOf(pinRow(PIN_BODY.replace("invoke-static {v7}", "invoke-static {v0}")))),
            "no spare register after the join" to pinRowFixture(pin = listOf(pinRow(PIN_BODY.replace("const/16 v7, 0x189\n", "")
                .replace("invoke-static {v7}", "invoke-static {p0}")))),
            "a second Pin row of the same shape" to pinRowFixture(pin = listOf(pinRow(), pinRow(id = SYNTHETIC_PIN_ROW.replace("$MENU_SURFACE)Z", "LX/1o9;)Z")))),
        )
        for ((case, rows) in cases) {
            val classes = listOf(fixtureClass("LX/2na;", listOf(reader()))) + rows
            assertTrue(found(classes).isEmpty(), case)
            assertFailsWith<PatchException>(case) { validateControls(mirroredControls(classes), setOf(PINNED_CHATS)) }
        }
    }

    @Test fun pinAsksTheSwitchOnceTheChatsOwnChecksPassAndOnlyForTheInbox() {
        findPinRows(pinRowFixture())
        val method = pinRow()
        val before = method.code()
        val at = method.pinRowSite()!!
        injectControl(PINNED_CHATS, mapOf(PINNED_CHATS to listOf(method)))
        val code = method.code()
        assertEquals(before.take(at), code.take(at))
        assertEquals(before.drop(at), code.drop(at + 8))
        assertEquals(PIN_ROW_GATE, (code[at] as ReferenceInstruction).reference.toString())
        assertEquals(listOf(Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.IF_EQZ, Opcode.IGET_OBJECT, Opcode.SGET_OBJECT,
            Opcode.IF_NE, Opcode.CONST_4, Opcode.RETURN), code.subList(at, at + 8).map { it.opcode })
        assertEquals(FOLDER, (code[at + 3] as ReferenceInstruction).reference.toString())
        assertEquals(INBOX, (code[at + 4] as ReferenceInstruction).reference.toString())
        // The join's register carries the answer, the constant's register the inbox, and both skips land on Messenger's own next step.
        assertEquals(listOf(0, 0, 7), listOf(code[at + 1], code[at + 3], code[at + 4]).map { (it as OneRegisterInstruction).registerA })
        assertEquals(at + 8, code.branchTarget(at + 2))
        assertEquals(at + 8, code.branchTarget(at + 5))
    }

    @Test fun unpinAnswersYesForAnyPinnedChatWhileTheSwitchIsOn() {
        findPinRows(pinRowFixture())
        val method = unpinRow()
        val before = method.code()
        val at = method.pinRowSite()!!
        injectControl(PINNED_CHATS, mapOf(PINNED_CHATS to listOf(method)))
        val code = method.code()
        assertEquals(before.take(at), code.take(at))
        assertEquals(before.drop(at), code.drop(at + 4))
        assertEquals(PIN_ROW_GATE, (code[at] as ReferenceInstruction).reference.toString())
        assertEquals(listOf(Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.IF_EQZ, Opcode.RETURN), code.subList(at, at + 4).map { it.opcode })
        assertEquals(at + 4, code.branchTarget(at + 2))
    }

    @Test fun pinWithoutItsInboxCheckRefusesTheSwitchBeforeAnyEdit() {
        pinRowGuard = null
        val method = pinRow()
        val before = method.code()
        assertFailsWith<PatchException> { injectControl(PINNED_CHATS, mapOf(PINNED_CHATS to listOf(method))) }
        assertEquals(before, method.code())
    }

    // A read that only looks like the limit's would raise some other number, so anything off the exact shape is refused,
    // and one odd reader spoils them all rather than leave a check that still uses Messenger's own limit.
    @Test fun anythingButAStraightReadOfTheLimitIsLeftAlone() {
        val cases = mapOf(
            "another config id" to pinnedChatLimitFixture(reader(READ_BODY.replace("0x8205ad0002101eL", "0x8205b900021070L"))),
            "the id loaded twice" to pinnedChatLimitFixture(reader(READ_BODY.replace("const-wide v0, 0x8205ad0002101eL",
                "const-wide v0, 0x8205ad0002101eL\nconst-wide v0, 0x8205ad0002101eL"))),
            "something between the id and the read" to pinnedChatLimitFixture(reader(READ_BODY.replace("invoke-static {p0, v0, v1}",
                "nop\ninvoke-static {p0, v0, v1}"))),
            "a long read" to pinnedChatLimitFixture(reader(READ_BODY.replace(CONFIG_READ, "LX/17P;->A0G(Ljava/lang/Object;J)J")
                .replace("move-result v0", "move-result-wide v0\nlong-to-int v0, v0"))),
            "an answer nobody takes" to pinnedChatLimitFixture(reader(READ_BODY.replace("move-result v0\n", ""))),
            "one odd reader beside a good one" to pinnedChatLimitFixture(reader(),
                reader(READ_BODY.replace("move-result v0\n", ""), id = "LX/2nb;->A00($FB_USER_SESSION)I")),
        )
        for ((case, classes) in cases) {
            assertTrue(found(classes).isEmpty(), case)
            assertFailsWith<PatchException>(case) { validateControls(mirroredControls(classes), setOf(PINNED_CHATS)) }
        }
    }

    @Test fun theReadAsksTheSwitchRightAfterItsAnswerAndKeepsEverythingElse() {
        val method = reader()
        val before = method.code()
        val taken = before.indexOfFirst { it.opcode == Opcode.MOVE_RESULT }
        injectControl(PINNED_CHATS, mapOf(PINNED_CHATS to listOf(method)))
        val code = method.code()
        assertEquals(before.size + 2, code.size)
        assertEquals(before.take(taken + 1), code.take(taken + 1))
        assertEquals(before.drop(taken + 1).map { it.opcode }, code.drop(taken + 3).map { it.opcode })
        val ask = code[taken + 1] as RegisterRangeInstruction
        assertEquals(Opcode.INVOKE_STATIC_RANGE, ask.opcode)
        assertEquals(PINNED_CHAT_LIMIT_GATE, (ask as ReferenceInstruction).reference.toString())
        // The read's own answer goes in and the switch's answer comes back in the register the method compares.
        assertEquals(listOf(1, 0), listOf(ask.registerCount, ask.startRegister))
        assertEquals(Opcode.MOVE_RESULT, code[taken + 2].opcode)
        assertEquals(0, (code[taken + 2] as OneRegisterInstruction).registerA)
    }

    @Test fun anUnusableMethodRefusesTheSwitchBeforeAnyEdit() {
        val cases = mapOf(
            "another config id" to reader(READ_BODY.replace("0x8205ad0002101eL", "0x1L")),
            "no answer taken" to reader(READ_BODY.replace("move-result v0\n", "")),
            "another method" to fixtureMethod("LX/2na;->A01($FB_USER_SESSION)I", "const/4 v0, 0x3\nreturn v0", registers = 3, flags = STATIC),
        )
        for ((case, bad) in cases) {
            val badBefore = bad.code()
            assertFailsWith<PatchException>(case) { injectControl(PINNED_CHATS, mapOf(PINNED_CHATS to listOf(bad))) }
            assertEquals(badBefore, bad.code(), case)
        }
    }

    @Test fun everySupportedBuildHooksEachReadAndBothRows() {
        val root = System.getenv("HUSH_NATIVE_FIXTURES")
        assumeTrue(root != null, "Set HUSH_NATIVE_FIXTURES to the exact stock fixture directory")
        val apks = Files.list(Path.of(root!!)).use { it.filter { p -> p.toString().endsWith(".apk") }.sorted().toList() }
        assertEquals(controlProfiles.size, apks.size)
        for (apk in apks) {
            val code = apk.fileName.toString().substringBeforeLast(".apk").substringAfterLast('-')
            activeProfile = controlProfileFor(code)
            val dex = DexFileFactory.loadDexContainer(apk.toFile(), Opcodes.forApi(35))
            val hooks = findPinnedChats(dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes })
            assertEquals(activeProfile.hooks.getValue(PINNED_CHATS), hooks.map { it.hookId() }.toSet(), code)
            validateControls(mapOf(PINNED_CHATS to hooks), setOf(PINNED_CHATS))
            for (hook in hooks) {
                val method = MutableMethod(hook)
                val before = method.code()
                val (at, added) = if (hook.definingClass == PIN_ROW || hook.definingClass == UNPIN_ROW)
                    method.pinRowSite()!! to if (hook.definingClass == PIN_ROW) 8 else 4
                    else method.pinnedChatLimitRead()!! + 1 to 2
                method.injectPinnedChats()
                val after = method.code()
                val gate = if (added == 2) PINNED_CHAT_LIMIT_GATE else PIN_ROW_GATE
                assertEquals(gate, (after[at] as ReferenceInstruction).reference.toString(), code)
                assertEquals(before.drop(at).map { it.opcode }, after.drop(at + added).map { it.opcode }, code)
            }
        }
    }
}
