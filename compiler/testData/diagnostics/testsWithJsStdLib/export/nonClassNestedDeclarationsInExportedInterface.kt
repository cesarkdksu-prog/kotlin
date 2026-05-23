// RUN_PIPELINE_TILL: FRONTEND
// OPT_IN: kotlin.js.ExperimentalJsExport
// RENDER_DIAGNOSTIC_ARGUMENTS
// LANGUAGE: +JsAllowExportingAnnotationClasses

@JsExport
interface NonClassNestedDeclarations {
    interface <!WRONG_EXPORTED_DECLARATION("nested/inner declaration inside exported interface")!>NestedI<!>

    <!WRONG_EXPORTED_DECLARATION("nested/inner declaration inside exported interface")!>object NestedObject<!>

    enum class <!WRONG_EXPORTED_DECLARATION("nested/inner declaration inside exported interface")!>NestedEnum<!> {
        A
    }

    annotation class <!WRONG_EXPORTED_DECLARATION("nested/inner declaration inside exported interface")!>NestedAnnotation<!>
}

@JsExport
sealed interface SealedNonClassNestedDeclarations {
    <!WRONG_EXPORTED_DECLARATION("nested/inner declaration inside exported interface")!>object NestedObject<!> : SealedNonClassNestedDeclarations
}
