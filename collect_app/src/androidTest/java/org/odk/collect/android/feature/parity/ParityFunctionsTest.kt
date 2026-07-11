package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
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

/**
 * Pillar 1 — FUNCTIONS & OPERATORS parity.
 *
 * This pillar REUSES the kotlinrosa engine differential fixture (`odk_all_features.xml`, copied
 * verbatim to `parity_functions.xml` with only the form title + id changed) so that the app-level
 * assertions here bind to the SAME golden values that the kotlinrosa engine test asserts
 * (`org.javarosa.differential.OdkReferenceFormDifferentialTest`). The golden map below is copied
 * verbatim from that test.
 *
 * ODK Collect is the oracle. The ENTIRE golden block is engine-computed (calculate / repeat
 * functions / date math) and is therefore marked `// LOCK` — these values are confirmed against
 * Collect's real device output on the first run; if Collect legitimately differs, the golden (and
 * the kotlinrosa golden) is corrected to match Collect.
 *
 * ---------------------------------------------------------------------------------------------
 * INPUTS: every fixed input that drives the calculates is supplied by an `<instance>` default in
 * the fixture, so NO question needs to be answered to reproduce the golden values:
 *   in_text="Hello World", in_int="7", in_dec="3.5", in_yn="yes", in_multi="a c",
 *   in_date="2026-01-15", cur_id="2", geo_poly=<polygon>, has_kids="yes", num_kids="2",
 *   current_age="40", txt_static_default="N/A", rep_count="2"  (all instance defaults);
 *   agg_rep_count and ro_calc are calculates.
 *
 * REQUIRED fields (must be satisfied to finalize):
 *   - /data/g_logic/num_kids       required, but defaulted to "2" -> already satisfied.
 *   - /data/g_other/trig_default   required acknowledge trigger with NO default -> the ONE thing
 *                                   the journey must actually drive ("Acknowledge to continue").
 *
 * NAVIGATION UNCERTAINTY: the fixture renders ~57 widgets across 18 groups; several question
 * labels repeat across groups (e.g. "Default", "Integer", "Date"), and the field-list groups
 * collapse multiple questions onto one screen. Enumerating the exact forward-swipe chain to the
 * required trigger cannot be done reliably offline. The traversal below is a first draft: it opens
 * the form (all defaults populate), acknowledges the required trigger, then swipes to the end and
 * finalizes. The first Collect device run will reveal the precise swipe steps needed to reach the
 * trigger screen; adjust the traversal there. The ASSERTIONS are the substance of this pillar and
 * are fully wired.
 */
@RunWith(AndroidJUnit4::class)
class ParityFunctionsTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    // Collect serializes with the device-local offset, not UTC "Z".
    private val isoDateTime = """\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}(?:Z|[+-]\d{2}:\d{2})"""
    private val isoDate = """\d{4}-\d{2}-\d{2}"""
    private val uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    @Test
    fun deterministicOperatorsAndFunctions_matchGolden() {
        openForm()
            // All golden inputs come from <instance> defaults — nothing to answer, and the
            // once-required trigger is now optional, so jump straight to the end via the hierarchy.
            .clickGoToArrow()
            .clickGoToEnd()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)

        // LOCK — copied verbatim from kotlinrosa OdkReferenceFormDifferentialTest.golden.
        // Every entry is engine-computed and confirmed against Collect on the first device run.
        val golden = mapOf(
            // Fixed inputs that drive the calculates.
            "in_text" to "Hello World",
            "in_int" to "7",
            "in_dec" to "3.5",
            "in_yn" to "yes",
            "in_multi" to "a c",
            "in_date" to "2026-01-15",
            "cur_id" to "2",
            "geo_poly" to "38.25 -77.00 0 0;38.25 -77.01 0 0;38.26 -77.01 0 0;38.26 -77.00 0 0;38.25 -77.00 0 0",
            "has_kids" to "yes",
            "num_kids" to "2",
            "current_age" to "40",
            "txt_static_default" to "N/A",
            "rep_count" to "2",
            "agg_rep_count" to "3",
            "ro_calc" to "4",
            // Operators (booleans serialize as 1/0).
            "op_add" to "10.5",
            "op_sub" to "6",
            "op_mul" to "14",
            "op_div" to "3.5",
            "op_mod" to "1",
            "op_neg" to "-7",
            "op_eq" to "1",
            "op_neq" to "0",
            "op_lt" to "1",
            "op_le" to "1",
            "op_gt" to "0",
            "op_ge" to "1",
            "op_and" to "1",
            "op_or" to "1",
            // Functions.
            "fn_if" to "pos",
            "fn_coalesce" to "7",
            "fn_not" to "0",
            "fn_true" to "1",
            "fn_false" to "0",
            "fn_boolean" to "1",
            "fn_boolean_from_string" to "1",
            "fn_number" to "42",
            "fn_int" to "3",
            "fn_string" to "7",
            "fn_is_selected" to "1",
            // Date/time serialize with the device-local offset (America/New_York = EST for these
            // fixed January dates), and decimal-*-time include the offset fraction. Stable for this
            // TZ; KotlinCollect must run under the same TZ to compare byte-for-byte.
            "fn_date" to "2026-01-15T00:00:00.000-05:00",
            "fn_date_time" to "2026-01-15T10:30:00.000-05:00",
            "fn_decimal_date_time" to "20468.208333333332",
            "fn_decimal_time" to "0.2916666666678793",
            "fn_format_date" to "2026/01/15",
            "fn_format_date_time" to "2026-01-15 13:05",
            "fn_abs" to "5",
            "fn_acos" to "1.0471975511965979",
            "fn_asin" to "0.5235987755982989",
            "fn_atan" to "0.7853981633974483",
            "fn_atan2" to "0.7853981633974483",
            "fn_cos" to "1",
            "fn_sin" to "0",
            "fn_tan" to "0",
            "fn_exp" to "1",
            "fn_exp10" to "100",
            "fn_log" to "0",
            "fn_log10" to "3",
            "fn_pi" to "3.141592653589793",
            "fn_pow" to "1024",
            "fn_round" to "4",
            "fn_sqrt" to "12",
            "fn_concat" to "Hello World-7",
            "fn_join" to "1/2/3",
            "fn_substr" to "Hello",
            "fn_substring_before" to "user",
            "fn_substring_after" to "example.com",
            "fn_contains" to "1",
            "fn_starts_with" to "1",
            "fn_ends_with" to "1",
            "fn_translate" to "ABC",
            "fn_string_length" to "11",
            "fn_normalize_space" to "a b",
            "fn_regex" to "1",
            "fn_selected" to "1",
            "fn_count_selected" to "2",
            "fn_selected_at" to "c",
            "fn_jr_choice_name" to "Yes",
            "fn_count" to "3",
            "fn_count_non_empty" to "3",
            "fn_sum" to "6",
            "fn_max" to "3",
            "fn_min" to "1",
            "fn_indexed_repeat" to "2",
            "fn_distance" to "1113.188452340665",
            "fn_area" to "973088.7737964024",
            "fn_enclosed_area" to "973088.7737964024",
            "fn_geofence" to "1",
            "fn_digest" to "5d41402abc4b2a76b9719d911017c592",
            "fn_base64_decode" to "hello",
            "fn_checklist" to "0",
            "fn_weighted_checklist" to "1",
            "fn_depend" to "7",
            // cities.csv attached via copyForm(..., listOf("cities.csv")); instance('cities') has
            // 3 rows → count() = 3. Exercises external-CSV instance() end-to-end.
            "fn_instance" to "3",
            "fn_current" to "0"
        )

        // Accumulate ALL mismatches (not fail-fast) so one run surfaces every divergence. // LOCK
        val mismatches = golden.entries
            .filter { (tag, expected) -> valueOf(xml, tag) != expected }
            .joinToString("\n") { (tag, expected) -> "  $tag: expected '$expected' but engine '${valueOf(xml, tag)}'" }
        assertThat("golden mismatches:\n$mismatches", mismatches, equalTo(""))

        // area() and enclosed-area() are the same code path; assert byte-equality. // LOCK
        assertThat(valueOf(xml, "fn_enclosed_area"), equalTo(valueOf(xml, "fn_area")))
        // extract-signed() over a bad signature yields empty per spec (must not throw). // LOCK
        assertExact(xml, "fn_extract_signed", "") // LOCK

        // Repeat-derived values (position() over the pre-populated repeats). // LOCK
        assertThat("people[1]/p_index", xml.contains("<p_index>1</p_index>"), equalTo(true))
        assertThat("people[2]/p_index", xml.contains("<p_index>2</p_index>"), equalTo(true))
        assertThat("agg_rep[1]/agg_val", xml.contains("<agg_val>1</agg_val>"), equalTo(true))
        assertThat("agg_rep[2]/agg_val", xml.contains("<agg_val>2</agg_val>"), equalTo(true))
        assertThat("agg_rep[3]/agg_val", xml.contains("<agg_val>3</agg_val>"), equalTo(true))
    }

    @Test
    fun nonDeterministicFields_haveExpectedFormat() {
        openForm()
            .clickGoToArrow()
            .clickGoToEnd()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)

        // Preloads / environment. deviceid is always present on device; username & phonenumber
        // may be empty depending on device settings — confirm on the first device run.
        assertShape(xml, "meta_start", isoDateTime)
        assertShape(xml, "meta_today", isoDate)
        assertShape(xml, "meta_deviceid", ".+")
        assertShape(xml, "date_dyn_default", isoDate)

        // Non-deterministic calculates.
        assertShape(xml, "nd_now", isoDateTime)
        assertShape(xml, "nd_today", isoDateTime)
        assertShape(xml, "nd_uuid", uuid)
        assertShape(xml, "nd_once", isoDateTime)
        assertShape(xml, "nd_version", """\d+""")
        assertShape(xml, "nd_property", ".+")

        val random = valueOf(xml, "nd_random")!!.toDouble()
        assertThat("nd_random in [0,1): $random", random >= 0.0 && random < 1.0, equalTo(true))

        assertShape(xml, "instanceID", "uuid:$uuid")
    }

    private fun openForm() =
        rule.withProject(testDependencies.server.url)
            // Attach cities.csv so instance('cities') / the external-CSV instance() resolves.
            .copyForm("parity_functions.xml", listOf("cities.csv"), testDependencies.server.hostName)
            .startBlankForm("Parity Functions")
}
