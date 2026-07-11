package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * The geospatial CAPTURE widgets (https://docs.getodk.org/form-question-types/#capturing-geospatial-data):
 * geopoint, geotrace, geoshape. Actual GPS/map capture is non-deterministic, so each widget is
 * SEEDED with a default and the journey navigates past it (asserting it renders) while the stored
 * value round-trips. This locks the data-level behavior + the widget's value normalization
 * (`0 0`->`0.0 0.0`, `; `->`;`). ODK Collect is the oracle.
 */
@RunWith(AndroidJUnit4::class)
class ParityGeoWidgetsTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    @Test
    fun geopointGeotraceGeoshape_render_andSeededValuesRoundTrip() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_geo_widgets.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Geo Widgets")
            .answerQuestion("Intro", "go")
            .swipeToNextQuestion("Geopoint") // widget renders
            .swipeToNextQuestion("Geotrace")
            .swipeToNextQuestion("Geoshape")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "gp", "38.25 -77.0 0.0 0.0") // LOCK: geopoint round-trip (normalized)
        assertExact(xml, "gt", "38.25 -77.0 0.0 0.0;38.26 -77.0 0.0 0.0") // LOCK: geotrace
        assertExact(
            xml, "gs",
            "38.25 -77.0 0.0 0.0;38.25 -77.01 0.0 0.0;38.26 -77.01 0.0 0.0;38.25 -77.0 0.0 0.0"
        ) // LOCK: geoshape
    }
}
