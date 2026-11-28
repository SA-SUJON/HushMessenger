package app.hushmessenger.patches.controls

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val HANDLER = "LX/Cph;"
internal const val SYNTHETIC_CHAT_PREVIEW = "$HANDLER->A02()V"
private const val MENU = "$HANDLER->A01()V"
private const val BUILDER_CLASS = "LX/Cpb;"
private const val BUILDER = "$BUILDER_CLASS->A00()Z"
private const val GATE = "LX/Cpg;->A00()Z"
private const val PREVIEW_READ = "invoke-static {v4, v2, v3}, LX/18A;->A1Z(Ljava/lang/Object;J)Z"
private const val ENCRYPTED_READ = "invoke-static {v0, v4, v2, v3}, LX/18A;->A1X(LX/30w;Ljava/lang/Object;J)Z"
private const val WARM = "LX/Cpf;->A00()Z"
private const val COLD_FLAG = 0x2101053700084493L
private const val COLD_READ = "invoke-static {v4, v2, v3}, LX/18A;->A1Y(Ljava/lang/Object;J)Z"
private const val READY = "LX/Cpd;->A00()Z"
private const val ADOPT_READ = "invoke-static {v0, v4, v2, v3}, LX/18A;->A1W(LX/30w;Ljava/lang/Object;J)Z"

/**
 * The long-press handler as Messenger 587 and 588 write it, cut down: an encrypted chat asks the encrypted-chat flag
 * first, every chat asks the preview flag and branches on the answer a little later, a cold Messenger asks the
 * cold-start flag, an encrypted chat asks its flag again, and only then does it call the static method that builds
 * the preview. Any no, or a yes from the cold-start flag, falls back to the plain menu.
 */
private val HANDLER_BODY = """
    const/4 v4, 0x0
    invoke-virtual {p0}, $HANDLER->A00()Z
    move-result v1
    if-eqz v1, :preview_flag
    sget-object v0, LX/30w;->A0A:LX/30w;
    const-wide v2, ${CHAT_PREVIEW_ENCRYPTED_FLAG}L
    $ENCRYPTED_READ
    move-result v0
    if-eqz v0, :menu
    :preview_flag
    const-wide v2, ${CHAT_PREVIEW_FLAG}L
    $PREVIEW_READ
    move-result v6
    invoke-virtual {p0}, $HANDLER->A00()Z
    move-result v0
    if-eqz v6, :menu
    invoke-static {}, $WARM
    move-result v0
    if-nez v0, :warm
    const-wide v2, ${COLD_FLAG}L
    $COLD_READ
    move-result v0
    if-nez v0, :menu
    :warm
    if-eqz v1, :show
    sget-object v0, LX/30w;->A0A:LX/30w;
    const-wide v2, ${CHAT_PREVIEW_ENCRYPTED_FLAG}L
    $ENCRYPTED_READ
    move-result v0
    if-nez v0, :show
    goto :menu
    :show
    invoke-static {}, $BUILDER
    move-result v0
    return-void
    :menu
    invoke-virtual {p0}, $MENU
    return-void
""".trimIndent()

/**
 * 588's handler when the press made a preview ready before the hold timed out (#261). It keeps the preview flag in
 * its register pair, jumps back over a stretch of it before the cold-start read (588 loops there too), the cold-start
 * read takes another pair, and before it adopts the ready preview it asks the preview flag again from the pair it
 * kept. A no there gives the plain menu.
 */
private val ADOPTING_BODY = """
    const/4 v4, 0x0
    invoke-virtual {p0}, $HANDLER->A00()Z
    move-result v1
    if-eqz v1, :preview_flag
    sget-object v0, LX/30w;->A0A:LX/30w;
    const-wide v2, ${CHAT_PREVIEW_ENCRYPTED_FLAG}L
    $ENCRYPTED_READ
    move-result v0
    if-eqz v0, :menu
    :preview_flag
    const-wide v2, ${CHAT_PREVIEW_FLAG}L
    $PREVIEW_READ
    move-result v6
    if-eqz v6, :menu
    :row
    invoke-static {}, LX/Cpc;->A00()Z
    move-result v0
    if-eqz v0, :row
    invoke-static {}, $WARM
    move-result v0
    if-nez v0, :warm
    const-wide v5, ${COLD_FLAG}L
    invoke-static {v4, v5, v6}, LX/18A;->A1Y(Ljava/lang/Object;J)Z
    move-result v0
    if-nez v0, :menu
    :warm
    invoke-static {}, $READY
    move-result v0
    if-eqz v0, :show
    sget-object v0, LX/30w;->A0A:LX/30w;
    $ADOPT_READ
    move-result v0
    if-eqz v0, :menu
    :show
    invoke-static {}, $BUILDER
    move-result v0
    return-void
    :menu
    invoke-virtual {p0}, $MENU
    return-void
""".trimIndent()

/** The adopting handler with the first [from] swapped for [to]. */
private fun adopting(from: String, to: String) = handler(ADOPTING_BODY.replaceFirst(from, to).also { check(it != ADOPTING_BODY) })

/** 586's handler, which builds the preview itself behind the same reads. */
private val HANDLER_586_BODY = HANDLER_BODY.replace("invoke-static {}, $BUILDER\nmove-result v0",
    "new-instance v5, $CHAT_PREVIEW_FRAGMENT\ninvoke-direct {v5}, $CHAT_PREVIEW_FRAGMENT-><init>()V")

/** The chat list's other gate, which asks the preview flag through the config's interface right after a cast to it. */
private val GATE_BODY = """
    const/4 v4, 0x0
    sget-object v0, LX/30w;->A0A:LX/30w;
    const-wide v2, ${CHAT_PREVIEW_FLAG}L
    check-cast v4, Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;
    invoke-interface {v4, v0, v2, v3}, Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;->B3e(LX/30w;J)Z
    move-result v0
    return v0
""".trimIndent()

private val STATIC = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value

private fun handler(body: String = HANDLER_BODY) = fixtureMethod(SYNTHETIC_CHAT_PREVIEW, body, 8)

private fun gate(body: String = GATE_BODY) = fixtureMethod(GATE, body, 5, STATIC)

/** The handler with the first [from] swapped for [to]. */
private fun changed(from: String, to: String) = handler(HANDLER_BODY.replaceFirst(from, to).also { check(it != HANDLER_BODY) })

private val ENCRYPTED = "const-wide v2, ${CHAT_PREVIEW_ENCRYPTED_FLAG}L"
private val PREVIEW = "const-wide v2, ${CHAT_PREVIEW_FLAG}L"

private fun builderClass() = fixtureClass(BUILDER_CLASS, listOf(fixtureMethod(BUILDER,
    "new-instance v0, $CHAT_PREVIEW_FRAGMENT\ninvoke-direct {v0}, $CHAT_PREVIEW_FRAGMENT-><init>()V\nconst/4 v0, 0x1\nreturn v0", 1, STATIC)))

/** The chat list's click handler with its long-press method, the plain menu it falls back to and the preview's builder. */
internal fun chatPreviewFixture(method: MutableMethod = handler()): List<MutableClass> =
    listOf(fixtureClass(HANDLER, listOf(method, fixtureMethod(MENU, "return-void", 1))), builderClass())

private fun gateClass(method: MutableMethod = gate()) = fixtureClass("LX/Cpg;", listOf(method))

private fun Method.code() = implementation!!.instructions.toList()

private fun Instruction.helper() = (this as? ReferenceInstruction)?.reference?.toString()
    ?.takeIf { it == CHAT_PREVIEW_HELPER || it == CHAT_PREVIEW_COLD_HELPER }

private fun Method.helperCalls() = code().indices.filter { code()[it].helper() != null }

class ChatPreviewTest {
    @AfterTest fun reset() {
        activeProfile = SYNTHETIC_PROFILE
    }

    private fun found(classes: List<MutableClass>) = mirroredControls(classes).getValue(CHAT_PREVIEW).map { it.hookId() }.toSet()

    @Test fun theLongPressHandlerIsFoundWhenItCallsTheBuilderBehindBothFlags() {
        assertEquals(setOf(SYNTHETIC_CHAT_PREVIEW), found(chatPreviewFixture()))
        validateControls(mirroredControls(chatPreviewFixture()), setOf(CHAT_PREVIEW))
        assertEquals(listOf(CHAT_PREVIEW_ENCRYPTED_FLAG, CHAT_PREVIEW_FLAG, COLD_FLAG, CHAT_PREVIEW_ENCRYPTED_FLAG),
            handler().chatPreviewSites().map { it.flag })
    }

    // A method that only asks whether Messenger is cold has nothing to do with the switch.
    @Test fun aColdStartReadAloneMakesNoReader() {
        val cold = fixtureClass("LX/Cpe;", listOf(fixtureMethod("LX/Cpe;->A00()Z",
            "const/4 v4, 0x0\nconst-wide v2, ${COLD_FLAG}L\n$COLD_READ\nmove-result v0\nreturn v0", 5, STATIC)))
        assertEquals(setOf(SYNTHETIC_CHAT_PREVIEW), found(chatPreviewFixture() + cold))
    }

    @Test fun everyOtherReaderOfThePreviewFlagIsTakenWithIt() {
        assertEquals(setOf(SYNTHETIC_CHAT_PREVIEW, GATE), found(chatPreviewFixture() + gateClass()))
        activeProfile = syntheticProfileWith(CHAT_PREVIEW to setOf(SYNTHETIC_CHAT_PREVIEW, GATE))
        validateControls(mirroredControls(chatPreviewFixture() + gateClass()), setOf(CHAT_PREVIEW))
        assertEquals(listOf(CHAT_PREVIEW_FLAG), gate().chatPreviewSites().map { it.flag })
    }

    @Test fun a586HandlerThatBuildsThePreviewItselfIsStillFound() {
        val classes = listOf(fixtureClass(HANDLER, listOf(handler(HANDLER_586_BODY), fixtureMethod(MENU, "return-void", 1))))
        assertEquals(setOf(SYNTHETIC_CHAT_PREVIEW), found(classes))
    }

    @Test fun eachMappingHooksItsOwnHandler() {
        assertEquals(setOf(SYNTHETIC_CHAT_PREVIEW), SYNTHETIC_PROFILE.hooks.getValue(CHAT_PREVIEW))
        assertEquals(setOf("LX/3Aq;->A02(LX/1Dr;LX/3Aq;)Ljava/lang/Object;", "LX/3Au;->A00(ILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            "LX/3B1;->A07(ILjava/lang/Object;)Ljava/lang/Object;"), BASE_PROFILE.hooks.getValue(CHAT_PREVIEW))
    }

    // The switch says a long press opens the preview. Readers that ask other questions, or ask these some other way,
    // could turn on something that isn't the preview, so they get no hook at all.
    @Test fun anyOtherShapeLeavesTheControlUnapplied() {
        val second = fixtureClass("LX/Zzq;", listOf(fixtureMethod("LX/Zzq;->A00()V", "new-instance v0, $CHAT_PREVIEW_FRAGMENT\nreturn-void", 1)))
        val cases = mapOf(
            "no preview built" to chatPreviewFixture().filter { it.type != BUILDER_CLASS },
            "a second place that builds the preview" to chatPreviewFixture() + second,
            "a builder no reader calls" to chatPreviewFixture(changed("invoke-static {}, $BUILDER", "invoke-static {}, $BUILDER_CLASS->A01()Z")),
            "the preview flag read twice" to chatPreviewFixture(changed(ENCRYPTED, PREVIEW)),
            "no encrypted-chat read" to chatPreviewFixture(handler(HANDLER_BODY.replace(ENCRYPTED, "const-wide v2, 0x1L"))),
            "a read that isn't static" to chatPreviewFixture(changed(PREVIEW_READ, "invoke-virtual {v4, v2, v3}, LX/18A;->A1Z(J)Z")),
            "a read that doesn't take the flag last" to chatPreviewFixture(changed(PREVIEW_READ, "invoke-static {v2, v3, v4}, LX/18A;->A1Z(JLjava/lang/Object;)Z")),
            "a read that isn't a yes or no" to chatPreviewFixture(changed(PREVIEW_READ, "invoke-static {v4, v2, v3}, LX/18A;->A1Z(Ljava/lang/Object;J)I")),
            "an answer nothing takes" to chatPreviewFixture(changed("$PREVIEW_READ\nmove-result v6", "$PREVIEW_READ\nconst/4 v6, 0x1")),
            "a cold-start read that isn't static" to chatPreviewFixture(changed(COLD_READ, "invoke-virtual {v4, v2, v3}, LX/18A;->A1Y(J)Z")),
            "a cold-start answer nothing takes" to chatPreviewFixture(changed("$COLD_READ\nmove-result v0", "$COLD_READ\nconst/4 v0, 0x1")),
            "a jump into the read" to chatPreviewFixture(handler(HANDLER_BODY.replaceFirst("if-eqz v1, :preview_flag", "if-eqz v1, :preview_read")
                .replaceFirst("$PREVIEW\n$PREVIEW_READ", "$PREVIEW\n:preview_read\n$PREVIEW_READ"))),
            "a cast read asked on another register" to chatPreviewFixture() + gateClass(gate(GATE_BODY.replace("invoke-interface {v4,", "invoke-interface {v1,"))),
            "a cast to another type than the check's" to chatPreviewFixture() + gateClass(gate(GATE_BODY.replaceFirst(
                "check-cast v4, Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;", "check-cast v4, LX/4qO;"))),
            "an interface read with no cast" to chatPreviewFixture() + gateClass(gate(GATE_BODY.replace(
                "check-cast v4, Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;\n", ""))),
        )
        for ((case, classes) in cases) {
            assertTrue(found(classes).isEmpty(), case)
            assertFailsWith<PatchException>(case) { validateControls(mirroredControls(classes), setOf(CHAT_PREVIEW)) }
        }
    }

    @Test fun everyAnswerPassesThroughTheSwitchBeforeAnythingReadsIt() {
        val method = handler()
        val before = method.code()
        injectControl(CHAT_PREVIEW, mapOf(CHAT_PREVIEW to listOf(method)))
        val code = method.code()
        val calls = method.helperCalls()
        assertEquals(listOf(CHAT_PREVIEW_HELPER, CHAT_PREVIEW_HELPER, CHAT_PREVIEW_COLD_HELPER, CHAT_PREVIEW_HELPER), calls.map { code[it].helper() })
        for (at in calls) {
            // The flag's own answer goes in, and the switch's answer comes back to the same register.
            val answer = (code[at - 1] as OneRegisterInstruction).registerA
            assertEquals(Opcode.MOVE_RESULT, code[at - 1].opcode)
            assertEquals(Opcode.INVOKE_STATIC_RANGE, code[at].opcode)
            assertEquals(Opcode.MOVE_RESULT, code[at + 1].opcode)
            assertEquals(answer, (code[at + 1] as OneRegisterInstruction).registerA)
        }
        // The preview's answer is still what the delayed branch reads, and a no still lands on Messenger's plain menu.
        val branch = code.indexOfFirst { it.opcode == Opcode.IF_EQZ && (it as OneRegisterInstruction).registerA == 6 }
        assertTrue(branch > calls[1])
        assertEquals(MENU, (code[code.branchTarget(branch)] as ReferenceInstruction).reference.toString())
        // The cold-start answer the switch hands back is what sends a cold Messenger to the plain menu.
        val cold = code[calls[2] + 2]
        assertEquals(Opcode.IF_NEZ, cold.opcode)
        assertEquals((code[calls[2] + 1] as OneRegisterInstruction).registerA, (cold as OneRegisterInstruction).registerA)
        assertEquals(MENU, (code[code.branchTarget(calls[2] + 2)] as ReferenceInstruction).reference.toString())
        // Everything else runs as Messenger wrote it.
        assertEquals(before.map { it.opcode }, code.filterIndexed { i, _ -> calls.none { i == it || i == it + 1 } }.map { it.opcode })
    }

    @Test fun anUnusableMethodRefusesTheSwitchBeforeAnyEdit() {
        val cases = mapOf(
            "the plain menu" to fixtureMethod(MENU, "return-void", 1),
            "a handler with the preview flag read twice" to changed(ENCRYPTED, PREVIEW),
        )
        for ((case, bad) in cases) {
            val badBefore = bad.code()
            assertFailsWith<PatchException>(case) { injectControl(CHAT_PREVIEW, mapOf(CHAT_PREVIEW to listOf(bad))) }
            assertEquals(badBefore, bad.code(), case)
        }
    }

    // #261: a press the chat list made a preview ready for asked the preview flag a second time, from the pair it had
    // loaded for the first read, and that answer never went through the switch. With Meta's flag off, a group or plain
    // chat opened the plain menu whenever its preview was ready in time, and an encrypted one (never made ready) peeked.
    @Test fun aPreviewMadeReadyBeforeTheHoldAsksTheFlagAgainThroughTheSwitch() {
        val method = handler(ADOPTING_BODY)
        assertEquals(listOf(CHAT_PREVIEW_ENCRYPTED_FLAG, CHAT_PREVIEW_FLAG, COLD_FLAG, CHAT_PREVIEW_FLAG), method.chatPreviewSites().map { it.flag })
        assertEquals(setOf(SYNTHETIC_CHAT_PREVIEW), found(chatPreviewFixture(handler(ADOPTING_BODY))))
        validateControls(mirroredControls(chatPreviewFixture(handler(ADOPTING_BODY))), setOf(CHAT_PREVIEW))
        val before = method.code()
        injectControl(CHAT_PREVIEW, mapOf(CHAT_PREVIEW to listOf(method)))
        val code = method.code()
        val calls = method.helperCalls()
        assertEquals(listOf(CHAT_PREVIEW_HELPER, CHAT_PREVIEW_HELPER, CHAT_PREVIEW_COLD_HELPER, CHAT_PREVIEW_HELPER), calls.map { code[it].helper() })
        // The second answer comes from the read of the kept pair, goes back to the register the branch reads, and a no
        // still lands on the plain menu.
        val again = calls.last()
        assertEquals(ADOPT_READ.substringAfter("}, "), (code[again - 2] as ReferenceInstruction).reference.toString())
        assertEquals(Opcode.MOVE_RESULT, code[again + 1].opcode)
        val branch = code[again + 2]
        assertEquals(Opcode.IF_EQZ, branch.opcode)
        assertEquals((code[again + 1] as OneRegisterInstruction).registerA, (branch as OneRegisterInstruction).registerA)
        assertEquals(MENU, (code[code.branchTarget(again + 2)] as ReferenceInstruction).reference.toString())
        assertEquals(before.map { it.opcode }, code.filterIndexed { i, _ -> calls.none { i == it || i == it + 1 } }.map { it.opcode })
    }

    // A pair is only taken for the preview flag while every way to the read comes through the flag's load with nothing
    // writing the pair on the way. Otherwise the read is left as Messenger wrote it, and the rest still applies.
    @Test fun aPairThatMayHoldSomethingElseIsNotTakenForThePreviewFlag() {
        val adopt = "sget-object v0, LX/30w;->A0A:LX/30w;\n$ADOPT_READ"
        val cases = mapOf(
            "the pair loaded again" to adopting(adopt, "const-wide v2, 0x1L\n$adopt"),
            "a wide write over the pair's low half" to adopting(adopt, "const-wide v1, 0x1L\n$adopt"),
            "a write to the pair's high half" to adopting(adopt, "const/4 v3, 0x0\n$adopt"),
            "a way in that skips the load" to adopting("$ENCRYPTED_READ\nmove-result v0\nif-eqz v0, :menu",
                "$ENCRYPTED_READ\nmove-result v0\nif-eqz v0, :warm"),
        )
        for ((case, method) in cases) {
            assertEquals(listOf(CHAT_PREVIEW_ENCRYPTED_FLAG, CHAT_PREVIEW_FLAG, COLD_FLAG), method.chatPreviewSites().map { it.flag }, case)
            injectControl(CHAT_PREVIEW, mapOf(CHAT_PREVIEW to listOf(method)))
            assertEquals(3, method.helperCalls().size, case)
        }
    }

    @Test fun aSecondReadOfTheKeptPairInAnotherShapeRefusesTheSwitch() {
        val cases = mapOf(
            "a second read that isn't static" to adopting(ADOPT_READ, "invoke-virtual {v0, v2, v3}, LX/18A;->A1W(J)Z"),
            "a second read over a register range" to adopting(ADOPT_READ, "invoke-static/range {v0 .. v3}, LX/18A;->A1W(LX/30w;Ljava/lang/Object;J)Z"),
            "a second answer nothing takes" to adopting("$ADOPT_READ\nmove-result v0", "$ADOPT_READ\nconst/4 v0, 0x1"),
        )
        for ((case, method) in cases) {
            assertTrue(found(chatPreviewFixture(method)).isEmpty(), case)
            val before = method.code()
            assertFailsWith<PatchException>(case) { injectControl(CHAT_PREVIEW, mapOf(CHAT_PREVIEW to listOf(method))) }
            assertEquals(before, method.code(), case)
        }
    }

    // #231: a cold Messenger also asked two flags that gave the first long press after a restart the plain menu, and
    // turning the switch on means a restart.
    @Test fun everySupportedBuildPassesBothColdStartReadsThroughTheSwitch() {
        val root = System.getenv("HUSH_NATIVE_FIXTURES")
        assumeTrue(root != null, "Set HUSH_NATIVE_FIXTURES to the exact stock fixture directory")
        val apks = Files.list(Path.of(root!!)).use { it.filter { p -> p.toString().endsWith(".apk") }.sorted().toList() }
        val coldFlags = setOf(0x2101053700044490L, 0x2101053700084493L)
        for (apk in apks) {
            val code = apk.fileName.toString().substringBeforeLast(".apk").substringAfterLast('-')
            activeProfile = controlProfileFor(code)
            val dex = DexFileFactory.loadDexContainer(apk.toFile(), Opcodes.forApi(35))
            val seen = mutableSetOf<Long>()
            for (hook in findChatPreview(dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes })) {
                val method = MutableMethod(hook)
                method.injectChatPreview()
                val after = method.code()
                for (at in after.indices) {
                    val flag = (after[at] as? WideLiteralInstruction)?.wideLiteral
                    if (after[at].opcode != Opcode.CONST_WIDE || flag !in coldFlags) continue
                    // The flag's answer goes through the switch, and the switch's answer back into the same register.
                    val answer = (at until after.size).first { after[it].opcode == Opcode.MOVE_RESULT }
                    assertEquals("$SETTINGS->chatPreviewCold(I)Z", (after[answer + 1] as? ReferenceInstruction)?.reference?.toString(), code)
                    assertEquals((after[answer] as OneRegisterInstruction).registerA, (after[answer + 2] as OneRegisterInstruction).registerA, code)
                    seen += flag!!
                }
            }
            assertEquals(coldFlags, seen, code)
        }
    }

    @Test fun everySupportedBuildPassesEveryPreviewReadThroughTheSwitch() {
        val root = System.getenv("HUSH_NATIVE_FIXTURES")
        assumeTrue(root != null, "Set HUSH_NATIVE_FIXTURES to the exact stock fixture directory")
        val apks = Files.list(Path.of(root!!)).use { it.filter { p -> p.toString().endsWith(".apk") }.sorted().toList() }
        assertEquals(controlProfiles.size, apks.size)
        for (apk in apks) {
            val code = apk.fileName.toString().substringBeforeLast(".apk").substringAfterLast('-')
            activeProfile = controlProfileFor(code)
            val dex = DexFileFactory.loadDexContainer(apk.toFile(), Opcodes.forApi(35))
            val hooks = findChatPreview(dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes })
            assertEquals(activeProfile.hooks.getValue(CHAT_PREVIEW), hooks.map { it.hookId() }.toSet(), code)
            validateControls(mapOf(CHAT_PREVIEW to hooks), setOf(CHAT_PREVIEW))
            // The long-press handler asks the encrypted-chat flag around its preview read, the cold-start flag between
            // them, and the preview flag a second time from the same pair when it adopts a preview made ready (#261).
            assertTrue(hooks.any { MutableMethod(it).chatPreviewSites().map { s -> s.flag } ==
                listOf(CHAT_PREVIEW_ENCRYPTED_FLAG, CHAT_PREVIEW_FLAG, COLD_FLAG, CHAT_PREVIEW_FLAG, CHAT_PREVIEW_ENCRYPTED_FLAG) }, code)
            // Both cold-start flags are asked somewhere the switch reaches, so a long press right after launch opens
            // the preview too (#231).
            val flags = hooks.flatMap { MutableMethod(it).chatPreviewSites().map { s -> s.flag } }.toSet()
            assertTrue(flags.containsAll(CHAT_PREVIEW_COLD_FLAGS), code)
            for (hook in hooks) {
                val method = MutableMethod(hook)
                val helpers = method.chatPreviewSites().map { it.helper }
                val before = method.code()
                method.injectChatPreview()
                val calls = method.helperCalls()
                val after = method.code()
                assertEquals(helpers, calls.map { after[it].helper() }, code)
                assertTrue(calls.all { after[it - 1].opcode == Opcode.MOVE_RESULT && after[it + 1].opcode == Opcode.MOVE_RESULT }, code)
                assertEquals(before.map { it.opcode }.filter { it != Opcode.NOP },
                    after.filterIndexed { i, _ -> calls.none { i == it || i == it + 1 } }.map { it.opcode }.filter { it != Opcode.NOP }, code)
            }
        }
    }
}
