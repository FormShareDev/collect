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
 * WIDGETS x APPEARANCES pillar — text and numeric families.
 *
 * For each widget type/appearance a KNOWN value is entered through the journey and the stored value
 * is asserted to round-trip unchanged: the appearance changes the UI, not the data.
 *
 * ODK Collect is the oracle. Values marked `// LOCK` (computed by the engine, or where display
 * differs from storage) are derived offline and confirmed/overwritten on the first Collect device
 * run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityWidgetsTextNumericTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    // ---- text family ---------------------------------------------------------------------------

    @Test
    fun textWidgets_roundTripAcrossAppearances() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_text.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Text")
            .answerQuestion("String default", "hello world")
            .swipeToNextQuestion("String multiline")
            .answerQuestion("String multiline", "line one\nline two")
            .swipeToNextQuestion("Url field")
            .answerQuestion("Url field", "http://opendatakit.org/")
            // note_field is readonly (calculate) — journey only navigates through it
            .swipeToNextQuestion("Note field")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "str_default", "hello world")
        assertExact(xml, "str_multiline", "line one\nline two")
        assertExact(xml, "url_field", "http://opendatakit.org/")
        assertExact(xml, "note_field", "computed note value") // LOCK: calculate default, not typed
        assertShape(xml, "instanceID", uuid)
    }

    // ---- numeric family ------------------------------------------------------------------------

    @Test
    fun numericWidgets_roundTripAcrossAppearances() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_numeric.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Numeric")
            .answerQuestion("Integer default", "42")
            .swipeToNextQuestion("Integer thousands")
            .answerQuestion("Integer thousands", "12345")
            .swipeToNextQuestion("Decimal default")
            .answerQuestion("Decimal default", "3.14")
            .swipeToNextQuestion("Decimal plain")
            .answerQuestion("Decimal plain", "2.5")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "int_default", "42")
        // thousands-sep formats the display ("12,345") but stores the raw digits.
        assertExact(xml, "int_thousands", "12345") // LOCK: display (12,345) != stored (12345)
        assertExact(xml, "dec_default", "3.14")
        assertExact(xml, "dec_plain", "2.5")
    }
}
