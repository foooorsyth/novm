package com.forsyth.novm.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class NoVMPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("NoVM plugin is applied to " + target.name);
    }
}