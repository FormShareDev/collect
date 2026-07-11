package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertAbsentOrEmpty
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertRepeatInstanceCount
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Pillar 11 — adversarial / edge-case journeys. Collect is the ORACLE: every engine-computed or
 * behaviour-dependent value is asserted at whatever Collect actually produces and marked `// LOCK`,
 * so the same value can be pinned on KotlinCollect. The `// LOCK` values below are best-effort
 * offline guesses; the first Collect device run confirms or overwrites them.
 */
@RunWith(AndroidJUnit4::class)
class ParityAdversarialTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    // ---- Fixture A: pathological calculates ----------------------------------------------------

    @Test
    fun mathEdgeCases_matchEngineOutput() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_edge_math.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Edge Math")
            .answerQuestion("Type anything to begin", "go")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "start", "go")
        // Each of these is whatever JavaRosa emits for the degenerate operation. LOCK = confirm on
        // first Collect device run and overwrite the guessed literal below.
        assertExact(xml, "div_by_zero", "Infinity") // LOCK: 1 div 0
        assertAbsentOrEmpty(xml, "zero_div_zero") // LOCK: 0 div 0 -> NaN, stored as empty/absent
        assertExact(xml, "big_mult", "9999999800000000") // LOCK: 99999999 * 99999999 (double rounding)
        assertExact(xml, "date_diff", "2192") // LOCK: date('2026-01-01') - date('2020-01-01') in days
        assertAbsentOrEmpty(xml, "sqrt_neg") // LOCK: sqrt(-1) -> NaN, stored as empty/absent
    }

    // ---- Fixture B: long + unicode text round-trip ---------------------------------------------

    @Test
    fun longAndUnicodeText_roundTripExactly() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_edge_text.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Edge Text")
            .answerQuestion("Long text", LONG_TEXT)
            .swipeToNextQuestion("Unicode text")
            .answerQuestion("Unicode text", UNICODE_TEXT)
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "long_text", LONG_TEXT) // LOCK: exact 540-char round-trip
        assertExact(xml, "unicode_text", UNICODE_TEXT) // LOCK: exact accents/emoji/RTL round-trip
    }

    // ---- Fixture C: large repeat count + empty select-multiple ---------------------------------

    @Test
    fun largeRepeatCount_isNotTruncated_andEmptySelectIsAbsent() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_edge_repeat.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Edge Repeat")
            .answerQuestion("How many items?", "15")
            // jr:count creates 15 instances; jump straight to the end without visiting each.
            .clickGoToArrow()
            .clickGoToEnd()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "rep_count", "15")
        assertRepeatInstanceCount(xml, "item", 15) // LOCK: all 15 count-driven instances present
        assertAbsentOrEmpty(xml, "colors") // LOCK: select-multiple with nothing selected
    }

    companion object {
        // A deterministic 540-char string (12 x 45). LOCK: constant so the round-trip assertion matches.
        private val LONG_TEXT = "The quick brown fox jumps over the lazy dog. ".repeat(12)

        // Accents, CJK, emoji (surrogate pairs) and an RTL (Arabic) run. LOCK: exact constant.
        private const val UNICODE_TEXT = "Café résumé naïve — Zürich Москва 日本語 😀🚀 مرحبا"
    }
}
