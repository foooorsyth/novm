package com.forsyth.novm

import kotlinx.coroutines.CoroutineScope

interface RetainedScopeOwner {
    val retainedScope: CoroutineScope
}