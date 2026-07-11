package org.odk.collect.android.feature.parity

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import java.io.File

/**
 * Assertion helpers for the Collect → KotlinCollect data-collection parity suite.
 *
 * Mirrors the kotlinrosa reference test (org.javarosa.differential.OdkReferenceFormDifferentialTest):
 * read the produced submission XML and assert leaf values exactly (deterministic fields), by shape
 * (non-deterministic fields), or that a node was pruned/empty (relevance branches).
 *
 * ODK Collect is the oracle: every exact value here is locked against Collect's real device output
 * before this suite is ported to KotlinCollect.
 */
object ParitySubmission {

    /** The produced submission for the n-th (0-based) sent instance. */
    fun submissionXml(submissions: List<File>, index: Int = 0): String =
        submissions[index].readText()

    /** Value of the first leaf element <tag>…</tag>; "" for a self-closing <tag/>; null if absent. */
    fun valueOf(xml: String, tag: String): String? {
        val full = Regex("<${Regex.escape(tag)}>([^<]*)</${Regex.escape(tag)}>").find(xml)
        if (full != null) return full.groupValues[1]
        if (Regex("<${Regex.escape(tag)}\\s*/>").containsMatchIn(xml)) return ""
        return null
    }

    /** Number of <tag> occurrences — used to count repeat instances by a child element name. */
    fun occurrences(xml: String, tag: String): Int =
        Regex("<${Regex.escape(tag)}[\\s>/]").findAll(xml).count()

    fun assertExact(xml: String, tag: String, expected: String) {
        assertThat("saved value of <$tag>", valueOf(xml, tag), equalTo(expected))
    }

    /** A relevance-pruned or never-answered node: either absent from the instance or empty. */
    fun assertAbsentOrEmpty(xml: String, tag: String) {
        val v = valueOf(xml, tag)
        assertThat("<$tag> should be pruned or empty but was '$v'", v == null || v == "", equalTo(true))
    }

    fun assertShape(xml: String, tag: String, pattern: String) {
        val v = valueOf(xml, tag)
        assertThat("missing <$tag>", v != null, equalTo(true))
        assertThat("<$tag>='$v' does not match /$pattern/", v!!.matches(Regex(pattern)), equalTo(true))
    }

    fun assertRepeatInstanceCount(xml: String, childTag: String, expected: Int) {
        assertThat("repeat instances (by <$childTag>)", occurrences(xml, childTag), equalTo(expected))
    }
}
