package com.forsyth.novm

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo


const val COMPONENT_ACTIVITY_QUALIFIED_NAME = "androidx.appcompat.app.AppCompatActivity"

class NoVMProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val stateSymbols = resolver.getSymbolsWithAnnotation(State::class.qualifiedName!!, false).toList()
        val activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>> = mutableMapOf()
        stateSymbols.forEach { stateNode ->
            val propertyDecl = stateNode as? KSPropertyDeclaration
            if (propertyDecl == null) {
                logger.error("@State must annotate a property declaration, skipping...")
                return@forEach
            }
            if (propertyDecl.parentDeclaration == null) {
                logger.error("@State cannot be declared at top level, skipping ${propertyDecl.simpleName}")
                return@forEach
            }
            val parentClassDecl = propertyDecl.parentDeclaration as? KSClassDeclaration
            if (parentClassDecl == null) {
                logger.error("@State must be declared within a ComponentActivity, skipping ${propertyDecl.simpleName}")
                return@forEach
            }
            val isSubclassOfComponentActivity = isSubclassOf(parentClassDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)
            if (!isSubclassOfComponentActivity) {
                logger.error("@State must be declared within a ComponentActivity, skipping ${propertyDecl.simpleName}")
                return@forEach
            }
            if (parentClassDecl.isLocal()) {
                logger.error("@State cannot be contained by local declaration, skipping $${propertyDecl.simpleName}")
                return@forEach
            }
            if (!activityToStateMap.contains(parentClassDecl.qualifiedName!!.asString())) {
                activityToStateMap[parentClassDecl.qualifiedName!!.asString()] = mutableListOf(propertyDecl)
            } else {
                activityToStateMap[parentClassDecl.qualifiedName!!.asString()]!!.add(propertyDecl)
            }
        }
        if (activityToStateMap.isNotEmpty()) {
            generateCode(resolver, activityToStateMap)
        }
        return emptyList()
    }

    private fun generateCode(resolver: Resolver, activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) {
        val activityContainingFiles = activityToStateMap.keys.mapNotNull { activityFullyQualified ->
            resolver.getClassDeclarationByName(activityFullyQualified)?.containingFile
        }
        val fileName = "NoVMGenerated"
        val packageName = resolver.getClassDeclarationByName(activityToStateMap.keys.first())!!.packageName.asString() // TODO improve, get app package name from manifest
        val stateHolderTypeSpecs = generateStateHoldersForActivities(resolver, activityToStateMap)
        val fileSpec = FileSpec.builder(packageName, fileName)
            .addTypes(stateHolderTypeSpecs)
            .build()
        fileSpec.writeTo(codeGenerator, Dependencies(true, *activityContainingFiles.toTypedArray()))
    }

    @OptIn(KspExperimental::class)
    private fun generateStateHoldersForActivities(resolver: Resolver,
                                                  activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) : List<TypeSpec> {
        val ret = mutableListOf<TypeSpec>()
        activityToStateMap.forEach { entry ->
            val activityName = resolver.getClassDeclarationByName(entry.key)!!.simpleName.asString()
            val typeSpecBuilder = TypeSpec.classBuilder(activityName + "State") // TODO dangerous, check for collisions
            entry.value.forEach { propDecl ->
                val stateAnn = propDecl.getAnnotationsByType(State::class).toList().first()
                // only put into the state holder if we're retaining across config change
                if (stateAnn.retainAcross == StateLossEvent.CONFIGURATION_CHANGE) {
                    propDecl.type.toTypeName().isNullable
                    val resolvedType = propDecl.type.resolve()
                    val propBuilder = PropertySpec.builder(propDecl.simpleName.asString(), resolvedType.toTypeName())
                        .mutable(true)

                    if (resolvedType.isMarkedNullable) {
                        when (resolvedType.makeNotNullable()) {
                            resolver.builtIns.booleanType,
                            resolver.builtIns.stringType,
                            resolver.builtIns.intType,
                            resolver.builtIns.longType,
                            resolver.builtIns.floatType,
                            resolver.builtIns.doubleType -> {
                                propBuilder.initializer("%L", null)
                            }
                            else -> {
                                logger.error("unsupported type")
                            }
                        }
                    } else {
                        when (resolvedType) {
                            resolver.builtIns.booleanType, -> {
                                propBuilder.initializer("%L", false) // TODO initial value not supported by KSP? https://github.com/google/ksp/issues/642
                            }
                            resolver.builtIns.stringType -> {
                                propBuilder.initializer("%L", "")
                            }
                            resolver.builtIns.intType -> {
                                propBuilder.initializer("%L", 0)
                            }
                            resolver.builtIns.longType -> {
                                propBuilder.initializer("%L", 0L)
                            }
                            resolver.builtIns.floatType -> {
                                propBuilder.initializer("%L", 0.0F)
                            }
                            resolver.builtIns.doubleType -> {
                                propBuilder.initializer("%L", 0.0)
                            }
                            else -> {
                                logger.error("unsupported type")
                            }
                        }
                    }

                    typeSpecBuilder.addProperty(
                        propBuilder.build()
                    )
                }
            }
            ret.add(typeSpecBuilder.build())
        }
        return ret
    }

    private fun isSubclassOf(clazz: KSClassDeclaration, qualifiedNameOfSuper: String) : Boolean {
        val superTypeClassDecls = clazz.superTypes.toList()
            .mapNotNull { ksTypeReference ->  ksTypeReference.resolve().declaration as? KSClassDeclaration }
        if (superTypeClassDecls.isEmpty()) {
            return false
        }
        if (superTypeClassDecls.any { ksClassDeclaration -> ksClassDeclaration.qualifiedName?.asString() == qualifiedNameOfSuper}) {
            return true
        }
        // recursively try supertypes
        return superTypeClassDecls
            .map { classDecl -> isSubclassOf(classDecl, qualifiedNameOfSuper) }
            .reduce { acc, b -> acc || b }
    }

    override fun finish() {
        logger.info("finished")
    }

    override fun onError() {
        logger.info("onError")
    }
}