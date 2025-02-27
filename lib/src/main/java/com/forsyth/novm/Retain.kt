package com.forsyth.novm

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class Retain(
     vararg val across: StateDestroyingEvent
)