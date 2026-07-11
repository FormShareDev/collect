package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
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
 * Pillar — structure & nesting journeys.
 *
 * Covers presentation grouping and its effect on the produced submission:
 *  - deeply nested groups produce nested output wrappers (<A><B><C><c_q/>…);
 *  - a field-list group renders several questions on ONE screen;
 *  - a non-relevant whole group is pruned from the instance.
 *
 * ODK Collect is the oracle. Values marked `// LOCK` are derived offline and confirmed/overwritten
 * on the first Collect device run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityStructureTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    // ---- Fixture A: groups nested 3 deep -------------------------------------------------------

    @Test
    fun nestedGroups_produceNestedOutputWrappers() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_groups_nested.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Groups Nested")
            .answerQuestion("Flat question", "flat value")
            .swipeToNextQuestion("A question")
            .answerQuestion("A question", "alpha")
            .swipeToNextQuestion("B question")
            .answerQuestion("B question", "bravo")
            .swipeToNextQuestion("C question")
            .answerQuestion("C question", "charlie")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)

        // Leaf values (valueOf finds leaves regardless of nesting depth).
        assertExact(xml, "flat_q", "flat value")
        assertExact(xml, "a_q", "alpha")
        assertExact(xml, "b_q", "bravo")
        assertExact(xml, "c_q", "charlie")

        // Structure: the wrappers exist and are nested A > B > C.
        assertThat(xml, containsString("<A>"))
        assertThat(xml, containsString("<B>"))
        assertThat(xml, containsString("<C>"))
        val a = xml.indexOf("<A>")
        val b = xml.indexOf("<B>")
        val c = xml.indexOf("<C>")
        val cClose = xml.indexOf("</C>")
        val bClose = xml.indexOf("</B>")
        val aClose = xml.indexOf("</A>")
        // opening order A, B, C then closing order C, B, A proves the nesting.
        assertThat("A opens before B", a in 0 until b, equalTo(true))
        assertThat("B opens before C", b in 0 until c, equalTo(true))
        assertThat("C closes before B", c in 0 until cClose && cClose < bClose, equalTo(true))
        assertThat("B closes before A", bClose in 0 until aClose, equalTo(true))
    }

    // ---- Fixture B: field-list (one screen, three questions) -----------------------------------

    @Test
    fun fieldList_allThreeOnOneScreen_stored() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_fieldlist.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Field List")
            // form opens on a plain intro question, then swipe into the field-list page
            .answerQuestion("Intro", "go")
            .swipeToNextQuestion("Text field")
            // all three are on the same page — answer without swiping between them
            .answerQuestion("Text field", "hello")
            .answerQuestion("Integer field", "42")
            .clickOnText("Green")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "intro", "go")
        assertExact(xml, "f1", "hello")
        assertExact(xml, "f2", "42")
        assertExact(xml, "f3", "green")
    }

    // ---- Fixture C: whole-group relevance ------------------------------------------------------

    @Test
    fun groupRelevanceNo_sectionPruned() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_group_relevance.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Group Relevance")
            .clickOnText("No")
            // optional_section non-relevant → its questions never appear, straight to end
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "show", "no")
        assertAbsentOrEmpty(xml, "opt_a")
        assertAbsentOrEmpty(xml, "opt_b")
    }

    @Test
    fun groupRelevanceYes_sectionPresentAndStored() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_group_relevance.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Group Relevance")
            .clickOnText("Yes")
            .swipeToNextQuestion("Optional A")
            .answerQuestion("Optional A", "first")
            .swipeToNextQuestion("Optional B")
            .answerQuestion("Optional B", "second")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "show", "yes")
        assertExact(xml, "opt_a", "first")
        assertExact(xml, "opt_b", "second")
    }
}
