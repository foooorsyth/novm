package com.forsyth.novm

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class Retain (
     vararg val across: StateDestroyingEvent,
)