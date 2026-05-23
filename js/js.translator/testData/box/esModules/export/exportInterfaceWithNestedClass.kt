// RUN_PLAIN_BOX_FUNCTION
// ES_MODULES

// MODULE: export_interface_with_nested_class
// FILE: lib.kt

@JsExport
interface I {
    class Nested(val value: String) {
        fun box() = value
    }
}

// FILE: main.mjs
// ENTRY_ES_MODULE

import { I } from "./exportInterfaceWithNestedClass-export_interface_with_nested_class_v5.mjs"

export function box() {
    const nested = new I.Nested("OK")

    if (nested.value !== "OK") return "Fail: nested class property was not exported"
    if (nested.box() !== "OK") return "Fail: nested class function was not exported"

    return "OK"
}
