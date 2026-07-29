package com.tideo.autobrightness.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import javax.xml.parsers.DocumentBuilderFactory

/** Locks the privacy allowlists in data_extraction_rules.xml (DA-034). */
class DataExtractionRulesTest {
    @Test
    fun cloudAndTransfer_allowOnlyReviewedDataStores() {
        val xml = File("src/main/res/xml/data_extraction_rules.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)

        assertEquals(
            setOf("datastore/aab_settings.json", "datastore/aab_user_profiles.json"),
            includedPaths(document, "cloud-backup"),
        )
        assertEquals(
            setOf(
                "datastore/aab_settings.json",
                "datastore/aab_user_profiles.json",
                "datastore/aab_context_rules.json",
            ),
            includedPaths(document, "device-transfer"),
        )
    }

    private fun includedPaths(document: org.w3c.dom.Document, section: String): Set<String> {
        val root = document.getElementsByTagName(section).item(0) as org.w3c.dom.Element
        val includes = root.getElementsByTagName("include")
        return (0 until includes.length).map { index ->
            val element = includes.item(index) as org.w3c.dom.Element
            assertEquals("file", element.getAttribute("domain"))
            element.getAttribute("path")
        }.toSet()
    }
}
