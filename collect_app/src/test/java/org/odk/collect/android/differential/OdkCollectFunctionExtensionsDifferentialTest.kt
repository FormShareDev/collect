package org.odk.collect.android.differential

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.javarosa.core.model.data.BooleanData
import org.javarosa.core.model.data.StringData
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.test.BindBuilderXFormsElement.bind
import org.javarosa.test.Scenario
import org.javarosa.test.XFormsElement
import org.javarosa.test.XFormsElement.body
import org.javarosa.test.XFormsElement.head
import org.javarosa.test.XFormsElement.html
import org.javarosa.test.XFormsElement.input
import org.javarosa.test.XFormsElement.mainInstance
import org.javarosa.test.XFormsElement.model
import org.javarosa.test.XFormsElement.t
import org.javarosa.test.XFormsElement.title
import org.junit.Test
import org.odk.collect.entities.javarosa.filter.PullDataFunctionHandler
import org.odk.collect.entities.storage.Entity
import org.odk.collect.entities.storage.InMemEntitiesRepository
import org.odk.collect.geo.javarosa.IntersectsFunctionHandler

/**
 * ODK Collect counterpart to JavaRosa's
 * `org.javarosa.differential.OdkReferenceFormDifferentialTest`.
 *
 * That reference test pins core JavaRosa's evaluation of (nearly) every ODK XPath operator and
 * function. Four constructs are deliberately left there as "probe" forms that merely assert a
 * throw, because **core JavaRosa does not support them**:
 *
 *  * `pulldata()`   — a *function* with no core handler
 *  * `intersects()` — a *function* with no core handler
 *  * `replace()`    — a *function* with no core handler (core JavaRosa only ships `regex()`)
 *  * `|` (union)    — an *operator* whose `XPathUnionExpr.eval()` is a hard stub
 *
 * ODK Collect registers its own [org.javarosa.core.model.condition.IFunctionHandler]s for two of
 * these (see [org.odk.collect.android.formmanagement.CollectFormEntryControllerFactory]), so in
 * Collect's runtime they resolve to **real values** instead of throwing. This test asserts those
 * real values, which is exactly what the JavaRosa probe forms could not do.
 *
 * Status of the four constructs verified against Collect's pinned JavaRosa
 * (`org.getodk:javarosa:5.2.0-31ab1af-SNAPSHOT`):
 *
 * | construct      | core JavaRosa            | ODK Collect                                   |
 * | -------------- | ------------------------ | --------------------------------------------- |
 * | `pulldata()`   | throws (no handler)      | **supported** → real value (entities/CSV)     |
 * | `intersects()` | throws (no handler)      | **supported** → real boolean (geo)            |
 * | `replace()`    | throws (no handler)      | still unsupported (Collect adds no handler)   |
 * | `|` (union)    | throws (eval is a stub)  | still unsupported (JavaRosa eval still a stub)|
 *
 * The last two rows are real findings: the handoff assumed Collect/Enketo enable all four, but
 * Collect registers handlers only for `pulldata` and `intersects`. `replace` has no Collect
 * handler and `XPathUnionExpr.eval()` still throws "nodeset union operation" in Collect's
 * JavaRosa, so both remain documented engine limitations — pinned here so a re-implementation is
 * compared against Collect's *actual* behavior, not an assumption.
 */
class OdkCollectFunctionExtensionsDifferentialTest {

    // -------------------------------------------------------------------------------------------
    // Supported by Collect: assert the real saved value (the probe forms could only assert a throw)
    // -------------------------------------------------------------------------------------------

    /**
     * `pulldata('cities', 'name', 'id', '2')` → `tokyo`.
     *
     * Mirrors the handoff's `probe_pulldata.xml` (`cities.csv`: paris/Paris/1, tokyo/Tokyo/2,
     * lima/Lima/3). Collect's primary `pulldata` is [PullDataFunctionHandler], which resolves a
     * named list (here a local entity list standing in for `cities.csv`; the CSV path
     * [org.odk.collect.android.dynamicpreload.handler.ExternalDataHandlerPull] is its fallback and
     * is covered end-to-end by the instrumented `DynamicPreLoadedDataPullTest`).
     */
    @Test
    fun `pulldata is supported by Collect and resolves to the real value`() {
        val entitiesRepository = InMemEntitiesRepository().apply {
            save(
                "cities",
                Entity.New("paris", "Paris", properties = listOf("id" to "1")),
                Entity.New("tokyo", "Tokyo", properties = listOf("id" to "2")),
                Entity.New("lima", "Lima", properties = listOf("id" to "3"))
            )
        }

        val scenario = Scenario.init(
            "pulldata probe",
            html(
                head(
                    title("pulldata probe"),
                    model(
                        mainInstance(
                            t(
                                "data id=\"probe_pulldata\"",
                                t("in_id"),
                                t("calculate")
                            )
                        ),
                        bind("/data/in_id").type("string"),
                        bind("/data/calculate").type("string")
                            .calculate("pulldata('cities', 'name', 'id', '2')")
                    )
                ),
                body(input("/data/in_id"))
            )
        ) { formDef ->
            FormEntryController(FormEntryModel(formDef)).also {
                it.addFunctionHandler(PullDataFunctionHandler(entitiesRepository))
            }
        }

        assertThat(scenario.answerOf<StringData>("/data/calculate").value, equalTo("tokyo"))
    }

    /**
     * `intersects(<self-crossing trace>)` → `true`; `intersects(<empty>)` → `false`.
     *
     * Mirrors the handoff's `probe_intersects.xml`. Collect's [IntersectsFunctionHandler] computes
     * whether a geo trace self-intersects, returning a real boolean.
     */
    @Test
    fun `intersects is supported by Collect and resolves to a real boolean`() {
        val scenario = Scenario.init(
            "intersects probe",
            html(
                head(
                    title("intersects probe"),
                    model(
                        mainInstance(
                            t(
                                "data id=\"probe_intersects\"",
                                t("trace"),
                                t("calculate")
                            )
                        ),
                        bind("/data/trace").type("geotrace"),
                        bind("/data/calculate").type("boolean")
                            .calculate("intersects(/data/trace)")
                    )
                ),
                body(input("/data/trace"))
            )
        ) { formDef ->
            FormEntryController(FormEntryModel(formDef)).also {
                it.addFunctionHandler(IntersectsFunctionHandler())
            }
        }

        // Empty trace: a real boolean (false), not a throw.
        assertThat(scenario.answerOf<BooleanData>("/data/calculate").value, equalTo(false))

        // A self-crossing trace: a real boolean (true).
        scenario.answer(
            "/data/trace",
            "1.0 1.0 0.0 0.0; 1.0 3.0 0.0 0.0; 2.0 3.0 0.0 0.0; 2.0 2.0 0.0 0.0; 0.0 2.0 0.0 0.0"
        )
        assertThat(scenario.answerOf<BooleanData>("/data/calculate").value, equalTo(true))
    }

    // -------------------------------------------------------------------------------------------
    // Still unsupported in Collect: pin the throw as a documented engine limitation.
    // -------------------------------------------------------------------------------------------

    /**
     * `replace()` has no handler in Collect and is not a core JavaRosa function (only `regex()`
     * is), so loading a form that calculates it fails — exactly as in core JavaRosa.
     */
    @Test
    fun `replace remains unsupported in Collect`() {
        val message = assertFormLoadFails(
            html(
                head(
                    title("replace probe"),
                    model(
                        mainInstance(
                            t(
                                "data id=\"probe_replace\"",
                                t("t", "abc123"),
                                t("calculate")
                            )
                        ),
                        bind("/data/t").type("string"),
                        bind("/data/calculate").type("string")
                            .calculate("replace(/data/t, '[0-9]', '')")
                    )
                ),
                body(input("/data/t"))
            )
        )

        // Same failure as core JavaRosa: an unhandled function. (JavaRosa's reference probe
        // asserts the identical "cannot handle function 'replace'" message.)
        assertThat(message, containsString("cannot handle function 'replace'"))
    }

    /**
     * The union operator `|` parses (into `XPathUnionExpr`) but its `eval()` still throws
     * "nodeset union operation" in Collect's JavaRosa — union is an operator, not a function, so
     * Collect cannot enable it with a function handler. It stays unsupported until JavaRosa core
     * implements it.
     */
    @Test
    fun `union operator remains unsupported in Collect`() {
        val message = assertFormLoadFails(
            html(
                head(
                    title("union probe"),
                    model(
                        mainInstance(
                            t(
                                "data id=\"probe_union\"",
                                t("a", "1"),
                                t("b", "2"),
                                t("calculate")
                            )
                        ),
                        bind("/data/a").type("int"),
                        bind("/data/b").type("int"),
                        bind("/data/calculate").type("string")
                            .calculate("count(/data/a | /data/b)")
                    )
                ),
                body(input("/data/a"), input("/data/b"))
            )
        )

        // JavaRosa parses `a | b` into XPathUnionExpr but its eval() is a hard stub that throws
        // XPathUnsupportedException("nodeset union operation"), surfaced as "unsupported construct
        // [<expr>]". (JavaRosa's reference probe asserts the identical "unsupported construct".)
        assertThat(message, containsString("unsupported construct"))
    }

    // ---- helpers ------------------------------------------------------------------------------

    /**
     * Loads [form] (no extra handlers) and asserts the load fails because of an unsupported
     * construct, returning the concatenated message of the whole cause chain so the caller can
     * assert on the specific reason.
     */
    private fun assertFormLoadFails(form: XFormsElement): String {
        val thrown = try {
            Scenario.init("unsupported construct probe", form)
            null
        } catch (e: Exception) {
            e
        }

        assertThat(
            "expected the form to be rejected as an unsupported construct, but it loaded",
            thrown != null,
            equalTo(true)
        )
        return fullMessage(thrown!!)
    }

    private fun fullMessage(throwable: Throwable): String {
        val builder = StringBuilder()
        var cause: Throwable? = throwable
        while (cause != null) {
            cause.message?.let { builder.append(it).append('\n') }
            cause = cause.cause
        }
        return builder.toString()
    }
}
