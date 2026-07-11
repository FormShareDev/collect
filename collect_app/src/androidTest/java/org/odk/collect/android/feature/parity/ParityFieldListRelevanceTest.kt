package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertAbsentOrEmpty
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Relevance on a question INSIDE an appearance="field-list" group: the dependent question must
 * appear/disappear LIVE on the same screen (no swipe) as the controlling field changes, and the
 * pruned node must be absent from the submission. Complements ParityStructureTest (field-list
 * layout) and ParityRelevanceTest (cross-screen relevance).
 */
@RunWith(AndroidJUnit4::class)
class ParityFieldListRelevanceTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private fun openFieldList() =
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_fieldlist_relevant.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Field List Relevant")
            .answerQuestion("Intro", "go")
            .swipeToNextQuestion("Show extra?") // enters the field-list screen

    @Test
    fun triggerNo_extraHiddenOnSameScreen_prunedInOutput() {
        openFieldList()
            .assertQuestion("Always")
            .assertNoQuestion("Extra") // not relevant before answering
            .clickOnText("No")
            .assertNoQuestion("Extra") // still hidden
            .answerQuestion("Always", "a")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "trigger", "no")
        assertExact(xml, "always_q", "a")
        assertAbsentOrEmpty(xml, "extra_q")
    }

    @Test
    fun triggerYes_extraAppearsLiveOnSameScreen_stored() {
        openFieldList()
            .assertNoQuestion("Extra")
            .clickOnText("Yes")
            .assertQuestion("Extra") // appears live, same screen, no swipe
            .answerQuestion("Always", "a")
            .answerQuestion("Extra", "x")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "trigger", "yes")
        assertExact(xml, "always_q", "a")
        assertExact(xml, "extra_q", "x")
    }

    @Test
    fun toggle_showsThenHidesLiveWithoutLeavingScreen() {
        openFieldList()
            .assertNoQuestion("Extra")
            .clickOnText("Yes")
            .assertQuestion("Extra") // shown
            .clickOnText("No")
            .assertNoQuestion("Extra") // hidden again
            .clickOnText("Yes")
            .assertQuestion("Extra") // shown again
    }
}
