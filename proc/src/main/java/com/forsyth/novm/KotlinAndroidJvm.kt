package com.forsyth.novm

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType


/**
 * Returns true iff property is a builtin that is strictly mapped
 * to a JVM primitive. Does NOT include java.lang.Object or java.lang.String.
 * Also does not include nullable primitives as these cannot be placed in a Bundle
 */
fun getBundleFunPostfixForPrimitive(resolver: Resolver, ksType: KSType) : String? {
    if (ksType.isMarkedNullable) {
        return null
    }
    return when (ksType) {
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
        resolver.builtIns.charType -> {
            return "Char"
        }
        resolver.builtIns.byteType -> {
            return "Byte"
        }
        resolver.builtIns.shortType -> {
            return "Short"
        }
        resolver.builtIns.floatType -> {
            return "Float"
        }
        else -> {
            null
        }
    }
}

val BUNDLE_SUPPORTED_NULLABLE_TYPES = mapOf(
    "kotlin.String" to "String",
    "kotlin.ByteArray" to "ByteArray",
    "kotlin.CharArray" to "CharArray",
    "kotlin.ShortArray" to "ShortArray",
    "kotlin.IntArray" to "IntArray",
    "kotlin.LongArray" to "LongArray",
    "kotlin.FloatArray" to "FloatArray",
    "kotlin.DoubleArray" to "DoubleArray",
    "kotlin.BooleanArray" to "BooleanArray",
)

fun getBundleFunPostfixForNonPrimitive(resolver: Resolver, ksType: KSType) : String? {
    val mutatedType = if (ksType.isMarkedNullable) {
        ksType.makeNotNullable()
    } else {
        ksType
    }
    return BUNDLE_SUPPORTED_NULLABLE_TYPES[mutatedType.declaration.qualifiedName!!.asString()]
}

fun getBundleFunPostfix(resolver: Resolver, ksType: KSType) : String? {
    val primStr = getBundleFunPostfixForPrimitive(resolver, ksType)
    // first check primitives
    if (primStr != null) {
        return primStr
    }
    // next check arrays and ArrayList<out String!>
    // TODO handle ArrayList<out String!>
    val nonPrimStr = getBundleFunPostfixForNonPrimitive(resolver, ksType)
    if (nonPrimStr != null) {
        return nonPrimStr
    }
    // TODO handle Serializable, Parcelable, Bundle, etc
    return null
}

fun getBundleFunPostfix(resolver: Resolver, ksPropertyDeclaration: KSPropertyDeclaration) : String? {
    return getBundleFunPostfix(resolver, ksPropertyDeclaration.type.resolve())
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
