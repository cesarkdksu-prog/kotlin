// RUN_PIPELINE_TILL: FRONTEND
// OPT_IN: kotlin.js.ExperimentalJsExport

@file:JsExport

interface ExportedInterfaceWithNestedClasses {
    class Nested

    value class NestedValue(val value: Int)
}
