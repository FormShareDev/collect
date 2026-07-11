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
 * Pillar — WIDGETS × APPEARANCES: media (image/audio/file) and geo.
 *
 * Determinism policy: MEDIA = presence only (assert the widget/label appears; never assert bytes and
 * never capture). GEO = SEED a known geopoint via a default and assert the stored value round-trips
 * exactly, plus a calculate over the seeded geo. Values not typed by the journey are marked `// LOCK`.
 */
@RunWith(AndroidJUnit4::class)
class ParityWidgetsMediaGeoTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    @Test
    fun mediaPresent_geoSeeded_roundTripsAndCalculates() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_media_geo.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Media Geo")
            // MEDIA: assert each upload widget's label is present; do NOT capture any bytes
            .assertQuestion("Image upload")
            .swipeToNextQuestion("Audio upload")
            .swipeToNextQuestion("File upload")
            // GEO: seeded via default; navigate past without capturing GPS
            .swipeToNextQuestion("Geopoint")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        // Media never captured -> nodes pruned/empty (presence only, no bytes asserted)
        assertAbsentOrEmpty(xml, "image_field")
        assertAbsentOrEmpty(xml, "audio_field")
        assertAbsentOrEmpty(xml, "file_field")
        assertExact(xml, "geo_field", "38.25 -77.0 0.0 0.0") // LOCK: seeded geopoint default round-trip
        assertExact(xml, "geo_copy", "38.25 -77.0 0.0 0.0")  // LOCK: calculate over the seeded geopoint
        assertShape(xml, "instanceID", uuid)
    }
}
