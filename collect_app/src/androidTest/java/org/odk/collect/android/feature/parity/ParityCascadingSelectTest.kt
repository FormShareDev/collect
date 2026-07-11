package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertAbsentOrEmpty
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertShape
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Pillar 5 — cascading choice-filter selects.
 *
 * A child select-one (`region`) is an itemset filtered by the chosen parent (`country`) via a
 * choice-filter over a secondary instance. Journeys prove (a) only the parent's children are
 * selectable and the pair is stored, and (b) changing the parent CLEARS a previously chosen child
 * (Collect's documented cascading behavior).
 *
 * ODK Collect is the oracle. Values marked `// LOCK` are derived offline and confirmed/overwritten
 * on the first Collect device run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityCascadingSelectTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    @Test
    fun ghana_filtersToGhanaRegions_storesPair() {
        openCascading()
            .clickOnText("Ghana")
            .swipeToNextQuestion("Region")
            // choice-filter shows only Ghana regions; Kenya regions are absent
            .assertText("Greater Accra")
            .assertText("Ashanti")
            .assertTextDoesNotExist("Nairobi")
            .assertTextDoesNotExist("Mombasa")
            .clickOnText("Greater Accra")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "country", "ghana")
        assertExact(xml, "region", "accra")
        assertShape(xml, "instanceID", uuid)
    }

    @Test
    fun changingCountry_clearsPreviouslyChosenRegion() {
        openCascading()
            .clickOnText("Ghana")
            .swipeToNextQuestion("Region")
            .clickOnText("Greater Accra")
            // go back and change the parent → the chosen Ghana region is no longer valid
            .swipeToPreviousQuestion("Country")
            .clickOnText("Kenya")
            .swipeToNextQuestion("Region")
            // Kenya regions are now the choices; the previous Ghana selection is cleared
            .assertText("Nairobi")
            .assertText("Mombasa")
            .assertTextDoesNotExist("Greater Accra")
            // leave region unanswered to capture the cleared state in the submission
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "country", "kenya")
        assertAbsentOrEmpty(xml, "region")   // LOCK: cascading clears the child when parent changes
        assertShape(xml, "instanceID", uuid)
    }

    private fun openCascading() =
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_cascading_select.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Cascading Select")
}
