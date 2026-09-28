package app.hushmessenger.patches.controls

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue

internal fun fixtureMethod(
    id: String,
    body: String,
    registers: Int = 8,
    flags: Int = AccessFlags.PUBLIC.value,
): MutableMethod {
    val owner = id.substringBefore("->")
    val name = id.substringAfter("->").substringBefore('(')
    val parameters = Regex("\\[*(?:L[^;]+;|[ZBSCIJFD])").findAll(id.substringAfter('(').substringBefore(')'))
        .map { ImmutableMethodParameter(it.value, null, null) }.toList()
    return MutableMethod(ImmutableMethod(owner, name, parameters, id.substringAfter(')'), flags,
        null, null, ImmutableMethodImplementation(registers, emptyList(), null, null)))
        .apply { addInstructionsWithLabels(0, body) }
}

internal fun fixtureClass(type: String, methods: List<Method> = emptyList(), originalName: String? = null): MutableClass {
    val fields = originalName?.let {
        listOf(ImmutableField(type, "__redex_internal_original_name", "Ljava/lang/String;",
            AccessFlags.STATIC.value, ImmutableStringEncodedValue(it), null, null))
    }.orEmpty()
    return MutableClass(ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
        emptyList(), null, emptySet(), fields, methods))
}

internal const val PEOPLE_JEWEL_HOOK = "LX/HAR;->A01(LX/HAR;)Z"

/** The first 13 instructions match both supported APKs; the tail stands in for the list reset. */
internal fun peopleJewelMethod(
    key: String = "LX/JTx;->A01:LX/1BL;",
    resultRegister: String = "v0",
    flags: Int = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
) = fixtureMethod(PEOPLE_JEWEL_HOOK, """
    iget-object v0, p0, LX/HAR;->A07:LX/17Z;
    invoke-static {v0}, LX/17Z;->A0F(LX/17Z;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, LX/JTx;
    iget-object v2, p0, LX/HAR;->A01:Lcom/facebook/auth/usersession/FbUserSession;
    iget-object v0, v0, LX/JTx;->A00:LX/17Z;
    invoke-static {v0}, LX/17Z;->A0C(LX/17Z;)Lcom/facebook/prefs/shared/FbSharedPreferences;
    move-result-object v1
    sget-object v0, $key
    const/4 v4, 0x0
    invoke-interface {v1, v0, v4}, $PREFERENCE_GETTER
    move-result $resultRegister
    if-eqz v0, :shown
    const/4 v0, 0x1
    return v0
    :shown
    return v4
""".trimIndent(), registers = 6, flags = flags)

internal fun peopleJewelKeyHolder() = fixtureClass("LX/JTx;", listOf(fixtureMethod("LX/JTx;-><clinit>()V", """
    const-string v0, "pymk_jewel_section_hidden"
    sput-object v0, LX/JTx;->A01:LX/1BL;
    return-void
""".trimIndent(), flags = AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value)))

internal fun pluginBody(anchor: String, branch: String = "if-eq") = """
    iget-object v0, p0, Lfixture/Gate;->cache:Ljava/lang/Object;
    const/4 v6, 0x1
    const/4 v5, 0x0
    const-string v2, "$anchor"
    iget-object v1, p0, Lfixture/Gate;->cache:Ljava/lang/Object;
    sget-object v0, LX/1dj;->A03:Ljava/lang/Object;
    $branch v1, v0, :disabled
    return v6
    :disabled
    return v5
""".trimIndent()
