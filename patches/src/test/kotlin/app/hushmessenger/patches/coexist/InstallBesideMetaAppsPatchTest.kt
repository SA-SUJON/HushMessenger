package app.hushmessenger.patches.coexist

import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Document
import org.w3c.dom.Element
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

    private fun manifest(): Document {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("manifest")
        document.appendChild(root)
        fun add(tag: String, attribute: String, value: String) {
            root.appendChild(document.createElement(tag).apply { setAttribute("android:$attribute", value) })
        }
        add("permission", "name", appCommunication)
        add("uses-permission", "name", appCommunication)
        repeat(7) { add("activity", "permission", appCommunication) }
        add("provider", "permission", appCommunication)
        repeat(13) { add("receiver", "permission", appCommunication) }
        repeat(3) { add("service", "permission", appCommunication) }
        add("permission", "name", receiverAccess)
        add("uses-permission", "name", receiverAccess)
        add("receiver", "permission", receiverAccess)
        return document
    }

    private fun values(document: Document): List<String> {
        val elements = document.getElementsByTagName("*")
        return (0 until elements.length).flatMap { index ->
            val attributes = elements.item(index).attributes
            (0 until attributes.length).map { attributes.item(it).nodeValue }
        }
    }

    @Test
    fun renamesEveryPermissionSiteInTheSupportedManifest() {
        val document = manifest()

        document.renameSharedPermissions()

        val names = values(document)
        assertEquals(26, names.count { it == renamedCommunication })
        assertEquals(3, names.count { it == renamedReceiver })
        assertEquals(0, names.count { it == appCommunication || it == receiverAccess })
    }

    @Test
    fun rejectsAChangedManifestBeforeRenamingAnySite() {
        val document = manifest()
        (document.getElementsByTagName("activity").item(0) as Element).removeAttribute("android:permission")

        val failure = assertFailsWith<PatchException> { document.renameSharedPermissions() }

        assertContains(failure.message.orEmpty(), "expected 26 manifest uses")
        assertEquals(25, values(document).count { it == appCommunication })
        assertEquals(0, values(document).count { it == renamedCommunication })
    }

    @Test
    fun rejectsAReassignedGuardEvenWhenTheTotalIsUnchanged() {
        val document = manifest()
        (document.getElementsByTagName("activity").item(0) as Element).removeAttribute("android:permission")
        document.documentElement.appendChild(document.createElement("meta-data").apply {
            setAttribute("android:value", appCommunication)
        })

        val failure = assertFailsWith<PatchException> { document.renameSharedPermissions() }

        assertContains(failure.message.orEmpty(), "manifest roles")
        assertEquals(26, values(document).count { it == appCommunication })
        assertEquals(0, values(document).count { it == renamedCommunication })
    }

    @Test
    fun rejectsASecondPermissionDeclaration() {
        val document = manifest()
        val duplicate = document.createElement("permission")
        duplicate.setAttribute("android:name", appCommunication)
        document.documentElement.appendChild(duplicate)

        val failure = assertFailsWith<PatchException> { document.renameSharedPermissions() }

        assertContains(failure.message.orEmpty(), "must declare $appCommunication exactly once")
        assertEquals(0, values(document).count { it == renamedCommunication })
    }
}
