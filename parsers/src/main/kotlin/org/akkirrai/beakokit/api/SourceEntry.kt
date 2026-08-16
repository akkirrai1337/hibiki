package org.akkirrai.beakokit.api

/** Marks a built-in source for compile-time catalog generation. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SourceEntry(
    val id: String,
    val order: Int,
)
