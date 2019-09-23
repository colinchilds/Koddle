package me.koddle.annotations

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Body(val key: String = "")