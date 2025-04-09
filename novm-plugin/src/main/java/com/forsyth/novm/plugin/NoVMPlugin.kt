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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.OutputStream
import java.io.File
import java.io.FileOutputStream

class NoVMPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("NoVM plugin is applied to " + target.name)
        val androidComponents = target.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants /*TODO use selector to limit variants */ { variant ->
            println("found variant: ${variant.name}")
            variant.instrumentation.transformClassesWith(
                RemoveFinalVisitorFactory::class.java,
                InstrumentationScope.ALL
            ) {
                it.writeToStdout.set(true)
                it.outputDirectory.set(target.layout.buildDirectory.dir("tmp/instrumented")) // TODO how is this known?
            }
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
        }
    }
    interface RemoveFinalParams : InstrumentationParameters {
        @get:Input
        val writeToStdout: Property<Boolean>
        @get:OutputDirectory
        val outputDirectory: DirectoryProperty
    }

    abstract class RemoveFinalVisitorFactory :
        AsmClassVisitorFactory<RemoveFinalParams> {

        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            /*return if (parameters.get().writeToStdout.get()) {
                TraceClassVisitor(nextClassVisitor, PrintWriter(System.out))
            } else {
                TraceClassVisitor(nextClassVisitor, PrintWriter(File("trace_out")))
            }
             */
            val outputDir = parameters.get().outputDirectory.asFile.get()
            val outputFile = File(outputDir, "${classContext.currentClassData.className.replace(".", "/")}.class")
            outputFile.parentFile.mkdirs()
            val outputStream: OutputStream = FileOutputStream(outputFile)
            val cw = ClassWriter(0) // TODO not using nextClassVisitor here breaks transformation chain, blocks other plugins?
            //val visitor = RemoveFinalVisitor(Opcodes.ASM9, TraceClassVisitor(nextClassVisitor, PrintWriter(System.out)))
            val visitor = RemoveFinalVisitor(Opcodes.ASM9, nextClassVisitor)
            visitor.cw = cw
            visitor.outputStream = outputStream
            return visitor
        }

        override fun isInstrumentable(classData: ClassData): Boolean {
            return classData.className == "com.forsyth.novm.DummyFinal"
            //return classData.className == "androidx.navigation.NavBackStackEntry" // TODO doesn't work investigate
        }
    }

    class RemoveFinalVisitor : ClassVisitor {

        constructor(api: Int) : super(api)

        constructor(api: Int, next: ClassVisitor) : super(api, next)

        var outputStream: OutputStream? = null
        var cw: ClassWriter? = null

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            val newAccess = access and Opcodes.ACC_FINAL.inv()
            super.visit(version, newAccess, name, signature, superName, interfaces)
            this.cv.visit(version, newAccess, name, signature, superName, interfaces)
            this.cw?.visit(version, newAccess, name, signature, superName, interfaces)
        }

        override fun visitEnd() {
            super.visitEnd()
            this.cv.visitEnd()
            this.cw?.let { classWriter ->
                classWriter.visitEnd()
                val bytes = classWriter.toByteArray()
                try {
                    outputStream?.write(bytes)
                } catch (ex: Exception) {
                    println("caught ex writing new class: $ex")
                }
                finally {
                    outputStream?.close()
                }
            }
        }
    }
}