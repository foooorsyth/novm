package com.forsyth.novm

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.innerArguments
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random


const val COMPONENT_ACTIVITY_QUALIFIED_NAME = "androidx.activity.ComponentActivity"
const val FRAGMENT_QUALIFIED_NAME = "androidx.fragment.app.Fragment"
const val DEFAULT_PACKAGE_NAME = "com.forsyth.novm"
const val DEFAULT_DEPENDENCY_PACKAGE_NAME = "com.forsyth.novm.dependencies"
const val DEPENDENCY_FILENAME_PROPERTY_PREFIX = "novm_"
const val DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME = "StateSavingFragment"
const val OPTION_IS_DEPENDENCY = "novm.isDependency"
const val OPTION_DEBUG_LOGGING = "novm.debugLogging"

fun msgNotSupportedByBundle(clazz: String, field: String) : String {
    return "State $clazz#$field is marked to be retained across PROCESS_DEATH but is not a type supported by Bundle"
}

fun glogd(logger: KSPLogger?, isDebugLoggingEnabled: Boolean?, msg: String, ksNode: KSNode? = null) {
    if (logger == null) {
        return
    }
    if (isDebugLoggingEnabled == true) {
        logger.warn(msg, ksNode)
    }
}

fun gloge(logger: KSPLogger?, msg: String, ksNode: KSNode? = null) {
   logger?.error(msg, ksNode)
}

class NoVMProcessor(
    val codeGenerator: CodeGenerator,
    val options: Map<String, String>,
    val logger: KSPLogger) : SymbolProcessor {
    var pass = 1
    var hasWrittenDynamic = false
    var isDebugLoggingEnabled: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        isDebugLoggingEnabled = options[OPTION_DEBUG_LOGGING]?.lowercase() == "true"
        val ret = if (options[OPTION_IS_DEPENDENCY]?.lowercase() == "true") {
            processDependency(resolver)
        } else {
            processApp(resolver)
        }
        pass += 1
        return ret
    }

    fun logd(message: String, ksNode: KSNode? = null) {
        if (isDebugLoggingEnabled) {
            logger.warn(message, ksNode)
        }
    }
    
    fun loge(message: String, ksNode: KSNode? = null) {
        logger.error(message, ksNode)
    }
    
    @OptIn(KspExperimental::class)
    private fun processApp(resolver: Resolver) : List<KSAnnotated> {
        val pkgDecls = resolver.getDeclarationsFromPackage(DEFAULT_DEPENDENCY_PACKAGE_NAME)
        // TODO search nested class eventually, for now just components at the top level of package
        val topLvlClassDcls = pkgDecls
            .filterIsInstance(KSPropertyDeclaration::class.java)
            .filterNot { it.simpleName.asString().startsWith(DEPENDENCY_FILENAME_PROPERTY_PREFIX) }
            .map { it.simpleName.asString().replace('_', '.') }
            .map { pkgContainingRetains ->
                logd("pkgContainingRetains: $pkgContainingRetains")
                val redirPkgDecls = resolver.getDeclarationsFromPackage(pkgContainingRetains)
                // Only declarations at the top level of the package -- not property decls inside of classes
                // need to recursively search
                logd("redirPkgDecl size: ${redirPkgDecls.toList().size}")
                redirPkgDecls.toList().forEach { redirPkgDecl ->
                    logd("all decls: " + redirPkgDecl.qualifiedName!!.asString())

                }
                redirPkgDecls
             }
            .flatten()
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        val retainSymbols = mutableListOf<KSAnnotated>()
        topLvlClassDcls.forEach { ksClassDeclaration ->
            retainSymbols.addAll(
                ksClassDeclaration
                    .getDeclaredProperties()
                    .filter { ksPropertyDeclaration -> ksPropertyDeclaration.isAnnotationPresent(Retain::class) }
                    .map { ksPropertyDeclaration -> ksPropertyDeclaration }
                    .toList()
            )
        }
        logd("dcls in libs: ${pkgDecls.toList().size}")
        logd("retain symbols from libs: ${retainSymbols.size}")
        retainSymbols.addAll(resolver.getSymbolsWithAnnotation(Retain::class.qualifiedName!!, false))
        val recheck = mutableListOf<KSAnnotated>()
        val componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>> =
            mutableMapOf()
        ensureSupported(
            retainSymbols,
            recheck,
            componentToStateMap
        )
        if (componentToStateMap.isNotEmpty() && !hasWrittenDynamic) {
            generateDynamicCode(resolver, componentToStateMap)
            hasWrittenDynamic = true
        }
        logd("sizeof recheck: ${recheck.size}, pass: $pass")
        return recheck
    }

    @OptIn(KspExperimental::class)
    private fun processDependency(resolver: Resolver) : List<KSAnnotated> {
        val takenSymbols = resolver.getDeclarationsFromPackage(DEFAULT_DEPENDENCY_PACKAGE_NAME)
        val retainSymbols = resolver.getSymbolsWithAnnotation(Retain::class.qualifiedName!!, false).toList()
        logd("retain symbols size: ${retainSymbols.size}")
        if (retainSymbols.isEmpty()) {
            return emptyList()
        }
        val componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>> =
            mutableMapOf()
        val recheck = mutableListOf<KSAnnotated>()
        ensureSupported(
            retainSymbols,
            recheck,
            componentToStateMap
        )
        // class declaration cache (lookups in this module)
        val classDeclMemo = mutableMapOf<String, KSClassDeclaration>()
        val pkgWithUnderscoresDeclaredByAnotherModule: Set<String> = takenSymbols
            .filter { !it.simpleName.asString().startsWith(DEPENDENCY_FILENAME_PROPERTY_PREFIX) }
            .map { it.simpleName.asString() }
            .toSet()
        val orderedPkgWithUnderscoresDeclaredByThisModules = mutableListOf<String>()
        // names of fields in the *.dependencies package that this module has/will declare
        val pkgWithUnderscoresDeclaredByThisModule = mutableSetOf<String>()
        componentToStateMap.keys.forEach { componentFullyQualified ->
            val componentClassDecl = if (classDeclMemo.contains(componentFullyQualified)) {
                classDeclMemo[componentFullyQualified]!!
            } else {
                val ret = resolver.getClassDeclarationByName(componentFullyQualified)!!
                classDeclMemo[componentFullyQualified] = ret
                ret
            }
            val pkgOfComponent = componentClassDecl.packageName.asString()
            val pkgUnderscore = pkgOfComponent.replace('.', '_')
            // need to check for properties that may already exist with the same package name
            if (!pkgWithUnderscoresDeclaredByAnotherModule.contains(pkgUnderscore) &&
                !pkgWithUnderscoresDeclaredByThisModule.contains(pkgUnderscore)) {
                orderedPkgWithUnderscoresDeclaredByThisModules.add(pkgUnderscore)
                pkgWithUnderscoresDeclaredByThisModule.add(pkgUnderscore)
            }
        }
        val takenFileNames = takenSymbols
            .filter {
                it.simpleName.asString().startsWith(DEPENDENCY_FILENAME_PROPERTY_PREFIX)
            }
            .map { it.simpleName.asString().substring(DEPENDENCY_FILENAME_PROPERTY_PREFIX.length) }
            .toSet()
        logd("orderedPkgs: $orderedPkgWithUnderscoresDeclaredByThisModules")
        val concatenatedNewPkgFields = orderedPkgWithUnderscoresDeclaredByThisModules.joinToString("_"){ it }
        logd("concatenatedNewPkgFields: $concatenatedNewPkgFields")
        var md5FileName: String? = null
        do {
            if (md5FileName == null) {
                md5FileName = concatenatedNewPkgFields.toMD5().takeLast(7)
            } else {
                md5FileName += "1"
            }
        } while (takenFileNames.contains(md5FileName))
        logd("md5 file name: $md5FileName")
        val fileBuilder = FileSpec.builder(
            DEFAULT_DEPENDENCY_PACKAGE_NAME,
            md5FileName!!
        )
        fileBuilder
            .addProperty(
                PropertySpec.builder(
                    DEPENDENCY_FILENAME_PROPERTY_PREFIX + md5FileName, INT, KModifier.CONST
                )
                    .initializer("%L", "0")
                    .build()
            )
        orderedPkgWithUnderscoresDeclaredByThisModules.forEach { pkgUnderscore ->
            fileBuilder
                .addProperty(
                    PropertySpec.builder(pkgUnderscore, INT, KModifier.CONST)
                        .initializer("%L", "0")
                        .build()
                )
        }
        val componentContainingFiles =
            classDeclMemo.values.mapNotNull { classDecl->
                classDecl.containingFile
            }.toSet().toList() // duplicates
        fileBuilder
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = true, *componentContainingFiles.toTypedArray()))
        return emptyList()
    }

    private fun generateRandomAlphanumeric(length: Int = 6): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { charset[Random.nextInt(charset.length)] }
            .joinToString("")
    }

    private fun ensureSupported(
        retainSymbols: List<KSAnnotated>,
        recheck: MutableList<KSAnnotated>,
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>
    ) {
        logd("ensureSupported#pass: $pass")
        retainSymbols.forEach { retainNode: KSAnnotated ->
            val propertyDecl = retainNode as? KSPropertyDeclaration
            
            if (propertyDecl == null) {
                loge("State marked with @Retain must annotate a property declaration, skipping...")
                return@forEach
            }
            if (!propertyDecl.isMutable) {
                loge("State marked with @Retain must be mutable")
                return@forEach
            }
            if (propertyDecl.parentDeclaration == null) {
                loge("State marked with @Retain cannot be declared at top level, skipping ${propertyDecl.simpleName}")
                return@forEach
            }
            val parentClassDecl = propertyDecl.parentDeclaration as? KSClassDeclaration
            if (parentClassDecl == null) {
                // TODO handle hostOf / hostedBy
                loge("State marked with @Retain must be declared within a ComponentActivity or Fragment, skipping ${propertyDecl.simpleName}")
                return@forEach
            }
            logd("ensureSupported#classDecl: ${parentClassDecl.qualifiedName!!.asString()}")
            val isSubclassOfComponentActivity =
                isSubclassOf(parentClassDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)
            val isSubclassOfFragment = isSubclassOf(parentClassDecl, FRAGMENT_QUALIFIED_NAME)
            logd("ensureSupported#isSubAct?: $isSubclassOfComponentActivity")
            logd("ensureSupported#isSubFrag?: $isSubclassOfFragment")
            if (!isSubclassOfComponentActivity && !isSubclassOfFragment) {
                loge("State marked with @Retain must be declared within a ComponentActivity or Fragment: ${propertyDecl.simpleName}")
                return
            }
            if (parentClassDecl.isLocal()) {
                loge("State marked with @Retain cannot be contained by local declaration, skipping $${propertyDecl.simpleName}")
                return@forEach
            }
            if (!componentToStateMap.contains(parentClassDecl.qualifiedName!!.asString())) {
                componentToStateMap[parentClassDecl.qualifiedName!!.asString()] =
                    mutableListOf(propertyDecl)
            } else {
                componentToStateMap[parentClassDecl.qualifiedName!!.asString()]!!.add(propertyDecl)
            }
        }
    }

    private fun generateDynamicCode(
        resolver: Resolver,
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>
    ) {
        val componentContainingFiles =
            componentToStateMap.keys.mapNotNull { componentFullyQualified ->
                resolver.getClassDeclarationByName(componentFullyQualified)?.containingFile
            }.toSet().toList()
        val noVMDynamicFileName = "NoVMDynamic"
        val stateHoldersForComponents =
            generateStateHoldersForComponents(resolver, componentToStateMap)
        val topLevelStateHolder = generateTopLevelStateHolder(
            DEFAULT_PACKAGE_NAME,
            resolver,
            componentToStateMap,
            stateHoldersForComponents
        )
        val generatedStateSaver = generateStateSaver(
            DEFAULT_PACKAGE_NAME,
            resolver,
            componentToStateMap,
            topLevelStateHolder,
            stateHoldersForComponents
        )

        FileSpec.builder(DEFAULT_PACKAGE_NAME, noVMDynamicFileName)
            .addImport("android.os", "Bundle")
            .addImport("androidx.annotation", "CallSuper")
            .addImport("androidx.appcompat.app", "AppCompatActivity")
            .addTypes(stateHoldersForComponents.values)
            .addType(topLevelStateHolder)
            .addType(generatedStateSaver)
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = true, *componentContainingFiles.toTypedArray()))
    }

    private fun generateStateSaver(
        packageName: String,
        resolver: Resolver,
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        topLevelStateHolder: TypeSpec,
        stateHoldersForComponents: MutableMap<String, TypeSpec>,
    ): TypeSpec {
        val ssbRet = generateSaveStateBundle(
            packageName,
            resolver,
            componentToStateMap,
            stateHoldersForComponents
        )
        val builder =
            TypeSpec.classBuilder("GeneratedStateSaver") //dangerous, collisions // TODO const string
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
                        stateHoldersForComponents
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

    private fun generateSaveStateBundleSignature(): FunSpec.Builder {
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
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        stateHoldersForComponents: MutableMap<String, TypeSpec>
    ): SSBRet {
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
            return@filter isSubclassOf(
                classDecl,
                "$DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME"
            )
        }

        funBuilder.beginControlFlow("is $COMPONENT_ACTIVITY_QUALIFIED_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        activitiesToStates.forEach { activityToStateEntry ->
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            activityToStateEntry.value
                .filter { ksPropertyDeclaration ->
                    ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                        .first().across.contains(StateDestroyingEvent.PROCESS_DEATH)
                }
                .forEach filteredForEach@{ ksPropertyDeclaration ->
                    val bundleFunPostfixRet = getBundleFunPostfix(resolver, ksPropertyDeclaration, logger, isDebugLoggingEnabled)
                    if (bundleFunPostfixRet.category == BundleFunPostfixCategory.NOT_APPLICABLE) {
                        loge("1: " + msgNotSupportedByBundle(activityToStateEntry.key, ksPropertyDeclaration.simpleName.asString()))
                        return@filteredForEach
                    }
                    // TODO check bundlefunpostfix BEFORE codegen starts
                    val kvp = generateBundleKeyValuePair(stateHoldersForComponents[activityToStateEntry.key]!!.name!!, ksPropertyDeclaration)
                    bundleKeyValuePairs[kvp.first] = kvp.second
                    if (ksPropertyDeclaration.modifiers.contains(Modifier.LATEINIT)) {
                        funBuilder.beginControlFlow("run {")
                        funBuilder.beginControlFlow("val isInitialized = try {")
                        funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()}")
                        funBuilder.addStatement("true")
                        funBuilder.endControlFlow()
                        funBuilder.beginControlFlow("catch (ex: UninitializedPropertyAccessException) {")
                        funBuilder.addStatement("false")
                        funBuilder.endControlFlow()
                        funBuilder.beginControlFlow("if (isInitialized) {")
                        funBuilder.addStatement("bundle.put${bundleFunPostfixRet.postfix}(${kvp.first}, component.${ksPropertyDeclaration.simpleName.asString()})")
                        funBuilder.endControlFlow()
                        funBuilder.endControlFlow()
                    } else {
                        funBuilder.addStatement("bundle.put${bundleFunPostfixRet.postfix}(${kvp.first}, component.${ksPropertyDeclaration.simpleName.asString()})")
                    }
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
                    ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                        .first().across.contains(StateDestroyingEvent.PROCESS_DEATH)
                }
                .forEach filteredForEach@{ ksPropertyDeclaration ->
                    val bundleFunPostfixRet = getBundleFunPostfix(resolver, ksPropertyDeclaration, logger, isDebugLoggingEnabled)
                    if (bundleFunPostfixRet.category == BundleFunPostfixCategory.NOT_APPLICABLE) {
                        loge("2: " +msgNotSupportedByBundle(fragmentToStateEntry.key, ksPropertyDeclaration.simpleName.asString()))
                        return@filteredForEach
                    }
                    // TODO check bundlefunpostfix BEFORE codegen starts
                    val kvp = generateBundleKeyValuePair(stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!, ksPropertyDeclaration)
                    bundleKeyValuePairs[kvp.first] = kvp.second
                    if (ksPropertyDeclaration.modifiers.contains(Modifier.LATEINIT)) {
                        funBuilder.beginControlFlow("run {")
                        funBuilder.beginControlFlow("val isInitialized = try {")
                        funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()}")
                        funBuilder.addStatement("true")
                        funBuilder.endControlFlow()
                        funBuilder.beginControlFlow("catch (ex: UninitializedPropertyAccessException) {")
                        funBuilder.addStatement("false")
                        funBuilder.endControlFlow()
                        funBuilder.beginControlFlow("if (isInitialized) {")
                        funBuilder.addStatement("fragBundle.put${bundleFunPostfixRet.postfix}(${kvp.first}, component.${ksPropertyDeclaration.simpleName.asString()})")
                        funBuilder.endControlFlow()
                        funBuilder.endControlFlow()
                    } else {
                        funBuilder.addStatement("fragBundle.put${bundleFunPostfixRet.postfix}(${kvp.first}, component.${ksPropertyDeclaration.simpleName.asString()})")
                    }
                }

            // TODO break this out into its own function for rsb
            val clsSimpleName =
                resolver.getClassDeclarationByName(fragmentToStateEntry.key)!!.simpleName.asString()
            // CLASS
            val lKvp = generateBundleFragmentKeyValuePairForClass(stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!)
            bundleKeyValuePairs[lKvp.first] = lKvp.second
            funBuilder.beginControlFlow("when (component.identificationStrategy) {")
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.ID -> {")
            funBuilder.addStatement(
                "bundle.putBundle(${
                    generateBundleFragmentKeyStringForId(
                        stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!
                    )
                }, fragBundle)"
            )
            funBuilder.endControlFlow() // close CONTAINER_ID
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.TAG -> {")
            funBuilder.beginControlFlow("if (component.tag == null) {")
            funBuilder.addStatement("throw RuntimeException(\"identificationStrategy for Fragment@\${Integer.toHexString(System.identityHashCode(component))} of type \${component::class.java.simpleName} is TAG but Fragment's tag field is null\")")
            funBuilder.endControlFlow() // close if
            funBuilder.addStatement(
                "bundle.putBundle(${
                    generateBundleFragmentKeyStringForTag(
                        stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!
                    )
                }, fragBundle)"
            )
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

    private fun generateRestoreStateBundleSignature(): FunSpec.Builder {
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
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        stateHoldersForComponents: MutableMap<String, TypeSpec>,
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
                    ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                        .first().across.contains(StateDestroyingEvent.PROCESS_DEATH)
                }
                .forEach filteredForEach@{ ksPropertyDeclaration ->
                    val resolvedType = ksPropertyDeclaration.type.resolve()
                    val key = generateBundleKeyValuePair(stateHoldersForComponents[activityToStateEntry.key]!!.name!!, ksPropertyDeclaration).first
                    val bundleFunPostfixRet = getBundleFunPostfix(resolver, resolvedType, logger, isDebugLoggingEnabled)

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
                                funBuilder.addStatement(
                                    "component.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key, ${
                                        resolvedType.makeNotNullable().toClassName()
                                    }::class.java)"
                                )
                            } else {
                                funBuilder.addStatement("bundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.toClassName()}::class.java)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.ARRAY_WITH_COVARIANT_PARAMETER -> {
                            // need to resolve param type
                            val paramClassDecl = resolver.getClassDeclarationByName(resolvedType.innerArguments[0].toTypeName().toString())!!
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement(
                                    "component.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key, ${
                                        paramClassDecl.toClassName()
                                    }::class.java)"
                                )
                            } else {
                                funBuilder.addStatement("bundle.get${bundleFunPostfixRet.postfix}($key, ${paramClassDecl.toClassName()}::class.java)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                        }
                        BundleFunPostfixCategory.NOT_APPLICABLE -> {
                            loge("3: " + msgNotSupportedByBundle(activityToStateEntry.key, ksPropertyDeclaration.simpleName.asString()))
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
            return@filter isSubclassOf(
                classDecl,
                "$DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME"
            )
        }
        fragmentsToStates.forEach { fragmentToStateEntry ->
            funBuilder.beginControlFlow("is ${fragmentToStateEntry.key} -> {")
            funBuilder.beginControlFlow("val fragBundleKey = when (component.identificationStrategy) {")
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.TAG -> {")
            funBuilder.addStatement(generateBundleFragmentKeyStringForTag(stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!))
            funBuilder.endControlFlow()
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.ID -> {")
            funBuilder.addStatement(generateBundleFragmentKeyStringForId(stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!))
            funBuilder.endControlFlow()
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.CLASS -> {")
            funBuilder.addStatement(generateBundleFragmentKeyValuePairForClass(stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!).first)
            funBuilder.endControlFlow()
            funBuilder.endControlFlow() // close when (id strat for bundle key)
            funBuilder.addStatement("val fragBundle = bundle.getBundle(fragBundleKey)")
            funBuilder.beginControlFlow("if (fragBundle == null) {")
            funBuilder.addStatement("return")
            funBuilder.endControlFlow()
            fragmentToStateEntry.value
                .filter { ksPropertyDeclaration ->
                    ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                        .first().across.contains(StateDestroyingEvent.PROCESS_DEATH)
                }
                .forEach filteredForEach@{ ksPropertyDeclaration ->
                    val resolvedType = ksPropertyDeclaration.type.resolve()
                    val key = generateBundleKeyValuePair(stateHoldersForComponents[fragmentToStateEntry.key]!!.name!!, ksPropertyDeclaration).first
                    val bundleFunPostfixRet = getBundleFunPostfix(resolver, resolvedType, logger, isDebugLoggingEnabled)

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
                                funBuilder.addStatement(
                                    "component.${ksPropertyDeclaration.simpleName.asString()} = fragBundle.get${bundleFunPostfixRet.postfix}($key, ${
                                        resolvedType.makeNotNullable().toClassName()
                                    }::class.java)"
                                )
                            } else {
                                funBuilder.addStatement("fragBundle.get${bundleFunPostfixRet.postfix}($key, ${resolvedType.toClassName()}::class.java)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                            return@filteredForEach
                        }
                        BundleFunPostfixCategory.ARRAY_WITH_COVARIANT_PARAMETER -> {
                            // need to resolve param type
                            val paramClassDecl = resolver.getClassDeclarationByName(resolvedType.innerArguments[0].toTypeName().toString())!!
                            if (resolvedType.isMarkedNullable) {
                                funBuilder.addStatement(
                                    "component.${ksPropertyDeclaration.simpleName.asString()} = bundle.get${bundleFunPostfixRet.postfix}($key, ${
                                        paramClassDecl.toClassName()
                                    }::class.java)"
                                )
                            } else {
                                funBuilder.addStatement("bundle.get${bundleFunPostfixRet.postfix}($key, ${paramClassDecl.toClassName()}::class.java)?.let { component.${ksPropertyDeclaration.simpleName.asString()} = it }")
                            }
                        }
                        BundleFunPostfixCategory.NOT_APPLICABLE -> {
                            loge("4: " + msgNotSupportedByBundle(fragmentToStateEntry.key, ksPropertyDeclaration.simpleName.asString()))
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
    private fun generateBundleFragmentKeyValuePairForClass(componentStateHolderName: String): Pair<String, String> {
        return "kl_${componentStateHolderName.substring(0, componentStateHolderName.length - 5)}" to "l_${componentStateHolderName.substring(0, componentStateHolderName.length - 5)}"
    }

    private fun generateBundleFragmentKeyStringForId(componentStateHolderName: String): String {
        return "\"i_${componentStateHolderName.substring(0, componentStateHolderName.length - 5)}_\${component.id}\""
    }

    private fun generateBundleFragmentKeyStringForTag(componentStateHolderName: String): String {
        return "\"t_${componentStateHolderName.substring(0, componentStateHolderName.length - 5)}_\${component.tag}\""
    }

    private fun generateBundleKeyValuePair(componentStateHolderName: String, ksPropertyDeclaration: KSPropertyDeclaration): Pair<String, String> {
        val value =
            "${componentStateHolderName.substring(0, componentStateHolderName.length - 5)}_${ksPropertyDeclaration.simpleName.asString()}"
        val key = "k_${value}"
        return key to value
    }

    private fun generateRestoreStateConfigChangeSignature(packageName: String): FunSpec.Builder {
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
        stateHoldersForComponents: MutableMap<String, TypeSpec>
    ): FunSpec {
        val funBuilder = generateRestoreStateConfigChangeSignature(packageName)
            .addModifiers(KModifier.OVERRIDE)
            .beginControlFlow("if (stateHolder is EmptyStateHolder) {")
            .addStatement("return")
            .endControlFlow()
            .addStatement("stateHolder as ${topLevelStateHolder.name!!}")
            .beginControlFlow("when (component) {")
        val activitiesToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            return@filter isSubclassOf(classDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)
        }
        val fragmentsToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            return@filter isSubclassOf(
                classDecl,
                "$DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME"
            )
        }
        funBuilder.beginControlFlow("is $COMPONENT_ACTIVITY_QUALIFIED_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        activitiesToStates.forEach { activityToStateEntry ->
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            val typeSpecOfStateHolder =
                stateHoldersForComponents[activityToStateEntry.key]!!
            val activityStateHolderFieldName =
                lowercaseFirstLetter(typeSpecOfStateHolder.name!!)
            funBuilder.beginControlFlow("if (stateHolder.$activityStateHolderFieldName != null) {")
            activityToStateEntry.value.filter { ksPropertyDeclaration ->
                ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                    .first().across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)
            }
                .forEach { ksPropertyDeclaration ->
                    // need to find equivalent field in state holder for this activity
                    // need to check for null in stateholder prop
                    val equivPropInStateHolder =
                        typeSpecOfStateHolder.propertySpecs.first { it.name == ksPropertyDeclaration.simpleName.asString() }
                    if (equivPropInStateHolder.type.isNullable && !ksPropertyDeclaration.type.resolve().isMarkedNullable) {
                        funBuilder.beginControlFlow("if (stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()} != null) {")
                        funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()}!!")
                        funBuilder.endControlFlow()
                    } else {
                        funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()}")
                    }
                }
            funBuilder.endControlFlow() // close if
            funBuilder.endControlFlow() // close is
        }
        funBuilder.endControlFlow() // close is (component activity)
        funBuilder.endControlFlow() // close when inner
        funBuilder.beginControlFlow("is $DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        fragmentsToStates.forEach { fragmentToStateEntry ->
            val classDeclOfFrag = resolver.getClassDeclarationByName(fragmentToStateEntry.key)!!
            val configChangeProps = fragmentToStateEntry.value.filter { ksPropertyDeclaration ->
                ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                    .first().across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)
            }
            funBuilder.beginControlFlow("is ${fragmentToStateEntry.key} -> {")
            funBuilder.beginControlFlow("val fragStateHolder = when(component.identificationStrategy) {")
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.CLASS -> {")
            val typeSpecOfStateHolder = stateHoldersForComponents[fragmentToStateEntry.key]!!
            val fragStateHolderFieldNameForClass =
                "${lowercaseFirstLetter(typeSpecOfStateHolder.name!!)}ByClass"
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForClass"
            )
            funBuilder.endControlFlow() // close is (CLASS)
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.TAG -> {")
            val fragStateHolderFieldNameForTag =
                "${lowercaseFirstLetter(typeSpecOfStateHolder.name!!)}ByTag"
            funBuilder.beginControlFlow("if (component.tag == null) {")
            funBuilder.addStatement(
                "throw RuntimeException(\"Fragment@\${Integer.toHexString(System.identityHashCode(component))} of type " +
                        "${classDeclOfFrag.qualifiedName!!.asString()} has identificationStrategy of TAG but Fragment's tag field is null\")"
            )
            funBuilder.endControlFlow()
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForTag[component.tag!!]"
            )
            funBuilder.endControlFlow() // close is (TAG)
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.ID -> {")
            val fragStateHolderFieldNameForId =
                "${lowercaseFirstLetter(typeSpecOfStateHolder.name!!)}ById"
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForId[component.id]"
            )
            funBuilder.endControlFlow() // close is (ID)
            funBuilder.endControlFlow() // close when (id strat)
            funBuilder.beginControlFlow("if (fragStateHolder == null) {")
            funBuilder.addStatement("return") // TODO logging
            funBuilder.endControlFlow() // close if
            configChangeProps.forEach { ksPropertyDeclaration ->
                // need to find equivalent field in state holder for this activity
                // need to check for null in stateholder prop
                val equivPropInStateHolder =
                    typeSpecOfStateHolder.propertySpecs.first { it.name == ksPropertyDeclaration.simpleName.asString() }
                if (equivPropInStateHolder.type.isNullable && !ksPropertyDeclaration.type.resolve().isMarkedNullable) {
                    funBuilder.beginControlFlow("if (fragStateHolder.${ksPropertyDeclaration.simpleName.asString()} != null) {")
                    funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = fragStateHolder.${ksPropertyDeclaration.simpleName.asString()}!!")
                    funBuilder.endControlFlow()
                } else {
                    funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()} = fragStateHolder.${ksPropertyDeclaration.simpleName.asString()}")
                }
            }
            funBuilder.beginControlFlow("when(component.identificationStrategy) {")
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.CLASS -> {")
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForClass = null"
            )
            funBuilder.endControlFlow() // close is (CLASS)
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.TAG -> {")
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForTag.remove(component.tag!!)"
            )
            funBuilder.endControlFlow() // close is (TAG)
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.ID -> {")
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForId.remove(component.id)"
            )
            funBuilder.endControlFlow() // close is (ID)
            funBuilder.endControlFlow() // close when (id strat)
            funBuilder.endControlFlow() // close is (specific fragment)
        }
        funBuilder.endControlFlow() // close is (fragment)
        funBuilder.endControlFlow() // close when inner
        funBuilder.endControlFlow() // close when outer
        return funBuilder.build()
    }

    private fun generateSaveStateConfigChangeSignature(packageName: String): FunSpec.Builder {
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
    ): FunSpec {
        val funBuilder = generateSaveStateConfigChangeSignature(packageName)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("val stateHolder = ${topLevelStateHolder.name!!}()")
            .beginControlFlow("when (component) {")

        val activitiesToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            return@filter isSubclassOf(classDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)
        }
        val fragmentsToStates = componentToStateMap.filter { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            return@filter isSubclassOf(
                classDecl,
                "$DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME"
            )
        }
        funBuilder.beginControlFlow("is $COMPONENT_ACTIVITY_QUALIFIED_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        activitiesToStates.forEach { activityToStateEntry ->
            val stateHolderTypeSpec = stateHoldersForComponents[activityToStateEntry.key]!!
            funBuilder.beginControlFlow("is ${activityToStateEntry.key} -> {")
            val activityStateHolderFieldName =
                lowercaseFirstLetter(stateHolderTypeSpec.name!!)
            funBuilder.addStatement(
                "stateHolder.$activityStateHolderFieldName = ${stateHolderTypeSpec.name!!}()"
            )
            activityToStateEntry.value.filter { ksPropertyDeclaration ->
                ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                    .first().across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)
            }
                .forEach { ksPropertyDeclaration ->
                    if (ksPropertyDeclaration.modifiers.contains(Modifier.LATEINIT)) {
                        funBuilder.beginControlFlow("run {")
                        funBuilder.beginControlFlow("val isInitialized = try {")
                        funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()}")
                        funBuilder.addStatement("true")
                        funBuilder.endControlFlow()
                        funBuilder.beginControlFlow("catch (ex: UninitializedPropertyAccessException) {")
                        funBuilder.addStatement("false")
                        funBuilder.endControlFlow()
                        funBuilder.beginControlFlow("if (isInitialized) {")
                        funBuilder.addStatement(
                            "stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                        )
                        funBuilder.endControlFlow()
                        funBuilder.endControlFlow()
                    } else {
                        // during save, nullability doesn't matter
                        funBuilder.addStatement(
                            "stateHolder.$activityStateHolderFieldName!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                        )
                    }
                }
            funBuilder.endControlFlow() // end is (specific activity)
        }
        funBuilder.endControlFlow() // close is (component activity)
        funBuilder.endControlFlow() // close when inner
        funBuilder.beginControlFlow("is $DEFAULT_PACKAGE_NAME.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME -> {")
        funBuilder.beginControlFlow("when (component) {")
        fragmentsToStates.forEach { fragmentToStateEntry ->
            val fragmentStateHolderTypeSpec = stateHoldersForComponents[fragmentToStateEntry.key]!!
            val configChangeProps = fragmentToStateEntry.value.filter { ksPropertyDeclaration ->
                ksPropertyDeclaration.getAnnotationsByType(Retain::class).toList()
                    .first().across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)
            }
            funBuilder.beginControlFlow("is ${fragmentToStateEntry.key} -> {")
            funBuilder.beginControlFlow("when(component.identificationStrategy) {")
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.CLASS -> {")
            val fragStateHolderFieldNameForClass =
                "${lowercaseFirstLetter(fragmentStateHolderTypeSpec.name!!)}ByClass"
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForClass = ${fragmentStateHolderTypeSpec.name!!}()"
            )
            configChangeProps.forEach { ksPropertyDeclaration ->
                // during save, nullability doesn't matter, but lateinit does
                if (ksPropertyDeclaration.modifiers.contains(Modifier.LATEINIT)) {
                    funBuilder.beginControlFlow("run {")
                    funBuilder.beginControlFlow("val isInitialized = try {")
                    funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()}")
                    funBuilder.addStatement("true")
                    funBuilder.endControlFlow()
                    funBuilder.beginControlFlow("catch (ex: UninitializedPropertyAccessException) {")
                    funBuilder.addStatement("false")
                    funBuilder.endControlFlow()
                    funBuilder.beginControlFlow("if (isInitialized) {")
                    funBuilder.addStatement(
                        "stateHolder.$fragStateHolderFieldNameForClass!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                    )
                    funBuilder.endControlFlow()
                    funBuilder.endControlFlow()
                } else {
                    funBuilder.addStatement(
                        "stateHolder.$fragStateHolderFieldNameForClass!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                    )
                }
            }
            funBuilder.endControlFlow() // close is (CLASS)
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.TAG -> {")
            val fragStateHolderFieldNameForTag =
                "${lowercaseFirstLetter(fragmentStateHolderTypeSpec.name!!)}ByTag"
            funBuilder.beginControlFlow("if (component.tag == null) {")
            funBuilder.addStatement(
                "throw RuntimeException(\"Fragment@\${Integer.toHexString(System.identityHashCode(component))} of type " +
                        "${fragmentToStateEntry.key} has identificationStrategy of TAG but Fragment's tag field is null\")"
            )
            funBuilder.endControlFlow()
            funBuilder.addStatement("val fragStateHolder = ${fragmentStateHolderTypeSpec.name!!}()")
            configChangeProps.forEach { ksPropertyDeclaration ->
                // during save, nullability doesn't matter
                if (ksPropertyDeclaration.modifiers.contains(Modifier.LATEINIT)) {
                    funBuilder.beginControlFlow("run {")
                    funBuilder.beginControlFlow("val isInitialized = try {")
                    funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()}")
                    funBuilder.addStatement("true")
                    funBuilder.endControlFlow()
                    funBuilder.beginControlFlow("catch (ex: UninitializedPropertyAccessException) {")
                    funBuilder.addStatement("false")
                    funBuilder.endControlFlow()
                    funBuilder.beginControlFlow("if (isInitialized) {")
                    funBuilder.addStatement(
                        "stateHolder.$fragStateHolderFieldNameForClass!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                    )
                    funBuilder.endControlFlow()
                    funBuilder.endControlFlow()
                } else {
                    funBuilder.addStatement(
                        "stateHolder.$fragStateHolderFieldNameForClass!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                    )
                }
            }
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForTag[component.tag!!] = fragStateHolder"
            )
            funBuilder.endControlFlow() // close is (TAG)
            funBuilder.beginControlFlow("FragmentIdentificationStrategy.ID -> {")
            val fragStateHolderFieldNameForId =
                "${lowercaseFirstLetter(fragmentStateHolderTypeSpec.name!!)}ById"
            funBuilder.addStatement("val fragStateHolder = ${fragmentStateHolderTypeSpec.name!!}()")
            configChangeProps.forEach { ksPropertyDeclaration ->
                // during save, nullability doesn't matter
                if (ksPropertyDeclaration.modifiers.contains(Modifier.LATEINIT)) {
                    funBuilder.beginControlFlow("run {")
                    funBuilder.beginControlFlow("val isInitialized = try {")
                    funBuilder.addStatement("component.${ksPropertyDeclaration.simpleName.asString()}")
                    funBuilder.addStatement("true")
                    funBuilder.endControlFlow()
                    funBuilder.beginControlFlow("catch (ex: UninitializedPropertyAccessException) {")
                    funBuilder.addStatement("false")
                    funBuilder.endControlFlow()
                    funBuilder.beginControlFlow("if (isInitialized) {")
                    funBuilder.addStatement(
                        "stateHolder.$fragStateHolderFieldNameForClass!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                    )
                    funBuilder.endControlFlow()
                    funBuilder.endControlFlow()
                } else {
                    funBuilder.addStatement(
                        "stateHolder.$fragStateHolderFieldNameForClass!!.${ksPropertyDeclaration.simpleName.asString()} = component.${ksPropertyDeclaration.simpleName.asString()}"
                    )
                }
            }
            funBuilder.addStatement(
                "stateHolder.$fragStateHolderFieldNameForId[component.id] = fragStateHolder"
            )
            funBuilder.endControlFlow() // close is (ID)
            funBuilder.endControlFlow() // close when (id strat)

            funBuilder.endControlFlow() // close is (specific fragment)
        }
        funBuilder.endControlFlow() // close is (fragment)
        funBuilder.endControlFlow() // close when inner
        funBuilder.endControlFlow() // close when outer
            .addStatement("return stateHolder")
        return funBuilder.build()
    }

    private fun generateTopLevelStateHolder(
        packageName: String, resolver: Resolver,
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        stateHoldersForComponents: MutableMap<String, TypeSpec>
    ): TypeSpec {
        val builder = TypeSpec.classBuilder("GeneratedStateHolder")
            .addSuperinterface(ClassName(DEFAULT_PACKAGE_NAME, "StateHolder"))
        componentToStateMap.forEach { componentEntry ->
            val stateHolderEntry =
                stateHoldersForComponents[componentEntry.key]!!
            val classDecl = resolver.getClassDeclarationByName(componentEntry.key)!!
            if (isSubclassOf(classDecl, COMPONENT_ACTIVITY_QUALIFIED_NAME)) {
                builder.addProperty(
                    PropertySpec.builder(
                        lowercaseFirstLetter(stateHolderEntry.name!!),
                        ClassName(packageName, stateHolderEntry.name!!).copy(nullable = true)
                    )
                        .mutable(true)
                        .initializer("%L", null)
                        .build()
                )
            } else if (isSubclassOf(
                    classDecl,
                    "$packageName.$DEFAULT_STATE_SAVING_FRAGMENT_SIMPLE_NAME"
                )
            ) {
                builder.addProperty(
                    PropertySpec.builder(
                        "${lowercaseFirstLetter(stateHolderEntry.name!!)}ByClass",
                        ClassName(packageName, stateHolderEntry.name!!).copy(nullable = true)
                    )
                        .mutable(true)
                        .initializer("%L", null)
                        .build()
                )
                val mutableMapIntToHolder = MUTABLE_MAP
                    .parameterizedBy(
                        INT,
                        ClassName(packageName, stateHolderEntry.name!!)
                    )
                val mutableMapStringToHolder = MUTABLE_MAP
                    .parameterizedBy(
                        STRING,
                        ClassName(packageName, stateHolderEntry.name!!)
                    )
                builder.addProperty(
                    PropertySpec.builder(
                        "${lowercaseFirstLetter(stateHolderEntry.name!!)}ById",
                        mutableMapIntToHolder
                    )
                        .mutable(true)
                        .initializer("%L", "mutableMapOf()")
                        .build()
                )
                builder.addProperty(
                    PropertySpec.builder(
                        "${lowercaseFirstLetter(stateHolderEntry.name!!)}ByTag",
                        mutableMapStringToHolder
                    )
                        .mutable(true)
                        .initializer("%L", "mutableMapOf()")
                        .build()
                )
            }
        }
        return builder.build()
    }

    private fun lowercaseFirstLetter(str: String) =
        str.replaceFirstChar { it.lowercase(Locale.getDefault()) }

    @OptIn(KspExperimental::class)
    private fun generateStateHoldersForComponents(
        resolver: Resolver,
        componentToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>
    ): MutableMap<String, TypeSpec> {
        val ret = mutableMapOf<String, TypeSpec>()
        val simpleNames = mutableSetOf<String>()
        componentToStateMap.forEach { entry ->
            val classDecl = resolver.getClassDeclarationByName(entry.key)!!
            val componentStateHolderPrefix = if (simpleNames.contains(classDecl.simpleName.asString())) {
                classDecl.qualifiedName!!.asString().replace('.', '_')
            } else {
                classDecl.simpleName.asString()
            }
            val componentStateHolderName = componentStateHolderPrefix + "State"
            simpleNames.add(componentStateHolderPrefix)
            val typeSpecBuilder =
                TypeSpec.classBuilder(componentStateHolderName)
            entry.value.forEach { propDecl ->
                val retainAnn = propDecl.getAnnotationsByType(Retain::class).toList().first()
                // only put into the state holder if we're retaining across config change
                if (retainAnn.across.contains(StateDestroyingEvent.CONFIGURATION_CHANGE)) {
                    val resolvedType = propDecl.type.resolve()
                    // TODO consider NOT doing this at all
                    // using a default value is probably not needed / dumb
                    // could just make all StateHolder fields nullable
                    val canUseDefaultVal = isBuiltInWithDefaultValue(resolver, resolvedType)
                    val propBuilder = PropertySpec.builder(
                        propDecl.simpleName.asString(),
                        if (canUseDefaultVal) resolvedType.toTypeName() else resolvedType.makeNullable()
                            .toTypeName()
                    )
                        .mutable(true)

                    if (!canUseDefaultVal) {
                        propBuilder.initializer("%L", null)
                    } else {
                        when (resolvedType) {
                            resolver.builtIns.booleanType -> {
                                propBuilder.initializer(
                                    "%L",
                                    false
                                ) // TODO initial value not supported by KSP? https://github.com/google/ksp/issues/642
                            }

                            resolver.builtIns.stringType -> {
                                propBuilder.initializer("%L", "\"\"")
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
                                loge("unsupported type ${resolvedType.toClassName().canonicalName}")
                            }
                        }
                    }

                    typeSpecBuilder.addProperty(
                        propBuilder.build()
                    )
                }
            }
            ret[entry.key] = typeSpecBuilder.build()
        }
        return ret
    }
}

fun String.toMD5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}