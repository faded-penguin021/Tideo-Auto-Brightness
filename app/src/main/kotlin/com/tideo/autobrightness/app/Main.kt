package com.tideo.autobrightness.app

fun main() {
    val module = AppModule()
    val result = module.evaluateAndApplyBrightnessUseCase.run()
    println("[app] Evaluated brightness result=$result")
}
