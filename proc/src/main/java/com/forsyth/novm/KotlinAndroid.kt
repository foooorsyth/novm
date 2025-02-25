package com.forsyth.novm

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

@Suppress("PrivateApi")
fun getBundleFunPostfix(resolver: Resolver, ksPropertyDeclaration: KSPropertyDeclaration) : String? {
    val ksType = ksPropertyDeclaration.type.resolve()
    when(ksType) {
        resolver.builtIns.stringType -> {
            return "String"
        }
        resolver.builtIns.intType -> {
            return "Int"
        }
        resolver.builtIns.booleanType -> {
            return "Boolean"
        }
        resolver.builtIns.doubleType -> {
            return "Double"
        }
        resolver.builtIns.longType -> {
            return "Long"
        }
    }
    // TODO more^

    val serializableClazz = Class.forName("java.io.Serializable").kotlin
    val parcelableClazz = Class.forName("android.os.Parcelable").kotlin
    // TODO Serializable, Parcelable, array variants, etc

    return null
}

fun isBuiltInWithDefaultValue(resolver: Resolver, ksType: KSType) : Boolean {
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

fun isSubclassOf(clazz: KSClassDeclaration, qualifiedNameOfSuper: String) : Boolean {
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
