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
 * WIDGETS x APPEARANCES pillar — range and date/time families.
 *
 * Sliders and pickers are UI-specific and hard to drive deterministically, so range values (except
 * rating) and every date/time value are SEEDED via a `default` in the fixture; the journey only
 * navigates through the questions and asserts the stored value round-trips unchanged. The rating
 * widget is driven with setRating(). Seeded / slider-set values are marked `// LOCK`.
 *
 * ODK Collect is the oracle. `// LOCK` values are derived offline and confirmed/overwritten on the
 * first Collect device run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityWidgetsRangeDateTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    // ---- range family --------------------------------------------------------------------------

    @Test
    fun rangeWidgets_roundTripAcrossAppearances() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_range.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Range")
            // range_int / range_dec / range_vert / range_picker are seeded via fixture defaults;
            // the journey navigates through them without touching the sliders.
            .swipeToNextQuestion("Range decimal")
            .swipeToNextQuestion("Range vertical")
            .swipeToNextQuestion("Range picker")
            .swipeToNextQuestion("Range rating")
            .setRating(4.0f)
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "range_int", "5")       // LOCK: seeded default, slider not driven
        assertExact(xml, "range_dec", "2.5")      // LOCK: seeded default, slider not driven
        assertExact(xml, "range_vert", "7")       // LOCK: seeded default, slider not driven
        assertExact(xml, "range_picker", "3")     // LOCK: seeded default, picker not driven
        assertExact(xml, "range_rating", "4")     // LOCK: set via setRating(4.0f)
        assertShape(xml, "instanceID", uuid)
    }

    // ---- date/time family ----------------------------------------------------------------------

    @Test
    fun dateTimeWidgets_roundTripAcrossAppearances() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_datetime.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Datetime")
            // all four values are seeded via fixture defaults; picker entry is UI-specific, so the
            // journey only navigates through them and asserts the stored ISO value round-trips.
            .swipeToNextQuestion("Time default")
            .swipeToNextQuestion("Datetime default")
            .swipeToNextQuestion("Date ethiopian")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "date_default", "2026-01-15")                        // LOCK: seeded default
        // DST-sensitive: Collect normalizes the seeded time to the device's local offset. Now (summer)
        // that is EDT (-04:00); in winter it would be EST (-05:00).
        assertExact(xml, "time_default", "15:30:00.000-04:00")               // LOCK: seeded default, tz/DST-sensitive
        assertExact(xml, "datetime_default", "2026-01-15T14:30:00.000-05:00") // LOCK: seeded default, tz-sensitive
        // ethiopian appearance changes only the picker calendar; stored value stays Gregorian ISO.
        assertExact(xml, "date_ethiopian", "2026-01-15")                     // LOCK: seeded default, non-Gregorian UI
        assertShape(xml, "instanceID", uuid)
    }
}
