package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Labels: do they support embedded output() of a variable and of a function, and Markdown?
 * This is a RENDERING assertion (labels never reach the submission), so it checks the on-screen
 * label text. ODK Collect is the oracle: if a marker is NOT stripped or an output is NOT inlined,
 * flip the expectation to Collect's real behavior.
 */
@RunWith(AndroidJUnit4::class)
class ParityLabelsTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    @Test
    fun labels_embedOutputOfVariableAndFunction_andRenderMarkdown() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_labels.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Labels")
            .answerQuestion("Start", "go")
            .swipeToNextQuestion("Number")
            .answerQuestion("Number", "10")
            .closeSoftKeyboard()
            // output() of a variable and of a function/expression, inlined into the label
            .assertText("Value is 10")
            .assertText("Doubled is 20")
            // Markdown markers stripped from the rendered label text
            .assertText("Weight in kg")
            .assertTextDoesNotExist("Weight in **kg**")
            .assertText("Enter name here")
            .assertText("See ODK")
    }
}
