package com.forsyth.novm

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.Locale


const val COMPONENT_ACTIVITY_QUALIFIED_NAME = "androidx.activity.ComponentActivity"

class NoVMProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    var pass = 1
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val retainSymbols = resolver.getSymbolsWithAnnotation(Retain::class.qualifiedName!!, false).toList()
        // TODO get package, statesavingactivity name from ksp options
        val isStateSavingActivityValid = resolver.getClassDeclarationByName("com.forsyth.novm.StateSavingActivity")?.validate() == true
        val recheck = mutableListOf<KSAnnotated>()
        val activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>> = mutableMapOf()
        ensureSupported(
            retainSymbols,
            isStateSavingActivityValid,
            recheck,
            activityToStateMap
        )
        if (activityToStateMap.isNotEmpty() && !isStateSavingActivityValid ) {
            generateCode(resolver, activityToStateMap)
        }
        //logger.warn("pass: $pass")
        //logger.warn("sizeof revalidate list: ${recheck.size}")
        pass += 1
        return recheck
    }

    private fun ensureSupported(retainSymbols: List<KSAnnotated>,
                                isStateSavingActivityValid: Boolean,
                                recheck: MutableList<KSAnnotated>,
                                activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) {
        retainSymbols.forEach { retainNode: KSAnnotated ->
            val propertyDecl = retainNode as? KSPropertyDeclaration
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
                if (!isStateSavingActivityValid) {
                    //logger.warn("@State must be declared within a ComponentActivity, checking ${propertyDecl.simpleName} again after codegen...")
                    recheck.add(retainNode)
                } else {
                    logger.error("@State must be declared within a ComponentActivity: ${propertyDecl.simpleName}")
                    return
                }
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
    }

    private fun generateCode(resolver: Resolver, activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) {
        val activityContainingFiles = activityToStateMap.keys.mapNotNull { activityFullyQualified ->
            resolver.getClassDeclarationByName(activityFullyQualified)?.containingFile
        }
        val noVMDynamicFileName = "NoVMDynamic"
        val packageName = resolver.getClassDeclarationByName(activityToStateMap.keys.first())!!.packageName.asString() // TODO improve, get app package name from manifest
        val stateHoldersForActivities = generateStateHoldersForActivities(resolver, activityToStateMap)
        val topLevelStateHolder = generateTopLevelStateHolder(packageName, stateHoldersForActivities)
        val stateSaver = generateStateSaver(packageName,
            resolver,
            activityToStateMap,
            topLevelStateHolder,
            stateHoldersForActivities
            )

        FileSpec.builder(packageName, noVMDynamicFileName)
            .addImport("android.os", "Bundle")
            .addImport("androidx.annotation", "CallSuper")
            .addImport("androidx.appcompat.app", "AppCompatActivity")
            .addTypes(stateHoldersForActivities.values)
            .addType(topLevelStateHolder)
            .addType(stateSaver)
            // StateSaver interface
            .addType(
                TypeSpec.interfaceBuilder("StateSaver")
                    .addFunction(
                        generateSaveStateConfigChangeSignature(packageName)
                            .addModifiers(KModifier.ABSTRACT)
                            .build()
                    )
                    .addFunction(
                        generateRestoreStateConfigChangeSignature(packageName)
                            .addModifiers(KModifier.ABSTRACT)
                            .build()
                    )
                    .addFunction(
                        generateSaveStateBundleSignature()
                            .addModifiers(KModifier.ABSTRACT)
                            .build()
                    )
                    .addFunction(
                        generateRestoreStateBundleSignature()
                            .addModifiers(KModifier.ABSTRACT)
                            .build()
                    )
                    .build()
            )
            .addFunction(
                FunSpec.builder("provideStateSaver")
                    .returns(ClassName(packageName, "StateSaver"))
                    .addStatement("%L", "return GeneratedStateSaver()")
                    .build()
            )
            .build()
            .writeTo(codeGenerator, Dependencies(true, *activityContainingFiles.toTypedArray()))

        generateStaticFile(packageName).writeTo(codeGenerator, Dependencies(true))
    }

    private fun generateStaticFile(packageName: String) : FileSpec {
        return FileSpec.builder(packageName, "NoVMStatic")
            .addType(
                TypeSpec.classBuilder("StateSavingActivity")
                    .addModifiers(KModifier.OPEN)
                    .superclass(ClassName("androidx.appcompat.app", "AppCompatActivity"))
                    .addProperty(
                        PropertySpec.builder("stateSaver", ClassName(packageName, "StateSaver"))
                            .initializer("%L", "provideStateSaver()")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("onCreate")
                            .addModifiers(KModifier.OVERRIDE)
                            .addAnnotation(ClassName("androidx.annotation", "CallSuper"))
                            .addParameter(
                                ParameterSpec
                                    .builder("savedInstanceState", ClassName("android.os", "Bundle").copy(nullable = true))
                                    .build()
                            )
                            .addStatement("%L", "super.onCreate(savedInstanceState)")
                            .addStatement("%L", "@Suppress(\"DEPRECATION\")")
                            .addCode(
                                "(lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->\n" +
                                        "  stateSaver.restoreStateConfigChange(this, retainedState)\n" +
                                        "}\n" +
                                        "if (savedInstanceState != null) {\n" +
                                        "  stateSaver.restoreStateBundle(this, savedInstanceState)\n" +
                                        "}"
                            )
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("onSaveInstanceState")
                            .addModifiers(KModifier.OVERRIDE)
                            .addAnnotation(ClassName("androidx.annotation", "CallSuper"))
                            .addParameter(
                                ParameterSpec
                                    .builder("outState", ClassName("android.os", "Bundle"))
                                    .build()
                            )
                            .addStatement("%L", "stateSaver.saveStateBundle(this, outState)")
                            .addStatement("%L", "super.onSaveInstanceState(outState)")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("onRetainCustomNonConfigurationInstance")
                            .addModifiers(KModifier.OVERRIDE)
                            .addAnnotation(ClassName("androidx.annotation", "CallSuper"))
                            .addAnnotation(
                                AnnotationSpec
                                    .builder(ClassName("kotlin", "Suppress"))
                                    .addMember("%L", "\"OVERRIDE_DEPRECATION\"")
                                    .build()
                            )
                            .addStatement("%L", "return stateSaver.saveStateConfigChange(this)")
                            .returns(ClassName("kotlin", "Any").copy(nullable = true))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun generateStateSaver(packageName: String,
                                   resolver: Resolver,
                                   activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
                                   topLevelStateHolder: TypeSpec,
                                   stateHoldersForActivities: MutableMap<String, TypeSpec>,
                                   ) : TypeSpec {
        val ssbRet = generateSaveStateBundle(
                    packageName,
                    resolver,
                    activityToStateMap,
                )
        val builder = TypeSpec.classBuilder("GeneratedStateSaver") //dangerous, collisions // TODO const string
            .addSuperinterface(ClassName(packageName, "StateSaver")) // TODO const string
            .addFunction(
                generateSaveStateConfigChange(
                    packageName,
                    resolver,
                    activityToStateMap,
                    topLevelStateHolder,
                    stateHoldersForActivities
                )
            )
            .addFunction(
                generateRestoreStateConfigChange(
                    packageName,
                    resolver,
                    activityToStateMap,
                    topLevelStateHolder,
                    stateHoldersForActivities
                )
            )
            .addFunction(
                ssbRet.funSpec
            )
            .addFunction(
                generateRestoreStateBundle(
                    packageName,
                    resolver,
                    activityToStateMap,
                )
            )
        val companionObjectBuilder = TypeSpec.companionObjectBuilder()
        ssbRet.bundleKeyValuePairs.forEach { entry ->
            companionObjectBuilder.addProperty(
                PropertySpec.builder(entry.key, ClassName("kotlin", "String"))
                    .addModifiers(KModifier.CONST)
                    .mutable(false)
                    .initializer("\"%L\"", entry.value)
                    .build()
            )
        }
        builder.addType(
            companionObjectBuilder.build()
        )
        return builder.build()
    }

    data class SSBRet(
        val funSpec: FunSpec,
        val bundleKeyValuePairs: Map<String, String>
    )

    private fun generateSaveStateBundleSignature() : FunSpec.Builder {
        return FunSpec.builder("saveStateBundle")
            .addParameter(
                ParameterSpec.builder(
                    "activity",
                    ClassName("androidx.activity", "ComponentActivity")
                )
                    .build()
            )
            .addParameter(
                ParameterSpec.builder(
                    "bundle",
                    ClassName("android.os", "Bundle")
                )
                    .build()
            )
    }

    @OptIn(KspExperimental::class)
    private fun generateSaveStateBundle(
        packageName: String,
        resolver: Resolver,
        activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>): SSBRet {
        val bundleKeyValuePairs = mutableMapOf<String, String>()
        val funBuilder = generateSaveStateBundleSignature()
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("when (activity) {")
        activityToStateMap.forEach { activityToStateEntry ->
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            activityToStateEntry.value
                .filter { ksPropertyDeclaration ->
                    ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList().first().across.contains(StateDestroyingEvent.PROCESS_DEATH)
                }
                .forEach filteredForEach@ { ksPropertyDeclaration ->
                    val bundleFunPostfixRet = getBundleFunPostfix(resolver, ksPropertyDeclaration)
                    if (bundleFunPostfixRet.category == BundleFunPostfixCategory.NOT_APPLICABLE) {
                        logger.error("State ${ksPropertyDeclaration.simpleName.asString()} is marked to be retained across PROCESS_DEATH but is not a type supported by Bundle")
                        return@filteredForEach
                    }
                    // TODO check bundlefunpostfix BEFORE codegen starts
                    val kvp = generateBundleKeyValuePair(ksPropertyDeclaration)
                    bundleKeyValuePairs[kvp.first] = kvp.second
                    funBuilder.addStatement("bundle.put${bundleFunPostfixRet.postfix}(${kvp.first}, activity.${ksPropertyDeclaration.simpleName.asString()})")
            }
            funBuilder.endControlFlow() // close is
        }
        funBuilder.endControlFlow() // close when

        return SSBRet(
            funSpec = funBuilder.build(),
            bundleKeyValuePairs = bundleKeyValuePairs
        )
    }

    private fun generateRestoreStateBundleSignature() : FunSpec.Builder {
        return FunSpec.builder("restoreStateBundle")
            .addParameter(
                ParameterSpec.builder(
                    "activity",
                    ClassName("androidx.activity", "ComponentActivity")
                )
                    .build()
            )
            .addParameter(
                ParameterSpec.builder(
                    "bundle",
                    ClassName("android.os", "Bundle")
                )
                    .build()
            )
    }

    @OptIn(KspExperimental::class)
    private fun generateRestoreStateBundle(
        packageName: String,
        resolver: Resolver,
        activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>
        ): FunSpec {
        val funBuilder = generateRestoreStateBundleSignature()
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("when (activity) {")
        activityToStateMap.forEach { activityToStateEntry ->
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            activityToStateEntry.value
                .filter { ksPropertyDeclaration ->
                    ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList().first().across.contains(StateDestroyingEvent.PROCESS_DEATH)
                }
                .forEach filteredForEach@ { ksPropertyDeclaration ->
                    val resolvedType = ksPropertyDeclaration.type.resolve()
                    val key = generateBundleKeyValuePair(ksPropertyDeclaration).first
                    val bundleFunPostfixRet = getBundleFunPostfix(resolver, resolvedType)

                    when (bundleFunPostfixRet.category) {
                        BundleFunPostfixCategory.NON_NULL_PRIMITIVE -> {
                            // primitives always have default value
                            funBuilder.addStatement("activity.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key)")
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.NULLABLE_KNOWN_TYPE -> {
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement("activity.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key)")
                            } else {
                                funBuilder.addStatement("bundle.get${bundleFunPostfixRet.postfix}($key)?.let { activity.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.SUBCLASS_SERIALIZABLE_OR_PARCELABLE -> {
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement("activity.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.toClassName()}::class.java)")
                            } else {
                                funBuilder.addStatement("bundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.toClassName()}::class.java)?.let { activity.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.NOT_APPLICABLE -> {
                            logger.error("State ${ksPropertyDeclaration.simpleName.asString()} is marked to be retained across PROCESS_DEATH but is not a type supported by Bundle")
                            return@filteredForEach
                        }
                    }

                }
            funBuilder.endControlFlow() // close is
        }
        funBuilder.endControlFlow() // close when
        return funBuilder.build()
    }

    private fun generateBundleKeyValuePair(ksPropertyDeclaration: KSPropertyDeclaration) : Pair<String, String> {
        val value = "${ksPropertyDeclaration.parentDeclaration!!.simpleName.asString()}_${ksPropertyDeclaration.simpleName.asString()}"
        val key = "KEY_${value.uppercase()}"
        return key to value
    }

    private fun generateRestoreStateConfigChangeSignature(packageName: String) : FunSpec.Builder {
        return FunSpec.builder("restoreStateConfigChange")
            .addParameter(
                ParameterSpec.builder(
                    "activity",
                    ClassName("androidx.activity", "ComponentActivity")
                )
                    .build()
            )
            .addParameter(
                ParameterSpec.builder(
                    "stateHolder",
                    ClassName(packageName, "StateHolder")
                )
                    .build()
            )
    }

    @OptIn(KspExperimental::class)
    private fun generateRestoreStateConfigChange(
        packageName: String,
        resolver: Resolver,
        activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        topLevelStateHolder: TypeSpec,
        stateHoldersForActivities: MutableMap<String, TypeSpec>) : FunSpec {
        // TODO use topLevelStateHolder typeSpec and stateHoldersForActivities
        // TODO instead of manually recreating names below
        val funBuilder = generateRestoreStateConfigChangeSignature(packageName)
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("when (activity) {")


        activityToStateMap.forEach { activityToStateEntry ->
            val classDeclOfActivity = resolver.getClassDeclarationByName(activityToStateEntry.key)!!
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            val activityStateHolderFieldName = "${lowercaseFirstLetter(classDeclOfActivity.simpleName.asString())}State"
            funBuilder.beginControlFlow("if (stateHolder.$activityStateHolderFieldName != null) {")
            activityToStateEntry.value.filter { ksPropertyDeclaration ->
               ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList().first().across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)
            }
            .forEach { ksPropertyDeclaration ->
                // need to find equivalent field in state holder for this activity
                // need to check for null in stateholder prop
                val typeSpecOfStateHolder = stateHoldersForActivities[activityToStateEntry.key]!!
                val equivPropInStateHolder = typeSpecOfStateHolder.propertySpecs.first { it.name == ksPropertyDeclaration.simpleName.asString() }
                if (equivPropInStateHolder.type.isNullable && !ksPropertyDeclaration.type.resolve().isMarkedNullable) {
                    funBuilder.beginControlFlow("if (stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()} != null) {")
                    funBuilder.addStatement("activity.${ksPropertyDeclaration.simpleName.asString()} = stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()}!!")
                    funBuilder.endControlFlow()
                }
                else {
                    funBuilder.addStatement("activity.${ksPropertyDeclaration.simpleName.asString()} = stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()}")
                }
            }
            funBuilder.endControlFlow() // close if
            funBuilder.endControlFlow() // close is
        }
        funBuilder.endControlFlow() // close when
        return funBuilder.build()
    }

    private fun generateSaveStateConfigChangeSignature(packageName: String) : FunSpec.Builder {
        return FunSpec.builder("saveStateConfigChange")
            .addParameter(
                ParameterSpec.builder(
                    "activity",
                    ClassName("androidx.activity", "ComponentActivity")
                )
                    .build()
            )
            .returns(ClassName(packageName, "StateHolder"))
    }

    @OptIn(KspExperimental::class)
    private fun generateSaveStateConfigChange(
        packageName: String,
           resolver: Resolver,
           activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
           topLevelStateHolder: TypeSpec,
           stateHoldersForActivities: MutableMap<String, TypeSpec>,
        ) : FunSpec {
        // TODO use topLevelStateHolder typeSpec and stateHoldersForActivities
        // TODO instead of manually recreating names below
        val funBuilder = generateSaveStateConfigChangeSignature(packageName)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("val stateHolder = StateHolder()")
            .beginControlFlow("when (activity) {")

        activityToStateMap.forEach { entry ->
            val classDeclOfActivity = resolver.getClassDeclarationByName(entry.key)!!
            funBuilder.beginControlFlow("is ${entry.key} -> {")
            val activityStateHolderFieldName = "${lowercaseFirstLetter(classDeclOfActivity.simpleName.asString())}State"
            funBuilder.addStatement(
                "stateHolder.$activityStateHolderFieldName = ${classDeclOfActivity.simpleName.asString()}State()"
            )
            entry.value.filter { ksPropertyDeclaration ->
                ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList().first().across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)
            }
            .forEach { propertyDecl ->
                // during save, nullability doesn't matter
                funBuilder.addStatement(
                    "stateHolder.$activityStateHolderFieldName!!.${propertyDecl.simpleName.asString()} = activity.${propertyDecl.simpleName.asString()}"
                )
            }
            funBuilder.endControlFlow() // end is
        }

        funBuilder.endControlFlow() // close when
            .addStatement("return stateHolder")
        return funBuilder.build()
    }

    private fun generateTopLevelStateHolder(packageName: String, stateHoldersForActivities: MutableMap<String, TypeSpec>) : TypeSpec {
        val builder = TypeSpec.classBuilder("StateHolder")
        stateHoldersForActivities.forEach { entry ->
            builder.addProperty(
                PropertySpec.builder(
                    lowercaseFirstLetter(entry.value.name!!),
                    ClassName(packageName, entry.value.name!!).copy(nullable = true)
                )
                    .mutable(true)
                    .initializer("%L", null)
                    .build()
            )

        }
        return builder.build()
    }

    private fun lowercaseFirstLetter(str: String) = str.replaceFirstChar { it.lowercase(Locale.getDefault()) }

    @OptIn(KspExperimental::class)
    private fun generateStateHoldersForActivities(resolver: Resolver,
                                                  activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) : MutableMap<String, TypeSpec> {
        val ret = mutableMapOf<String, TypeSpec>()
        activityToStateMap.forEach { entry ->
            val activityName = resolver.getClassDeclarationByName(entry.key)!!.simpleName.asString()
            val typeSpecBuilder = TypeSpec.classBuilder(activityName + "State") // TODO dangerous, check for collisions
            entry.value.forEach { propDecl ->
                val retainAnn = propDecl.getAnnotationsByType(Retain::class).toList().first()
                // only put into the state holder if we're retaining across config change
                if (retainAnn.across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)) {
                    val resolvedType = propDecl.type.resolve()
                    val canUseDefaultVal = isBuiltInWithDefaultValue(resolver, resolvedType)
                    val propBuilder = PropertySpec.builder(
                            propDecl.simpleName.asString(),
                            if (canUseDefaultVal) resolvedType.toTypeName() else resolvedType.makeNullable().toTypeName()
                    )
                        .mutable(true)

                    if (!canUseDefaultVal) {
                        propBuilder.initializer("%L", null)
                    } else {
                        when (resolvedType) {
                            resolver.builtIns.booleanType, -> {
                                propBuilder.initializer("%L", false) // TODO initial value not supported by KSP? https://github.com/google/ksp/issues/642
                            }
                            resolver.builtIns.stringType -> {
                                propBuilder.initializer("%L", "")
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
                            resolver.builtIns.intType,
                            resolver.builtIns.byteType,
                            resolver.builtIns.shortType,
                            resolver.builtIns.charType,
                            resolver.builtIns.numberType -> {
                                propBuilder.initializer("%L", 0)
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
            ret[entry.key] =  typeSpecBuilder.build()
        }
        return ret
    }
}