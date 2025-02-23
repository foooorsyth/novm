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
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
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
        val fileSpec = FileSpec.builder(packageName, fileName)
            .addImport("android.os", "Bundle")
            .addImport("androidx.annotation", "CallSuper")
            .addImport("androidx.appcompat.app", "AppCompatActivity")
            .addTypes(stateHoldersForActivities.values)
            .addType(topLevelStateHolder)
            .addType(stateSaver)
            /*
            .addCode("\nopen class StateSavingActivity : AppCompatActivity() {\n" +
                    "\n" +
                    "    val stateSaver = StateSaver()\n" +
                    "\n" +
                    "    @CallSuper\n" +
                    "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                    "        super.onCreate(savedInstanceState)\n" +
                    "\n" +
                    "        // Restore config change proof state\n" +
                    "        @Suppress(\"DEPRECATION\")\n" +
                    "        (lastCustomNonConfigurationInstance as? StateHolder)?.let { retainedState ->\n" +
                    "            stateSaver.restoreStateConfigChange(this, retainedState)\n" +
                    "        }\n" +
                    "\n" +
                    "        // Restore process death proof state\n" +
                    "        if (savedInstanceState != null) {\n" +
                    "            stateSaver.restoreStateBundle(this, savedInstanceState)\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    @CallSuper\n" +
                    "    override fun onSaveInstanceState(outState: Bundle) {\n" +
                    "        stateSaver.saveStateBundle(this, outState)\n" +
                    "        super.onSaveInstanceState(outState)\n" +
                    "    }\n" +
                    "\n" +
                    "    @Suppress(\"OVERRIDE_DEPRECATION\")\n" +
                    "    @CallSuper\n" +
                    "    override fun onRetainCustomNonConfigurationInstance(): Any? {\n" +
                    "        return stateSaver.saveStateConfigChange(this)\n" +
                    "    }\n" +
                    "}")
             */
            .build()
        fileSpec.writeTo(codeGenerator, Dependencies(true, *activityContainingFiles.toTypedArray()))
    }

    private fun generateStateSaver(packageName: String,
                                   resolver: Resolver,
                                   activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
                                   topLevelStateHolder: TypeSpec,
                                   stateHoldersForActivities: MutableMap<String, TypeSpec>,
                                   ) : TypeSpec {
        val builder = TypeSpec.classBuilder("StateSaver")
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
        return builder.build()
    }

    private fun generateRestoreStateConfigChange(
        packageName: String,
        resolver: Resolver,
        activityToStateMap: MutableMap<String, MutableList<KSPropertyDeclaration>>,
        topLevelStateHolder: TypeSpec,
        stateHoldersForActivities: MutableMap<String, TypeSpec>) : FunSpec {
        val funBuilder = FunSpec.builder("restoreStateConfigChange")
        // TODO implement
        return funBuilder.build()
    }
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
            entry.value.forEach { propertyDecl ->
                val resolveTypeOfProp = propertyDecl.type.resolve()
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

    private fun isBuiltInWithDefaultValue(resolver: Resolver, ksType: KSType) : Boolean {
        return when (ksType) {
            resolver.builtIns.booleanType,
            resolver.builtIns.stringType,
            resolver.builtIns.intType,
            resolver.builtIns.longType,
            resolver.builtIns.floatType,
            resolver.builtIns.doubleType,
            resolver.builtIns.byteType,
            resolver.builtIns.shortType,
            resolver.builtIns.charType,
            resolver.builtIns.numberType -> {
                true
            }
            else -> {
                false
            }
        }
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
        logger.info("novm finished")
    }

    override fun onError() {
        logger.info("novm onError")
    }
}