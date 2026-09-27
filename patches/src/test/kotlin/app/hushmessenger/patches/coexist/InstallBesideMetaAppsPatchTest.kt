package app.hushmessenger.patches.coexist

import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstallBesideMetaAppsPatchTest {
    private val appCommunication = "com.facebook.permission.prod.FB_APP_COMMUNICATION"
    private val receiverAccess = "com.facebook.receiver.permission.ACCESS"
    private val renamedCommunication = "app.hushfacebook.permission.prod.FB_APP_COMMUNICATION"
    private val renamedReceiver = "app.hushfacebook.receiver.permission.ACCESS"

    private fun manifest(communicationMentions: Int, receiverMentions: Int): Document {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("manifest")
        document.appendChild(root)
        fun add(tag: String, name: String) {
            root.appendChild(document.createElement(tag).apply { setAttribute("android:name", name) })
        }
        add("permission", appCommunication)
        repeat(communicationMentions - 1) { add("uses-permission", appCommunication) }
        add("permission", receiverAccess)
        repeat(receiverMentions - 1) { add("uses-permission", receiverAccess) }
        return document
    }

    private fun names(document: Document): List<String> {
        val elements = document.getElementsByTagName("*")
        return (0 until elements.length).mapNotNull { index ->
            elements.item(index).attributes?.getNamedItem("android:name")?.nodeValue
        }
    }

    @Test
    fun renamesEveryPermissionSiteInTheSupportedManifest() {
        val document = manifest(26, 3)

        document.renameSharedPermissions()

        val names = names(document)
        assertEquals(26, names.count { it == renamedCommunication })
        assertEquals(3, names.count { it == renamedReceiver })
        assertEquals(0, names.count { it == appCommunication || it == receiverAccess })
    }

    @Test
    fun rejectsAChangedManifestBeforeRenamingAnySite() {
        val document = manifest(25, 3)

        val failure = assertFailsWith<PatchException> { document.renameSharedPermissions() }

        assertContains(failure.message.orEmpty(), "expected 26 manifest uses")
        assertEquals(25, names(document).count { it == appCommunication })
        assertEquals(0, names(document).count { it == renamedCommunication })
    }

    @Test
    fun rejectsASecondPermissionDeclaration() {
        val document = manifest(26, 3)
        val duplicate = document.createElement("permission")
        duplicate.setAttribute("android:name", appCommunication)
        document.documentElement.appendChild(duplicate)

        val failure = assertFailsWith<PatchException> { document.renameSharedPermissions() }

        assertContains(failure.message.orEmpty(), "must declare $appCommunication exactly once")
        assertEquals(0, names(document).count { it == renamedCommunication })
    }
}
