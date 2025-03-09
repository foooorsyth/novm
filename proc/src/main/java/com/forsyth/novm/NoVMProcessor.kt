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
const val FRAGMENT_QUALIFIED_NAME = "androidx.fragment.app.Fragment"
const val DEFAULT_PACKAGE_NAME = "com.forsyth.novm"
const val DEFAULT_STATE_SAVING_ACTIVITY_SIMPLE_NAME = "StateSavingActivity"
const val DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME = "StateSavingFragment"

class NoVMProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    var pass = 1
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // TODO get package, statesavingactivity name from ksp options
        val isStateSavingActivityWritten = resolver.getClassDeclarationByName("${DEFAULT_PACKAGE_NAME}.$DEFAULT_STATE_SAVING_ACTIVITY_SIMPLE_NAME") != null
        val isStateSavingFragmentWritten = resolver.getClassDeclarationByName("${DEFAULT_PACKAGE_NAME}.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME") != null
        if (!isStateSavingActivityWritten) {
            logger.warn("generating ssactivity, pass $pass")
            generateStateSavingActivityFile(DEFAULT_PACKAGE_NAME).writeTo(codeGenerator, Dependencies(true))
        }
        if (!isStateSavingFragmentWritten) {
            logger.warn("generating ssfragment, pass $pass")
            generateStateSavingFragmentFile(DEFAULT_PACKAGE_NAME).writeTo(codeGenerator, Dependencies(true))
        }
        val retainSymbols = resolver.getSymbolsWithAnnotation(Retain::class.qualifiedName!!, false).toList()
        val recheck = mutableListOf<KSAnnotated>()
        val componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>> = mutableMapOf()
        ensureSupported(
            retainSymbols,
            isStateSavingActivityWritten,
            isStateSavingFragmentWritten,
            recheck,
            componentToStateMap
        )
        if (componentToStateMap.isNotEmpty() && isStateSavingActivityWritten) {
            generateDynamicCode(resolver, componentToStateMap)
        }
        logger.warn("sizeof recheck: ${recheck.size}, pass: $pass")
        pass += 1
        return recheck
    }

    private fun ensureSupported(retainSymbols: List<KSAnnotated>,
                                isStateSavingActivityValid: Boolean,
                                isStateSavingFragmentValid: Boolean,
                                recheck: MutableList<KSAnnotated>,
                                componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) {
        logger.warn("ensureSupported#pass: $pass")
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
                // TODO handle hostOf / hostedBy
                logger.error("@State must be declared within a ComponentActivity or Fragment, skipping ${propertyDecl.simpleName}")
                return@forEach
            }
            logger.warn("ensureSupported#classDecl: ${parentClassDecl.qualifiedName!!.asString()}")
            val isSubclassOfComponentActivity = isSubclassOf(parentClassDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)
            val isSubclassOfFragment = isSubclassOf(parentClassDecl, FRAGMENT_QUALIFIED_NAME)
            logger.warn("ensureSupported#isSubAct?: $isSubclassOfComponentActivity")
            logger.warn("ensureSupported#isSubFrag?: $isSubclassOfFragment")
            if (!isSubclassOfComponentActivity && !isSubclassOfFragment) {
                if (!isStateSavingActivityValid || !isStateSavingFragmentValid) {
                    // ALL generated StateSavingX must be generated before we can make a determination here
                    //logger.warn("@State must be declared within a ComponentActivity or Fragment, checking ${propertyDecl.simpleName} again after codegen...")
                    recheck.add(retainNode)
                } else {
                    logger.error("@State must be declared within a ComponentActivity or Fragment: ${propertyDecl.simpleName}")
                    return
                }
            }
            if (parentClassDecl.isLocal()) {
                logger.error("@State cannot be contained by local declaration, skipping $${propertyDecl.simpleName}")
                return@forEach
            }
            if (!componentToStateMap.contains(parentClassDecl.qualifiedName!!.asString())) {
                componentToStateMap[parentClassDecl.qualifiedName!!.asString()] = mutableListOf(propertyDecl)
            } else {
                componentToStateMap[parentClassDecl.qualifiedName!!.asString()]!!.add(propertyDecl)
            }
        }
    }

    private fun generateStaticCode(packageName: String) {
        generateStateSavingActivityFile(packageName).writeTo(codeGenerator, Dependencies(true))
        generateStateSavingFragmentFile(packageName).writeTo(codeGenerator, Dependencies(true))
    }

    private fun generateDynamicCode(resolver: Resolver, componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) {
        val componentContainingFiles = componentToStateMap.keys.mapNotNull { componentFullyQualified ->
            resolver.getClassDeclarationByName(componentFullyQualified)?.containingFile
        }
        val noVMDynamicFileName = "NoVMDynamic"
        val packageName = resolver.getClassDeclarationByName(componentToStateMap.keys.first())!!.packageName.asString() // TODO improve, get app package name from ksp options
        val stateHoldersForComponents = generateStateHoldersForComponents(resolver, componentToStateMap)
        val topLevelStateHolder = generateTopLevelStateHolder(packageName, stateHoldersForComponents)
        val stateSaver = generateStateSaver(packageName,
            resolver,
            componentToStateMap,
            topLevelStateHolder,
            stateHoldersForComponents
            )

        FileSpec.builder(packageName, noVMDynamicFileName)
            .addImport("android.os", "Bundle")
            .addImport("androidx.annotation", "CallSuper")
            .addImport("androidx.appcompat.app", "AppCompatActivity")
            .addTypes(stateHoldersForComponents.values)
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
            .writeTo(codeGenerator, Dependencies(true, *componentContainingFiles.toTypedArray()))
    }

    private fun generateStateSavingFragmentFile(packageName: String) : FileSpec {
        return FileSpec.builder(packageName, "StateSavingFragment")
            .addType(
                TypeSpec.classBuilder("StateSavingFragment")
                    .addModifiers(KModifier.OPEN)
                    .superclass(ClassName("androidx.fragment.app", "Fragment"))
                    .addProperty(
                        PropertySpec.builder("stateSaver", ClassName(packageName, "StateSaver"))
                            .initializer("%L", "provideStateSaver()")
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("identificationStrategy", ClassName(packageName, "FragmentIdentificationStrategy"))
                            .initializer("%L", "FragmentIdentificationStrategy.TAG")
                            .addModifiers(KModifier.OPEN)
                            .mutable(true)
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
                            .beginControlFlow("if (savedInstanceState != null)")
                            .addStatement("%L", "stateSaver.restoreStateBundle(this, savedInstanceState)")
                            .endControlFlow() // close if
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
                    .build()
            )
            .build()

    }

    private fun generateStateSavingActivityFile(packageName: String) : FileSpec {
        return FileSpec.builder(packageName, "StateSavingActivity")
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
                                        "}\n"
                            )
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("onRestoreInstanceState")
                            .addModifiers(KModifier.OVERRIDE)
                            .addAnnotation(ClassName("androidx.annotation", "CallSuper"))
                            .addParameter(
                                ParameterSpec
                                    .builder("savedInstanceState", ClassName("android.os", "Bundle"))
                                    .build()
                            )
                            .addStatement("%L", "super.onRestoreInstanceState(savedInstanceState)")
                            .addStatement("%L", "stateSaver.restoreStateBundle(this, savedInstanceState)")
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
                                   componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
                                   topLevelStateHolder: TypeSpec,
                                   stateHoldersForComponents: MutableMap<String, TypeSpec>,
                                   ) : TypeSpec {
        val ssbRet = generateSaveStateBundle(
                    packageName,
                    resolver,
                    componentToStateMap,
                )
        val builder = TypeSpec.classBuilder("GeneratedStateSaver") //dangerous, collisions // TODO const string
            .addSuperinterface(ClassName(packageName, "StateSaver")) // TODO const string
            .addFunction(
                generateSaveStateConfigChange(
                    packageName,
                    resolver,
                    componentToStateMap,
                    topLevelStateHolder,
                    stateHoldersForComponents
                )
            )
            .addFunction(
                generateRestoreStateConfigChange(
                    packageName,
                    resolver,
                    componentToStateMap,
                    topLevelStateHolder,
                    stateHoldersForComponents
                )
            )
            .addFunction(
                ssbRet.funSpec
            )
            .addFunction(
                generateRestoreStateBundle(
                    packageName,
                    resolver,
                    componentToStateMap,
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
                    "component",
                    ClassName("kotlin", "Any")
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
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>): SSBRet {
        val bundleKeyValuePairs = mutableMapOf<String, String>()
        val funBuilder = generateSaveStateBundleSignature()
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("when (component) {")
        val activitiesToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            val isSub = isSubclassOf(classDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)
            return@filter isSub
        }
        val fragmentsToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            return@filter isSubclassOf(classDecl, "$DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME")
        }

        funBuilder.beginControlFlow("is $COMPONENT_ACTIVITY_QUALIFIED_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        activitiesToStates.forEach { activityToStateEntry ->
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
                    funBuilder.addStatement("bundle.put${bundleFunPostfixRet.postfix}(${kvp.first}, component.${ksPropertyDeclaration.simpleName.asString()})")
            }
            funBuilder.endControlFlow() // close is (specific activity)
        }
        funBuilder.endControlFlow() // close is (component activity)
        funBuilder.endControlFlow() // close when inner
        funBuilder.beginControlFlow("is $DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        fragmentsToStates.forEach { fragmentToStateEntry ->
            funBuilder.beginControlFlow("is ${fragmentToStateEntry.key} -> {")
            funBuilder.addStatement("val fragBundle = Bundle()")
            fragmentToStateEntry.value
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
                    funBuilder.addStatement("fragBundle.put${bundleFunPostfixRet.postfix}(${kvp.first}, component.${ksPropertyDeclaration.simpleName.asString()})")
                }

            // TODO break this out into its own function for rsb
            val clsSimpleName = resolver.getClassDeclarationByName(fragmentToStateEntry.key)!!.simpleName.asString()
            // CLASS
            val lKvp = generateBundleFragmentKeyValuePairForClass(clsSimpleName)
            bundleKeyValuePairs[lKvp.first] = lKvp.second
            funBuilder.beginControlFlow("when (component.identificationStrategy) {")
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.ID -> {")
            funBuilder.addStatement("bundle.putBundle(${generateBundleFragmentKeyStringForId(clsSimpleName)}, fragBundle)")
            funBuilder.endControlFlow() // close CONTAINER_ID
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.TAG -> {")
            funBuilder.beginControlFlow("if (component.tag == null) {")
            funBuilder.addStatement("throw RuntimeException(\"identificationStrategy for instance of Fragment \${component::class.java.simpleName} is TAG but fragment.tag is null\")")
            funBuilder.endControlFlow() // close if
            funBuilder.addStatement("bundle.putBundle(${generateBundleFragmentKeyStringForTag(clsSimpleName)}, fragBundle)")
            funBuilder.endControlFlow() // close TAG
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.CLASS -> {")
            funBuilder.addStatement("bundle.putBundle(kl_$clsSimpleName, fragBundle)")
            funBuilder.endControlFlow() // close CLASS
            funBuilder.endControlFlow() // close when (id strat switch)
            funBuilder.endControlFlow() // close is (specific fragment)
        }
        funBuilder.endControlFlow() // close is (fragment)
        funBuilder.endControlFlow() // close when inner
        funBuilder.endControlFlow() // close when outer
        return SSBRet(
            funSpec = funBuilder.build(),
            bundleKeyValuePairs = bundleKeyValuePairs
        )
    }

    private fun generateRestoreStateBundleSignature() : FunSpec.Builder {
        return FunSpec.builder("restoreStateBundle")
            .addParameter(
                ParameterSpec.builder(
                    "component",
                    ClassName("kotlin", "Any")
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
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>
        ): FunSpec {
        val funBuilder = generateRestoreStateBundleSignature()
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("when (component) {")
        val activitiesToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            return@filter isSubclassOf(classDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)
        }

        funBuilder.beginControlFlow("is $COMPONENT_ACTIVITY_QUALIFIED_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        activitiesToStates.forEach { activityToStateEntry ->
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
                            funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key)")
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.NULLABLE_KNOWN_TYPE -> {
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key)")
                            } else {
                                funBuilder.addStatement("bundle.get${bundleFunPostfixRet.postfix}($key)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.SUBCLASS_SERIALIZABLE_OR_PARCELABLE -> {
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.makeNotNullable().toClassName()}::class.java)")
                            } else {
                                funBuilder.addStatement("bundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.toClassName()}::class.java)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.NOT_APPLICABLE -> {
                            logger.error("State ${ksPropertyDeclaration.simpleName.asString()} is marked to be retained across PROCESS_DEATH but is not a type supported by Bundle")
                            return@filteredForEach
                        }
                    }

                }
            funBuilder.endControlFlow() // close is (specific activity)
        }
        funBuilder.endControlFlow() // close when inner
        funBuilder.endControlFlow() // close is (component activity)

        funBuilder.beginControlFlow("is $DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME-> {")
        funBuilder.beginControlFlow("when (component) {")
        val fragmentsToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            return@filter isSubclassOf(classDecl, "$DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME")
        }
        fragmentsToStates.forEach { fragmentToStateEntry ->
            val clsSimpleName = resolver.getClassDeclarationByName(fragmentToStateEntry.key)!!.simpleName.asString()
            funBuilder.beginControlFlow("is ${fragmentToStateEntry.key} -> {")
            funBuilder.beginControlFlow("val fragBundleKey = when (component.identificationStrategy) {")
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.TAG -> {")
            funBuilder.addStatement(generateBundleFragmentKeyStringForTag(clsSimpleName))
            funBuilder.endControlFlow()
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.ID -> {")
            funBuilder.addStatement(generateBundleFragmentKeyStringForId(clsSimpleName))
            funBuilder.endControlFlow()
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.CLASS -> {")
            funBuilder.addStatement("\"${generateBundleFragmentKeyValuePairForClass(clsSimpleName).first}\"")
            funBuilder.endControlFlow()
            funBuilder.endControlFlow() // close when (id strat for bundle key)
            funBuilder.addStatement("val fragBundle = bundle.getBundle(fragBundleKey)")
            funBuilder.beginControlFlow("if (fragBundle == null) {")
            funBuilder.addStatement("return")
            funBuilder.endControlFlow()
            fragmentToStateEntry.value
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
                            funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = fragBundle.get${bundleFunPostfixRet.postfix}($key)")
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.NULLABLE_KNOWN_TYPE -> {
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = fragBundle.get${bundleFunPostfixRet.postfix}($key)")
                            } else {
                                funBuilder.addStatement("fragBundle.get${bundleFunPostfixRet.postfix}($key)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.SUBCLASS_SERIALIZABLE_OR_PARCELABLE -> {
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = fragBundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.makeNotNullable().toClassName()}::class.java)")
                            } else {
                                funBuilder.addStatement("fragBundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.toClassName()}::class.java)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.NOT_APPLICABLE -> {
                            logger.error("State ${ksPropertyDeclaration.simpleName.asString()} is marked to be retained across PROCESS_DEATH but is not a type supported by Bundle")
                            return@filteredForEach
                        }
                    }

                }
            funBuilder.endControlFlow() // close is (specific fragment)
        }
        funBuilder.endControlFlow() // close when inner
        funBuilder.endControlFlow() // close is (fragment))
        funBuilder.endControlFlow() // close when outer
        return funBuilder.build()
    }

    // for each fragment, there are 3 classes of bundle
    /*
    // 1) kt_:fragmentName_:tag <-- tag
    // 2) ki_:fragmentName_:containerId <-- containerId
    // 3) kl_:fragmentName <-- class

    Two of these are determined at runtime -- only the class variant is predetermined
     */
    private fun generateBundleFragmentKeyValuePairForClass(clsSimpleName: String) : Pair<String, String>{
       return "kl_$clsSimpleName" to "l_$clsSimpleName"
    }
    private fun generateBundleFragmentKeyStringForId(clsSimpleName: String) : String {
        return "\"i_${clsSimpleName}_\${component.id}\""
    }
    private fun generateBundleFragmentKeyStringForTag(clsSimpleName: String) : String {
        return "\"t_${clsSimpleName}_\${component.tag}\""
    }

    private fun generateBundleKeyValuePair(ksPropertyDeclaration: KSPropertyDeclaration) : Pair<String, String> {
        val value = "${ksPropertyDeclaration.parentDeclaration!!.simpleName.asString()}_${ksPropertyDeclaration.simpleName.asString()}"
        val key = "k_${value}"
        return key to value
    }

    private fun generateRestoreStateConfigChangeSignature(packageName: String) : FunSpec.Builder {
        return FunSpec.builder("restoreStateConfigChange")
            .addParameter(
                ParameterSpec.builder(
                    "component",
                    ClassName("kotlin", "Any")
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
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        topLevelStateHolder: TypeSpec,
        stateHoldersForComponents: MutableMap<String, TypeSpec>) : FunSpec {
        // TODO use topLevelStateHolder typeSpec and stateHoldersForComponents
        // TODO instead of manually recreating names below
        val funBuilder = generateRestoreStateConfigChangeSignature(packageName)
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("when (component) {")


        componentToStateMap.forEach { activityToStateEntry ->
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
                val typeSpecOfStateHolder = stateHoldersForComponents[activityToStateEntry.key]!!
                val equivPropInStateHolder = typeSpecOfStateHolder.propertySpecs.first { it.name == ksPropertyDeclaration.simpleName.asString() }
                if (equivPropInStateHolder.type.isNullable && !ksPropertyDeclaration.type.resolve().isMarkedNullable) {
                    funBuilder.beginControlFlow("if (stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()} != null) {")
                    funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()}!!")
                    funBuilder.endControlFlow()
                }
                else {
                    funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()}")
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
                    "component",
                    ClassName("kotlin", "Any")
                )
                    .build()
            )
            .returns(ClassName(packageName, "StateHolder"))
    }

    @OptIn(KspExperimental::class)
    private fun generateSaveStateConfigChange(
        packageName: String,
           resolver: Resolver,
           componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
           topLevelStateHolder: TypeSpec,
           stateHoldersForComponents: MutableMap<String, TypeSpec>,
        ) : FunSpec {
        // TODO use topLevelStateHolder typeSpec and stateHoldersForComponents
        // TODO instead of manually recreating names below
        val funBuilder = generateSaveStateConfigChangeSignature(packageName)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("val stateHolder = StateHolder()")
            .beginControlFlow("when (component) {")

        componentToStateMap.forEach { entry ->
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
                    "stateHolder.$activityStateHolderFieldName!!.${propertyDecl.simpleName.asString()} = component.${propertyDecl.simpleName.asString()}"
                )
            }
            funBuilder.endControlFlow() // end is
        }

        funBuilder.endControlFlow() // close when
            .addStatement("return stateHolder")
        return funBuilder.build()
    }

    private fun generateTopLevelStateHolder(packageName: String, stateHoldersForComponents: MutableMap<String, TypeSpec>) : TypeSpec {
        val builder = TypeSpec.classBuilder("StateHolder")
        stateHoldersForComponents.forEach { entry ->
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
    private fun generateStateHoldersForComponents(resolver: Resolver,
                                                  componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>) : MutableMap<String, TypeSpec> {
        val ret = mutableMapOf<String, TypeSpec>()
        componentToStateMap.forEach { entry ->
            val componentName = resolver.getClassDeclarationByName(entry.key)!!.simpleName.asString()
            val typeSpecBuilder = TypeSpec.classBuilder(componentName + "State") // TODO dangerous, check for collisions
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