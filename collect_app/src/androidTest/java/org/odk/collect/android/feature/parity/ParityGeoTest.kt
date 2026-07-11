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
 * Geography functions (https://docs.getodk.org/form-operators-functions/#geography):
 * area(), enclosed-area() (alias), distance(), geofence(point, polygon).
 * Deterministic given fixed coordinates. Golden computed offline with the bundled JavaRosa and
 * confirmed on device. (These are core functions — also exercised inside parity_functions.)
 */
@RunWith(AndroidJUnit4::class)
class ParityGeoTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    @Test
    fun areaDistanceGeofence_matchGolden() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_geo.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Geo")
            .answerQuestion("Start", "go")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "fn_area", "1239188.5162540074")     // LOCK: area() m^2
        assertExact(xml, "fn_enclosed", "1239188.5162540074") // LOCK: enclosed-area() == area()
        assertExact(xml, "fn_distance", "2226.3768965669715") // LOCK: distance() m
        assertExact(xml, "fn_geofence_in", "1")               // LOCK: point inside polygon
        assertExact(xml, "fn_geofence_out", "0")              // LOCK: point outside polygon
    }
}
