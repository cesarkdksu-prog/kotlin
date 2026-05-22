// TARGET_BACKEND: JS_IR
// ONLY_IR_DCE

// FILE: main.kt

var constructorCalls = 0

@JsExport
value class ExportedValue(val value: String) {
    init {
        constructorCalls++
    }
}

external fun externalValue(): ExportedValue = definedExternally

fun box(): String {
    val value = externalValue()
    if (constructorCalls != 0) return "fail"
    return value.value
}

// FILE: external.js
function externalValue() {
    return "OK";
}
