package com.forsyth.novm

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class State(
    val retainAcross: StateLossEvent
)