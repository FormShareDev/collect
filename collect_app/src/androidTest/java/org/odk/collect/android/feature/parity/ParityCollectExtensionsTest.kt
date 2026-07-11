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
 * Collect expression extensions that core JavaRosa rejects but Collect registers at runtime:
 *  - pulldata() over an external CSV.
 *  - intersects(geometry): SINGLE-arg SELF-intersection test (input must be geotrace/geoshape;
 *    a string throws). Registered via IntersectsFunctionHandler in the geo module — see
 *    collect_app/src/test/.../IntersectsFunctionHandlerTest.kt.
 * (replace() and the union `|` operator are not enabled in this build.)
 *
 * ODK Collect is the oracle; `// LOCK` values are confirmed on the device run.
 */
@RunWith(AndroidJUnit4::class)
class ParityCollectExtensionsTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    @Test
    fun pulldataAndSelfIntersects_matchCollectOutput() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_collect_extensions.xml", listOf("cities.csv"), testDependencies.server.hostName)
            .startBlankForm("Parity Collect Extensions")
            .answerQuestion("Intro", "go")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "note1", "go")
        assertExact(xml, "pull_label", "Tokyo") // LOCK: pulldata label for name='tokyo'
        assertExact(xml, "pull_id", "3")         // LOCK: pulldata id for name='lima'
        // intersects(geometry) self-intersection: crossing trace -> true, simple trace -> false
        assertExact(xml, "fn_selfcross", "1")    // LOCK
        assertExact(xml, "fn_simple", "0")       // LOCK
    }
}
