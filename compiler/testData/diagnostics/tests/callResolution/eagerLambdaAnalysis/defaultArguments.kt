// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +EagerLambdaAnalysis, +CallCompletionRefinementsFor25, +UnitConversionsOnArbitraryExpressions, +InferThrowableTypeParameterToUpperBound

fun f(block: () -> String, x: Int = 0) = 1
fun f(block: () -> Unit) = "(2)"

fun test() {
   val result = f { "OK" }
   <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.String")!>result<!>
}

/* GENERATED_FIR_TAGS: functionDeclaration, functionalType, integerLiteral, lambdaLiteral, localProperty,
propertyDeclaration, stringLiteral */
