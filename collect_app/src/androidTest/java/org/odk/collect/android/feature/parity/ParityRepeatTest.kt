package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertRepeatInstanceCount
import org.odk.collect.android.feature.parity.ParitySubmission.assertShape
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.pages.FormEndPage
import org.odk.collect.android.support.pages.FormEntryPage
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Pillar — repeats.
 *
 * Exercises the four repeat behaviours Collect must reproduce identically in KotlinCollect:
 *  - fixed jr:count auto-creates instances with NO "add another" prompt,
 *  - user-controlled repeats driven through the add/do-not-add dialog,
 *  - nested repeats where an inner jr:count references an outer sibling value,
 *  - repeat aggregate functions (sum/count/indexed-repeat/join).
 *
 * ODK Collect is the oracle. Values marked `// LOCK` are engine-computed (calculate/repeat
 * function) and confirmed/overwritten on the first Collect device run before porting.
 */
@RunWith(AndroidJUnit4::class)
class ParityRepeatTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    // ---- Fixture A: fixed jr:count="3" — auto-created, no add prompt --------------------------

    @Test
    fun fixedCount_createsExactlyThreeInstances_noPrompt() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_repeat_fixed.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Repeat Fixed")
            // Form opens on a plain intro question, then swipe into the repeat.
            .answerQuestion("Intro", "go")
            .swipeToNextQuestion("Visit note")
            // Three instances are pre-created by jr:count; walk through all three notes.
            // No "add another" dialog appears — the swipe after the third note lands on the end.
            .answerQuestion("Visit note", "first")
            .swipeToNextQuestion("Visit note")
            .answerQuestion("Visit note", "second")
            .swipeToNextQuestion("Visit note")
            .answerQuestion("Visit note", "third")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "intro", "go")
        assertRepeatInstanceCount(xml, "note", 3)
        assertShape(xml, "instanceID", uuid)
    }

    // ---- Fixture B: user-controlled repeat — add 2 then decline -------------------------------

    @Test
    fun userRepeat_addTwoThenDecline_countTwo() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_repeat_user.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Repeat User")
            // Form opens on a plain intro question, then swipe into the repeat.
            .answerQuestion("Intro", "go")
            // A user-controlled repeat with no instances shows the "add?" dialog on entry.
            .swipeToNextQuestionWithRepeatGroup("Item")
            .clickOnAdd(FormEntryPage("Parity Repeat User"))
            .answerQuestion("Item name", "apple")
            .swipeToNextQuestionWithRepeatGroup("Item")
            .clickOnAdd(FormEntryPage("Parity Repeat User"))
            .answerQuestion("Item name", "banana")
            .swipeToNextQuestionWithRepeatGroup("Item")
            .clickOnDoNotAdd(FormEndPage("Parity Repeat User"))
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "intro", "go")
        assertRepeatInstanceCount(xml, "item_name", 2)
        assertExact(xml, "item_count", "2") // LOCK: count() over user-added instances
    }

    // ---- Fixture C: nested repeats — inner jr:count from outer sibling -------------------------

    @Test
    fun nestedRepeat_twoHouseholdsSizesTwoAndOne_memberCounts() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_repeat_nested.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Repeat Nested")
            // Form opens on a plain intro question, then swipe into the repeat.
            .answerQuestion("Intro", "go")
            // A user-controlled outer repeat with no instances shows the "add?" dialog on entry.
            .swipeToNextQuestionWithRepeatGroup("Household")
            .clickOnAdd(FormEntryPage("Parity Repeat Nested"))
            // Household 1: size 2 -> inner member repeat auto-creates 2 members.
            .answerQuestion("Household name", "Alpha")
            .swipeToNextQuestion("Household size")
            .answerQuestion("Household size", "2")
            .swipeToNextQuestion("Member name")
            .answerQuestion("Member name", "A1")
            .swipeToNextQuestion("Member name")
            .answerQuestion("Member name", "A2")
            // Next swipe offers a new household; add household 2.
            .swipeToNextQuestionWithRepeatGroup("Household")
            .clickOnAdd(FormEntryPage("Parity Repeat Nested"))
            // Household 2: size 1 -> inner member repeat auto-creates 1 member.
            .answerQuestion("Household name", "Beta")
            .swipeToNextQuestion("Household size")
            .answerQuestion("Household size", "1")
            .swipeToNextQuestion("Member name")
            .answerQuestion("Member name", "B1")
            .swipeToNextQuestionWithRepeatGroup("Household")
            .clickOnDoNotAdd(FormEndPage("Parity Repeat Nested"))
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "intro", "go")
        assertRepeatInstanceCount(xml, "hh_name", 2)
        assertRepeatInstanceCount(xml, "member_name", 3) // 2 + 1
        assertExact(xml, "total_members", "3")           // LOCK: count() over all nested members
        // Per-household member counts (in document order).
        assertThat("household[0]/hh_member_count", ParitySubmission.valueOf(householdBlock(xml, 0), "hh_member_count"), equalTo("2")) // LOCK
        assertThat("household[1]/hh_member_count", ParitySubmission.valueOf(householdBlock(xml, 1), "hh_member_count"), equalTo("1")) // LOCK
    }

    // ---- Fixture D: repeat aggregate functions ------------------------------------------------

    @Test
    fun repeatFunctions_sumCountFirstJoin() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_repeat_functions.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Repeat Functions")
            // Form opens on a plain intro question, then swipe into the repeat.
            .answerQuestion("Intro", "go")
            .swipeToNextQuestion("Value")
            // Fixed 3 rows; type 1, 2, 3.
            .answerQuestion("Value", "1")
            .swipeToNextQuestion("Value")
            .answerQuestion("Value", "2")
            .swipeToNextQuestion("Value")
            .answerQuestion("Value", "3")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "intro", "go")
        assertRepeatInstanceCount(xml, "val", 3)
        assertExact(xml, "sum_vals", "6")       // LOCK: sum() over repeat
        assertExact(xml, "count_vals", "3")     // LOCK: count() over repeat
        assertExact(xml, "first_val", "1")      // LOCK: indexed-repeat() first instance
        assertExact(xml, "pos_join", "1,2,3")   // LOCK: join() order + separator
    }

    // ---- Fixture E: open-ended repeat popup — yes/no navigation targets ------------------------

    @Test
    fun openEndedRepeatPopup_yesGoesToNewInstance_noGoesToQuestionAfterRepeat() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_repeat_popup.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Repeat Popup")
            .answerQuestion("Intro", "go")
            // Entering an empty open-ended repeat shows the "Add Item?" popup (assertOnPage in the dialog).
            .swipeToNextQuestionWithRepeatGroup("Item")
            // YES -> lands on the new instance's first question.
            .clickOnAdd(FormEntryPage("Parity Repeat Popup"))
            .assertQuestion("Item name")
            .answerQuestion("Item name", "A")
            // End of instance 1 -> popup again; YES -> instance 2's question.
            .swipeToNextQuestionWithRepeatGroup("Item")
            .clickOnAdd(FormEntryPage("Parity Repeat Popup"))
            .assertQuestion("Item name")
            .answerQuestion("Item name", "B")
            // End of instance 2 -> popup; NO -> the question AFTER the repeat (not the end screen).
            .swipeToNextQuestionWithRepeatGroup("Item")
            .clickOnDoNotAdd(FormEntryPage("Parity Repeat Popup"))
            .assertQuestion("After repeat")
            .answerQuestion("After repeat", "done")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "intro", "go")
        assertRepeatInstanceCount(xml, "item_name", 2)
        assertExact(xml, "after_repeat", "done")
    }

    /** Substring of the submission covering the n-th (0-based) <household>…</household> block. */
    private fun householdBlock(xml: String, index: Int): String {
        val matches = Regex("<household>.*?</household>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).toList()
        return matches[index].value
    }
}
