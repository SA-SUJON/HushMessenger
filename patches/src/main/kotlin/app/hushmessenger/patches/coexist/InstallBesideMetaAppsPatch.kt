/*
 * Adapted from Hushfacebook's SharedPermissions.kt at
 * https://github.com/SysAdminDoc/Hushfacebook/blob/15b8e9ed9315464a3e2d1a821b4e26ad47bbc28c/patches/src/main/kotlin/app/morphe/patches/facebook/coexist/SharedPermissions.kt
 * Copyright 2026 Hushfacebook contributors. GPL-3.0.
 */
package app.hushmessenger.patches.coexist

import app.hushmessenger.patches.MessengerTarget
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val PATCH_NAME = "Install beside Meta apps"
private const val META_PREFIX = "com.facebook."
private const val SHARED_PREFIX = "app.hushfacebook."
private const val APP_COMMUNICATION = "com.facebook.permission.prod.FB_APP_COMMUNICATION"
private const val RECEIVER_ACCESS = "com.facebook.receiver.permission.ACCESS"
private const val APP_COMMUNICATION_FORMAT = "com.facebook.permission.%s.FB_APP_COMMUNICATION"
private const val EXPECTED_DEX_SITES = 6

private val sharedNames = listOf(APP_COMMUNICATION, RECEIVER_ACCESS)
private val dexNames = sharedNames + APP_COMMUNICATION_FORMAT
private val expectedManifestMentions = mapOf(APP_COMMUNICATION to 26, RECEIVER_ACCESS to 3)
private val expectedManifestRoles = mapOf(
    APP_COMMUNICATION to mapOf(
        "permission:name" to 1,
        "uses-permission:name" to 1,
        "activity:permission" to 7,
        "provider:permission" to 1,
        "receiver:permission" to 13,
        "service:permission" to 3,
    ),
    RECEIVER_ACCESS to mapOf(
        "permission:name" to 1,
        "uses-permission:name" to 1,
        "receiver:permission" to 1,
    ),
)

private fun renamed(name: String): String =
    SHARED_PREFIX + name.removePrefix(META_PREFIX)

/**
 * Rename declarations, requests and guarded components together. Removing a declaration would
 * let Messenger install but leave its signature-protected receivers and services unprotected.
 */
internal fun Document.renameSharedPermissions() {
    val mentions = sharedNames.associateWith { mutableListOf<org.w3c.dom.Attr>() }
    val declarations = sharedNames.associateWith { 0 }.toMutableMap()
    val roles = sharedNames.associateWith { mutableMapOf<String, Int>() }
    val renamedNames = sharedNames.map(::renamed).toSet()
    val elements = getElementsByTagName("*")
    for (i in 0 until elements.length) {
        val element = elements.item(i) as? Element ?: continue
        val attributes = element.attributes
        for (j in 0 until attributes.length) {
            val attr = attributes.item(j) as? org.w3c.dom.Attr ?: continue
            if (attr.value in renamedNames) {
                throw PatchException("$PATCH_NAME: ${attr.value} is already in the manifest")
            }
            if (attr.value !in sharedNames) continue
            mentions.getValue(attr.value).add(attr)
            val role = "${element.tagName}:${attr.nodeName.substringAfter(':')}"
            roles.getValue(attr.value).merge(role, 1, Int::plus)
            if (element.tagName == "permission" && attr.nodeName.substringAfter(':') == "name") {
                declarations[attr.value] = declarations.getValue(attr.value) + 1
            }
        }
    }
    for (name in sharedNames) {
        if (declarations.getValue(name) != 1) {
            throw PatchException("$PATCH_NAME: the manifest must declare $name exactly once")
        }
        val actual = mentions.getValue(name).size
        val expected = expectedManifestMentions.getValue(name)
        if (actual != expected) {
            throw PatchException("$PATCH_NAME: expected $expected manifest uses of $name, found $actual")
        }
        if (roles.getValue(name) != expectedManifestRoles.getValue(name)) {
            throw PatchException("$PATCH_NAME: manifest roles for $name differ from the supported APK")
        }
    }
    mentions.values.flatten().forEach { it.value = renamed(it.value) }
}

private val renameManifest = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.renameSharedPermissions() }
    }
}

private fun Instruction.sharedName(): String? {
    if (opcode != Opcode.CONST_STRING && opcode != Opcode.CONST_STRING_JUMBO) return null
    val literal = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
    return literal?.takeIf { it in dexNames }
}

private fun Method.hasSharedName(): Boolean =
    implementation?.instructions?.any { it.sharedName() != null } == true

private fun MutableMethod.renameSharedNames(): Int {
    val sites = (implementation ?: return 0).instructions.withIndex()
        .mapNotNull { (index, instruction) -> instruction.sharedName()?.let { index to it } }
    for ((index, oldName) in sites.asReversed()) {
        val register = getInstruction<OneRegisterInstruction>(index).registerA
        if (register > 255) {
            throw PatchException("$PATCH_NAME: $definingClass->$name uses v$register for a permission")
        }
        replaceInstruction(index, "const-string/jumbo v$register, \"${renamed(oldName)}\"")
    }
    return sites.size
}

private fun BytecodePatchContext.renameDexNames(): Int {
    val classes = dexNames.flatMap { classDefByStrings(it, StringComparisonType.EQUALS) }
        .map { it.type }.distinct()
    val methods = classes.flatMap { type ->
        mutableClassDefBy(type).methods.filter { it.hasSharedName() }
    }
    val sites = methods.sumOf { method ->
        method.implementation?.instructions?.count { it.sharedName() != null } ?: 0
    }
    if (sites != EXPECTED_DEX_SITES) {
        throw PatchException("$PATCH_NAME: expected $EXPECTED_DEX_SITES permission loads, found $sites")
    }
    val renamed = methods.sumOf { it.renameSharedNames() }
    if (renamed != EXPECTED_DEX_SITES || methods.any { it.hasSharedName() }) {
        throw PatchException("$PATCH_NAME: not all permission loads were renamed")
    }
    return renamed
}

/**
 * A re-signed Messenger and stock Meta apps otherwise declare two signature permissions under
 * the same names. The shared prefix is deliberately the one used by Hushfacebook. Pairing the
 * two patched apps still requires one signing key and separate cross-app trust checks.
 */
@Suppress("unused")
val installBesideMetaAppsPatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Renames Messenger's two shared signature permissions so it can install beside Meta apps.",
    default = true,
) {
    category("Fixes")
    compatibleWith(MessengerTarget.COMPATIBILITY)
    dependsOn(renameManifest)
    execute {
        renameDexNames()
    }
}
