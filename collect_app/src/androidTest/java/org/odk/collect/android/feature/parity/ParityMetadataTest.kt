package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertShape
import org.odk.collect.android.feature.parity.ParitySubmission.valueOf
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo

/**
 * Pillar 7 — preloaded metadata.
 *
 * Fills one text question and finalizes. Every preloaded metadata field is non-deterministic
 * (clock/device/user dependent), so we assert by SHAPE only — never by exact value.
 *
 * ODK Collect is the oracle. The one typed value (note_text) is exact; the metadata shapes are
 * confirmed on the first Collect device run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityMetadataTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    // ISO datetime as serialized by Collect: millis + zone offset (+HH:MM / -HH:MM) or Z.
    private val isoDateTime = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}([+-]\\d{2}:\\d{2}|Z)"
    private val isoDate = "\\d{4}-\\d{2}-\\d{2}"
    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    @Test
    fun metadataPreloads_haveExpectedShapes() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_metadata.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Metadata")
            .answerQuestion("Enter some text", "hello")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "note_text", "hello")

        // Non-deterministic metadata — SHAPE only. // LOCK: engine/device-preloaded.
        assertShape(xml, "start", isoDateTime)   // LOCK: preload timestamp/start
        assertShape(xml, "end", isoDateTime)     // LOCK: preload timestamp/end
        assertShape(xml, "today", isoDate)       // LOCK: preload date/today
        assertShape(xml, "instanceID", uuid)     // LOCK: preload uid

        // deviceid: non-empty (device-provided).
        assertNonEmpty(xml, "deviceid")          // LOCK: preload property/deviceid
        // username: present but EMPTY — the demo/test project has no project user configured, so
        // property/username preloads to an empty string. Assert presence only, not non-empty.
        assertPresent(xml, "username")           // LOCK: preload property/username (empty without a project user)
    }

    private fun assertPresent(xml: String, tag: String) {
        val v = valueOf(xml, tag)
        assertThat("<$tag> should be present but was missing", v != null, equalTo(true))
    }

    private fun assertNonEmpty(xml: String, tag: String) {
        val v = valueOf(xml, tag)
        assertThat("<$tag> should be present and non-empty but was '$v'", v != null && v.isNotEmpty(), equalTo(true))
    }
}
