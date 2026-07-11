package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertShape
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.pages.AppClosedPage
import org.odk.collect.android.support.pages.FormHierarchyPage
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.FormEntryActivityTestRule
import org.odk.collect.android.support.rules.RecentAppsRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Pillar 10 — form lifecycle journeys (draft save/resume, edit of a finalized instance,
 * process-death / savepoint recovery).
 *
 * Each journey drives the real Collect lifecycle machinery and then asserts the produced
 * submission (journeys 1 & 2) or the in-form state after recovery (journey 3). ODK Collect is
 * the oracle. Values marked `// LOCK` are engine-derived (uuid shape) and confirmed on the first
 * Collect device run before this test is ported to KotlinCollect. All values typed by the journey
 * (text answers) are deterministic and NOT locked.
 */
@RunWith(AndroidJUnit4::class)
class ParityLifecycleTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    // ---- Journey 1: draft save/resume ----------------------------------------------------------

    @Test
    fun draft_partialAnswersSurviveSaveAndResume_thenFinalizeAndSend() {
        // Fill 2 of 3 questions, then save as a draft and leave the form.
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_lifecycle.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Lifecycle")
            .answerQuestion("What is your name?", "Ada")
            .swipeToNextQuestion("Favourite colour?")
            .answerQuestion("Favourite colour?", "green")
            .pressBackAndSaveAsDraft()

            // Reopen from Drafts and confirm the partial answers are still present.
            .clickDrafts(1)
            .clickOnForm("Parity Lifecycle")
            .clickGoToStart()
            .assertAnswer("What is your name?", "Ada")
            .swipeToNextQuestion("Favourite colour?")
            .assertAnswer("Favourite colour?", "green")

            // Finish the third question, finalize and send.
            .swipeToNextQuestion("Which city?")
            .answerQuestion("Which city?", "London")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "name", "Ada")
        assertExact(xml, "color", "green")
        assertExact(xml, "city", "London")
        assertShape(xml, "instanceID", uuid) // LOCK: engine-generated uid
    }

    // ---- Journey 2: edit a finalized instance --------------------------------------------------

    @Test
    fun editFinalizedInstance_changedAnswerAppearsInSubmission() {
        // Fill and finalize a first instance.
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_lifecycle.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Lifecycle")
            .answerQuestion("What is your name?", "Ada")
            .swipeToNextQuestion("Favourite colour?")
            .answerQuestion("Favourite colour?", "green")
            .swipeToNextQuestion("Which city?")
            .answerQuestion("Which city?", "London")
            .swipeToEndScreen()
            .clickFinalize()

            // Edit the finalized instance (form opts in via odk:client-editable) and change one answer.
            .clickSendFinalizedForm(1)
            .clickOnForm("Parity Lifecycle")
            .editForm("Parity Lifecycle")
            // editForm lands on the FormHierarchyPage; jump to the colour question, change it, then
            // walk forward through the remaining question (city) to reach the end screen reliably.
            .clickOnQuestion("Favourite colour?")
            .answerQuestion("Favourite colour?", "blue")
            .swipeToNextQuestion("Which city?")
            .swipeToEndScreen("Parity Lifecycle (Edit 1)")
            .clickFinalize()

            .clickSendFinalizedForm(2).clickSelectAll().clickSendSelected()

        // Submission index 0 = original, index 1 = edited copy.
        val edited = ParitySubmission.submissionXml(testDependencies.server.submissions, 1)
        assertExact(edited, "name", "Ada")
        assertExact(edited, "color", "blue")
        assertExact(edited, "city", "London")
    }
}

/**
 * Pillar 10 — process-death / savepoint recovery. Kept in its own class because it uses
 * [FormEntryActivityTestRule] + [RecentAppsRule], mirroring the reference SavePointTest exactly
 * (see feature/formentry/SavePointTest.kt). No server / submission here: we assert that the
 * answers typed before the app was killed are preserved after recovering the savepoint.
 */
@RunWith(AndroidJUnit4::class)
class ParityLifecycleSavepointTest {

    private val rule = FormEntryActivityTestRule()
    private val recentAppsRule = RecentAppsRule()

    @get:Rule
    val ruleChain: RuleChain = TestRuleChain.chain()
        .around(recentAppsRule)
        .around(rule)

    @Test
    @Ignore("RecentAppsRule unsupported on this API level (>=33 on some devices); process-death parity must run on a supported device")
    fun answersSurviveProcessDeath_viaSavepointRecovery() {
        rule.setUpProjectAndCopyForm("parity_lifecycle.xml")
            .fillNewForm("parity_lifecycle.xml", "Parity Lifecycle")
            .answerQuestion("What is your name?", "Ada")
            .swipeToNextQuestion("Favourite colour?")
            .answerQuestion("Favourite colour?", "green")

        recentAppsRule.leaveAndKillApp()

        rule.fillNewFormWithSavepoint("parity_lifecycle.xml")
            .clickRecover(FormHierarchyPage("Parity Lifecycle"))
            .clickGoToStart()
            .assertAnswer("What is your name?", "Ada")
            .swipeToNextQuestion("Favourite colour?")
            .assertAnswer("Favourite colour?", "green")
            .pressBackAndDiscardForm(AppClosedPage())
    }
}
