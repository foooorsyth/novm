package com.forsyth.novm

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class Retain (
     vararg val across: StateDestroyingEvent,
)