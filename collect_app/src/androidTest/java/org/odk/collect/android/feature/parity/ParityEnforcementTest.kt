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
 * Pillar 5 — constraint & required ENFORCEMENT journeys.
 *
 * These journeys prove Collect blocks progression/finalization when a constraint fails or a required
 * answer is missing (asserting the custom jr:constraintMsg / jr:requiredMsg), and that after
 * correction the form finalizes and stores the corrected value.
 *
 * ODK Collect is the oracle. Values marked `// LOCK` are derived offline and confirmed/overwritten
 * on the first Collect device run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityEnforcementTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    @Test
    fun constraintViolation_blocksSwipe_thenCorrectionProceedsAndStores() {
        openConstraint()
            .answerQuestion("Age", "200")
            // 200 is outside 0..120 → forward swipe blocked, custom constraint message shown
            .swipeToNextQuestionWithConstraintViolation("Age must be 0-120")
            // correct to a valid value → swipe now proceeds
            .answerQuestion("Age", "45")
            .swipeToNextQuestion("Name", true)
            .answerQuestion("Name", true, "Ada")
            .swipeToNextQuestion("Comment")
            .answerQuestion("Comment", "ok")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "age", "45")
        assertExact(xml, "name", "Ada")
        assertExact(xml, "comment", "ok")
        assertShape(xml, "instanceID", uuid)
    }

    @Test
    fun requiredMissing_blocksProgress_thenFilledFinalizesAndStores() {
        openConstraint()
            .answerQuestion("Age", "30")
            .swipeToNextQuestion("Name", true)
            // required name left empty → forward swipe blocked with the custom required message
            .swipeToNextQuestionWithConstraintViolation("Name is required")
            // fill it → progression allowed and the form finalizes
            .answerQuestion("Name", true, "Grace")
            .swipeToNextQuestion("Comment")
            .answerQuestion("Comment", "done")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "age", "30")
        assertExact(xml, "name", "Grace")
        assertExact(xml, "comment", "done")
        assertShape(xml, "instanceID", uuid)
    }

    @Test
    fun regexConstraint_blocksInvalid_thenValidProceedsAndStores() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_constraint_regex.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Constraint Regex")
            .answerQuestion("Code (2 letters + 3 digits)", "12ab")
            // fails regex ^[A-Z]{2}[0-9]{3}$ → forward swipe blocked with the custom message
            .swipeToNextQuestionWithConstraintViolation("Code must be 2 letters then 3 digits")
            // correct to a matching value → swipe now proceeds
            .answerQuestion("Code (2 letters + 3 digits)", "AB123")
            .swipeToNextQuestion("Confirm")
            .answerQuestion("Confirm", "yes")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "code", "AB123")
        assertExact(xml, "confirm", "yes")
        assertShape(xml, "instanceID", uuid)
    }

    private fun openConstraint() =
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_constraint.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Constraint")
}
