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
 * Select one / select multiple from EXTERNAL datasets (secondary instances):
 *  - GeoJSON (select_one_from_file .geojson): value = top-level `id`, label = `title` property;
 *    the feature `geometry` is accessible in ODK format.
 *  - XML (select_multiple_from_file .xml): value = `name` child, label = `label` child.
 * Files crafted at test-forms/src/main/resources/media/{parity_places.geojson,parity_fruits.xml};
 * attached via copyForm(..., listOf(...)). ODK Collect is the oracle.
 */
@RunWith(AndroidJUnit4::class)
class ParitySelectExternalTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    @Test
    fun selectOneFromGeojson_andMultipleFromXml_storeExpectedValues() {
        rule.withProject(testDependencies.server.url)
            .copyForm(
                "parity_select_external.xml",
                listOf("parity_places.geojson", "parity_fruits.xml"),
                testDependencies.server.hostName
            )
            .startBlankForm("Parity Select External")
            .answerQuestion("Intro", "go")
            // select_one from GeoJSON: labels are the `title` property
            .swipeToNextQuestion("Pick a place")
            .clickOnText("Giger Museum")
            // select_multiple from XML: labels are the `label` child
            .swipeToNextQuestion("Pick fruits")
            .clickOnText("Apple")
            .clickOnText("Cherry")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        // GeoJSON select stores the feature id
        assertExact(xml, "place", "fs87b")                       // LOCK
        // geometry of the selected feature, in ODK "lat lon alt acc" format
        assertExact(xml, "geom_selected", "46.5841618 7.0801379 0 0") // LOCK
        // XML multi-select stores space-separated values
        assertExact(xml, "fruits", "apple cherry")               // LOCK: order
    }
}
