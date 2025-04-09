package com.forsyth.novm.plugin

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.PrintWriter

class NoVMPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("NoVM plugin is applied to " + target.name)
        val androidComponents = target.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            println("found variant: ${variant.name}")
            variant.instrumentation.transformClassesWith(
                RemoveFinalVisitor::class.java,
                InstrumentationScope.ALL
            ) {
                it.writeToStdout.set(true)
            }
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
        }
    }
    interface RemoveFinalParams : InstrumentationParameters {
        @get:Input
        val writeToStdout: Property<Boolean>
    }

    abstract class RemoveFinalVisitor :
        AsmClassVisitorFactory<RemoveFinalParams> {

        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return if (parameters.get().writeToStdout.get()) {
                TraceClassVisitor(nextClassVisitor, PrintWriter(System.out))
            } else {
                TraceClassVisitor(nextClassVisitor, PrintWriter(File("trace_out")))
            }
        }

        override fun isInstrumentable(classData: ClassData): Boolean {
            return classData.interfaces.contains("com.forsyth.novm.NonConfigStateRegistryOwner")
        }
    }
}