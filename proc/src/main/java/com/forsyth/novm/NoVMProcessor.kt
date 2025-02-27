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
        val stateHoldersForActivities = generateStateHoldersForActivities(resolver, activityToStateMap)
        val topLevelStateHolder = generateTopLevelStateHolder(packageName, stateHoldersForActivities)
        val stateSaver = generateStateSaver(packageName,
            resolver,
            activityToStateMap,
            topLevelStateHolder,
            stateHoldersForActivities
            )

        FileSpec.builder(packageName, fileName)
            .addImport("android.os", "Bundle")
            .addImport("androidx.annotation", "CallSuper")
            .addImport("androidx.appcompat.app", "AppCompatActivity")
            .addTypes(stateHoldersForActivities.values)
            .addType(topLevelStateHolder)
            .addType(stateSaver)
            .build()
            .writeTo(codeGenerator, Dependencies(true, *activityContainingFiles.toTypedArray()))
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
        val builder = TypeSpec.classBuilder("StateSaver") //dangerous, collisions
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

    @OptIn(KspExperimental::class)
    private fun generateSaveStateBundle(
        packageName: String,
        resolver: Resolver,
        activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>): SSBRet {
        val bundleKeyValuePairs = mutableMapOf<String, String>()
        val funBuilder = FunSpec.builder("saveStateBundle")
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
            .beginControlFlow("when (activity) {")
        activityToStateMap.forEach { activityToStateEntry ->
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            activityToStateEntry.value
                .filter { ksPropertyDeclaration ->
                    ksPropertyDeclaration.getAnnotationsByType(State::class).toList().first().retainAcross == StateDestroyingEvent.PROCESS_DEATH
                }
                .forEach filteredForEach@ { ksPropertyDeclaration ->
                    val bundleFunPostfixRet = getBundleFunPostfix(resolver, ksPropertyDeclaration)
                    if (bundleFunPostfixRet.postfix == null) {
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

    @OptIn(KspExperimental::class)
    private fun generateRestoreStateBundle(
        packageName: String,
        resolver: Resolver,
        activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>
        ): FunSpec {
        val funBuilder = FunSpec.builder("restoreStateBundle")
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
            .beginControlFlow("when (activity) {")
        activityToStateMap.forEach { activityToStateEntry ->
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            activityToStateEntry.value
                .filter { ksPropertyDeclaration ->
                    ksPropertyDeclaration.getAnnotationsByType(State::class).toList().first().retainAcross == StateDestroyingEvent.PROCESS_DEATH
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

    @OptIn(KspExperimental::class)
    private fun generateRestoreStateConfigChange(
        packageName: String,
        resolver: Resolver,
        activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        topLevelStateHolder: TypeSpec,
        stateHoldersForActivities: MutableMap<String, TypeSpec>) : FunSpec {
        // TODO use topLevelStateHolder typeSpec and stateHoldersForActivities
        // TODO instead of manually recreating names below
        val funBuilder = FunSpec.builder("restoreStateConfigChange")
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
            .beginControlFlow("when (activity) {")


        activityToStateMap.forEach { activityToStateEntry ->
            val classDeclOfActivity = resolver.getClassDeclarationByName(activityToStateEntry.key)!!
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            val activityStateHolderFieldName = "${lowercaseFirstLetter(classDeclOfActivity.simpleName.asString())}State"
            funBuilder.beginControlFlow("if (stateHolder.$activityStateHolderFieldName != null) {")
            activityToStateEntry.value.filter { ksPropertyDeclaration ->
               ksPropertyDeclaration.getAnnotationsByType(State::class).toList().first().retainAcross == StateDestroyingEvent.CONFIGURATION_CHANGE
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
        val funBuilder = FunSpec.builder("saveStateConfigChange")
            .addParameter(
                ParameterSpec.builder(
                    "activity",
                    ClassName("androidx.activity", "ComponentActivity")
                )
                    .build()
            )
            .returns(ClassName(packageName, "StateHolder"))
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
                ksPropertyDeclaration.getAnnotationsByType(State::class).toList().first().retainAcross == StateDestroyingEvent.CONFIGURATION_CHANGE
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
                val stateAnn = propDecl.getAnnotationsByType(State::class).toList().first()
                // only put into the state holder if we're retaining across config change
                if (stateAnn.retainAcross == StateDestroyingEvent.CONFIGURATION_CHANGE) {
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