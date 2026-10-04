package com.tideo.autobrightness.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TranslationCoverageBadgeTest {

    private val resRoot = File("src/main/res")
    private val readme = File("../README.md")
    private val badge = Regex(
        """!\[[^\]]*\(([A-Za-z-]+)\): (\d+)% translated]\(https://img\.shields\.io/badge/[^)\s]*-(\d+)%25%20translated-([a-z]+)\)""",
    )
    private val resourceTags = setOf("string", "plurals", "string-array")

    private fun keys(file: File, translatableOnly: Boolean): Set<String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until nodes.length).map { nodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName in resourceTags }
            .filterNot { translatableOnly && it.getAttribute("translatable") == "false" }
            .map { it.getAttribute("name") }
            .toSet()
    }

    private fun languageTag(qualifier: String): String =
        if (qualifier.startsWith("b+")) qualifier.removePrefix("b+").replace('+', '-') else qualifier.replace("-r", "-")

    private fun color(percent: Int): String = when {
        percent == 100 -> "brightgreen"
        percent >= 90 -> "green"
        percent >= 75 -> "yellowgreen"
        percent >= 50 -> "yellow"
        else -> "orange"
    }

    @Test
    fun readmeBadgeShowsEachTranslationsCoverage() {
        val source = keys(File(resRoot, "values/strings.xml"), translatableOnly = true)
        val badges = badge.findAll(readme.readText()).associateBy { it.groupValues[1] }
        val locales = resRoot.listFiles { dir -> dir.name.startsWith("values-") && File(dir, "strings.xml").isFile }.orEmpty()
        assertTrue("expected a translated locale under ${resRoot.absolutePath}", locales.isNotEmpty())

        locales.forEach { dir ->
            val tag = languageTag(dir.name.removePrefix("values-"))
            val translated = (keys(File(dir, "strings.xml"), translatableOnly = false) intersect source).size
            val percent = translated * 100 / source.size
            val found = badges[tag]?.groupValues
            assertTrue(
                "DD-027: README's Translations badge for $tag must read $percent% ($translated of ${source.size} " +
                    "strings), colour ${color(percent)}. Keep its label and set the numbers, or add one:\n" +
                    "[![<Language> ($tag): $percent% translated](https://img.shields.io/badge/<Language>-" +
                    "$percent%25%20translated-${color(percent)})](CONTRIBUTING.md#translations-are-welcome-here)",
                found != null && found[2].toInt() == percent && found[3].toInt() == percent &&
                    found[4] == color(percent),
            )
        }
    }
}
