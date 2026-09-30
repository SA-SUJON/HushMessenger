/*
 * Material You theme patch: hooks Messenger's dark mode colour resolution to tint it
 * with the wallpaper palette on Android 12+.
 *
 * Route 1 (this commit): hook the Mig dark scheme resolver's return so every dark token
 * colour passes through MaterialYouTheme.mig(), which recolours greys to the palette's
 * neutral and blues to its accent at the same CIE lightness.
 *
 * The dark mode detection hook sends Messenger's own answer through
 * MaterialYouTheme.darkModeAnswer() so the runtime knows when to act.
 */
package app.hushmessenger.patches.controls

import app.hushmessenger.patches.MessengerTarget
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val THEME = "Lapp/hushmessenger/extension/MaterialYouTheme;"
private const val DARK_SCHEME = "Lcom/facebook/mig/scheme/schemes/DarkColorScheme;"
private const val FDS_COLORS = "Lcom/facebook/fds/core/theme/component/FDSColors;"

/** The Mig colour token interface — every token enum implements this. */
private const val TOKEN_IFACE = "LX/4r6;"

private var materialYouApplied = false

private val materialYouResources = resourcePatch(description = "Record HushMessenger capability: material_you") {
    dependsOn(settingsResources)
    execute {
        materialYouApplied = false
        document("AndroidManifest.xml").use { it.requireFeatureAbsent("material_you") }
    }
    finalize {
        if (materialYouApplied) document("AndroidManifest.xml").use { it.addFeature("material_you") }
    }
}

@Suppress("unused")
val materialYouPatch = bytecodePatch(
    name = "Material You theme",
    description = "Gives Messenger's dark mode the colours of your wallpaper on Android 12 and newer, and a fixed blue palette on Android 11. Light mode stays as it is. Turn on dark mode in Messenger first.",
    default = false,
) {
    category("Theme")
    compatibleWith(MessengerTarget.COMPATIBILITY)
    dependsOn(settingsExtension, materialYouResources)
    execute {
        // --- Find the DarkColorScheme.DCz method ---
        // DCz(LX/4r6;)I invokes the token's ApL()I and returns the colour.
        // We hook before each RETURN to recolour through MaterialYouTheme.mig(int).
        val darkScheme = mutableClassDefBy(DARK_SCHEME)
        val dcz = darkScheme.methods.singleOrNull { m ->
            m.returnType == "I" && m.parameterTypes.size == 1 &&
                m.parameterTypes[0] == TOKEN_IFACE &&
                m.implementation != null
        } ?: throw app.morphe.patcher.patch.PatchException(
            "DarkColorScheme.DCz(LX/4r6;)I not found"
        )

        // Validate: DCz should invoke ApL()I on the token and return an int
        val dczCode = dcz.implementation!!.instructions.toList()
        val hasApL = dczCode.any { i ->
            i.opcode == Opcode.INVOKE_INTERFACE &&
                ((i as? ReferenceInstruction)?.reference as? MethodReference)?.name == "ApL"
        }
        check(hasApL) { "DCz does not call ApL on the token" }

        // Find the RETURN instruction and hook before it
        val returnIndex = dczCode.indexOfLast { it.opcode == Opcode.RETURN }
        check(returnIndex >= 0) { "no RETURN in DCz" }

        // The return register holds the resolved colour
        val returnReg = (dczCode[returnIndex] as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA

        // Insert: invoke-static {vReturn}, MaterialYouTheme.mig(I)I; move-result vReturn
        dcz.addInstructions(
            returnIndex,
            """
                invoke-static {v$returnReg}, $THEME->mig(I)I
                move-result v$returnReg
            """
        )

        // --- Find the FDS dark mode check and hook it ---
        // FDSColors.A00 calls LX/2AN.A02(Context)Z (the isDarkMode check).
        // LX/2AN.A02 calls LX/2AO.A07(Context)Z and returns its result.
        // We hook the return of A02 to also feed through darkModeAnswer.
        val fdsColors = mutableClassDefBy(FDS_COLORS)
        val fdsMethod = fdsColors.methods.firstOrNull { m ->
            m.returnType == "I" && m.parameterTypes.size == 3 &&
                m.parameterTypes[0] == "Landroid/content/Context;" &&
                m.implementation != null
        }

        if (fdsMethod != null) {
            // Find the dark mode check class from FDSColors: it's called as a static method returning boolean
            val darkCheckRef = fdsMethod.implementation!!.instructions.toList()
                .filterIsInstance<ReferenceInstruction>()
                .mapNotNull { it.reference as? MethodReference }
                .firstOrNull { it.returnType == "Z" && it.parameterTypes.size == 1 && it.parameterTypes[0] == "Landroid/content/Context;" }

            if (darkCheckRef != null) {
                val darkCheckClass = mutableClassDefBy(darkCheckRef.definingClass)
                val darkCheckMethod = darkCheckClass.methods.singleOrNull { m ->
                    m.name == darkCheckRef.name && m.returnType == "Z" &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == "Landroid/content/Context;" &&
                        m.implementation != null
                }

                if (darkCheckMethod != null) {
                    val darkCode = darkCheckMethod.implementation!!.instructions.toList()
                    val darkReturn = darkCode.indexOfLast { it.opcode == Opcode.RETURN }
                    if (darkReturn >= 0) {
                        val darkReg = (darkCode[darkReturn] as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
                        darkCheckMethod.addInstructions(
                            darkReturn,
                            """
                                invoke-static {v$darkReg}, $THEME->darkModeAnswer(Z)Z
                                move-result v$darkReg
                            """
                        )
                    }
                }
            }

            // --- Hook FDS colour returns ---
            // FDSColors has two return paths: one from the resolver (intValue), one from the fallback.
            // Hook both returns of each method that returns int.
            // Insert in reverse order so indices stay valid.
            for (m in fdsColors.methods) {
                if (m.returnType != "I" || m.implementation == null) continue
                val code = m.implementation!!.instructions.toList()
                val returns = code.indices.filter { code[it].opcode == Opcode.RETURN }
                for (index in returns.reversed()) {
                    val reg = (code[index] as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
                    m.addInstructions(
                        index,
                        """
                            invoke-static {v$reg}, $THEME->fds(I)I
                            move-result v$reg
                        """
                    )
                }
            }
        }

        recordControl("material_you")
        materialYouApplied = true
    }
}
