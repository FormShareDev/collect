package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertShape
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Pillar 6 — defaults & dynamic values.
 *
 * Covers: static instance defaults (kept vs overridden), a dynamic default computed from another
 * node, a LIVE calculate that recomputes from two answers, a dynamic <output> label, and a
 * read-only calculate.
 *
 * ODK Collect is the oracle. Values marked `// LOCK` are engine-computed (calculate) and are
 * confirmed/overwritten on the first Collect device run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityDefaultsTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    @Test
    fun staticDefaultUntouched_roundTripsAndCalculatesLive() {
        open()
            // "Static default" starts pre-filled with "N/A"; we leave it untouched and swipe past.
            .assertAnswer("Static default", "N/A")
            .swipeToNextQuestion("Value A")
            .answerQuestion("Value A", "2")
            .swipeToNextQuestion("Value B")
            .answerQuestion("Value B", "3")
            // dynamic <output> label renders the current value of A (best-effort).
            .swipeToNextQuestion("Echo of A is 2")
            .swipeToNextQuestion("Read-only product")
            .assertAnswer("Read-only product", "6", true) // LOCK: readonly calculate a*b
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "txt_static", "N/A")                 // static default kept
        assertExact(xml, "a", "2")
        assertExact(xml, "b", "3")
        assertExact(xml, "live_calc", "5")                    // LOCK: live calculate a + b
        assertExact(xml, "ro_calc", "6")                      // LOCK: readonly calculate a * b
        assertShape(xml, "txt_dyn", "id-\\d+")                // LOCK: dynamic default concat('id-', seed)
        assertShape(xml, "instanceID", uuid)
    }

    @Test
    fun staticDefaultOverridden_storesNewValue() {
        open()
            .assertAnswer("Static default", "N/A")
            .answerQuestion("Static default", "custom")
            .swipeToNextQuestion("Value A")
            .answerQuestion("Value A", "10")
            .swipeToNextQuestion("Value B")
            .answerQuestion("Value B", "4")
            .swipeToNextQuestion("Echo of A is 10")
            .swipeToNextQuestion("Read-only product")
            .assertAnswer("Read-only product", "40", true) // LOCK: readonly calculate a*b
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "txt_static", "custom")              // static default overridden
        assertExact(xml, "live_calc", "14")                   // LOCK: live calculate a + b
        assertExact(xml, "ro_calc", "40")                     // LOCK: readonly calculate a * b
    }

    private fun open() =
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_defaults.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Defaults")
}
