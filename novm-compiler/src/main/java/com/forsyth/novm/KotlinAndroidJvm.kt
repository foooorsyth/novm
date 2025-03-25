package com.forsyth.novm

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.innerArguments
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName


/**
 * Returns true iff property is a builtin that is strictly mapped
 * to a JVM primitive. Does NOT include java.lang.Object or java.lang.String.
 * Also does not include nullable primitives as these cannot be placed in a Bundle
 */
fun getBundleFunPostfixForPrimitive(resolver: Resolver, ksType: KSType): String? {
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
    "kotlin.CharSequence" to "CharSequence",
    "kotlin.collections.ArrayList<kotlin.CharSequence>" to "CharSequenceArrayList",
    "kotlin.ShortArray" to "ShortArray",
    "kotlin.IntArray" to "IntArray",
    "kotlin.LongArray" to "LongArray",
    "kotlin.FloatArray" to "FloatArray",
    "kotlin.DoubleArray" to "DoubleArray",
    "kotlin.BooleanArray" to "BooleanArray",
    "kotlin.collections.ArrayList<kotlin.Int>" to "IntegerArrayList",
    "kotlin.collections.ArrayList<kotlin.String>" to "StringArrayList",
    "android.os.Bundle" to "Bundle",
    "android.util.Size" to "Size",
    "android.util.SizeF" to "SizeF",
    "android.os.IBinder" to "Binder",
)

fun getBundleFunPostfixForNonPrimitive(resolver: Resolver, ksType: KSType): String? {
    val mutatedType = if (ksType.isMarkedNullable) {
        ksType.makeNotNullable()
    } else {
        ksType
    }
    return BUNDLE_SUPPORTED_NULLABLE_TYPES[mutatedType.toTypeName().toString()]
}

enum class BundleFunPostfixCategory {
    NOT_APPLICABLE,
    NON_NULL_PRIMITIVE,
    NULLABLE_KNOWN_TYPE,
    ARRAY_WITH_COVARIANT_PARAMETER,
    SUBCLASS_SERIALIZABLE_OR_PARCELABLE
}

data class BundleFunPostfixRet(
    val postfix: String? = null,
    val category: BundleFunPostfixCategory
)

fun getBundleFunPostfix(
    resolver: Resolver,
    ksType: KSType,
    logger: KSPLogger? = null,
    isDebugLoggingEnabled: Boolean? = false): BundleFunPostfixRet {
    // eg. primitive, nullable non primitive, or subclass of serializable / parcel
    // also maybe return the nullability of the type
    val primStr = getBundleFunPostfixForPrimitive(resolver, ksType)
    // first check primitives
    if (primStr != null) {
        return BundleFunPostfixRet(
            primStr,
            BundleFunPostfixCategory.NON_NULL_PRIMITIVE
        )
    }
    val nonPrimStr = getBundleFunPostfixForNonPrimitive(resolver, ksType)
    if (nonPrimStr != null) {
        return BundleFunPostfixRet(
            nonPrimStr,
            BundleFunPostfixCategory.NULLABLE_KNOWN_TYPE
        )
    }
    val nonNullKsType = if (ksType.isMarkedNullable) {
        ksType.makeNotNullable()
    } else {
        ksType
    }

    if (ksType.innerArguments.isNotEmpty()) {
        if (ksType.innerArguments.size > 1) {
            glogd(logger, isDebugLoggingEnabled, "parameterized type with too many arguments (${ksType.toTypeName()}. giving up...")
            return BundleFunPostfixRet(
                null,
                BundleFunPostfixCategory.NOT_APPLICABLE
            )
        }
        val parameterizedTypeQualified = nonNullKsType.toTypeName().toString().split('<')[0] // fragile
        val innerClassDecl = resolver.getClassDeclarationByName(ksType.innerArguments[0].toTypeName().toString())
        if (innerClassDecl == null) {
            // Array with no param. Do we stop here? Or let this continue until we think it's a valid
            // Serializable? Think we should stop here
            glogd(logger, isDebugLoggingEnabled, "found parameterized type ${ksType.toTypeName()} with unresolvable first param. giving up...")
            return BundleFunPostfixRet(
                null,
                BundleFunPostfixCategory.NOT_APPLICABLE
            )
        }
        when (parameterizedTypeQualified) {
            "kotlin.Array", "kotlin.collections.ArrayList" -> {
                if (isSubclassOf(innerClassDecl, "kotlin.String", logger, isDebugLoggingEnabled)) {
                    return BundleFunPostfixRet(
                        if (parameterizedTypeQualified.endsWith("List")) "StringArrayList" else "StringArray",
                        BundleFunPostfixCategory.NULLABLE_KNOWN_TYPE
                    )
                } else if (isSubclassOf(innerClassDecl, "android.os.Parcelable", logger, isDebugLoggingEnabled)){
                    return BundleFunPostfixRet(
                        if (parameterizedTypeQualified.endsWith("List")) "ParcelableArrayList" else "ParcelableArray",
                        BundleFunPostfixCategory.ARRAY_WITH_COVARIANT_PARAMETER
                    )
                } else if (isSubclassOf(innerClassDecl, "kotlin.CharSequence", logger, isDebugLoggingEnabled)) {
                    return BundleFunPostfixRet(
                        if (parameterizedTypeQualified.endsWith("List")) "CharSequenceArrayList" else "CharSequenceArray",
                        BundleFunPostfixCategory.NULLABLE_KNOWN_TYPE
                    )
                } else {
                    glogd(logger, isDebugLoggingEnabled, "found Array with unsupported type param (not String, Parcelable or CharSequence). giving up...")
                    return BundleFunPostfixRet(
                        null,
                        BundleFunPostfixCategory.NOT_APPLICABLE
                    )
                }
            }
            "android.util.SparseArray" -> {
                return if (isSubclassOf(innerClassDecl, "android.os.Parcelable", logger, isDebugLoggingEnabled)){
                    BundleFunPostfixRet(
                        "SparseParcelableArray",
                        BundleFunPostfixCategory.ARRAY_WITH_COVARIANT_PARAMETER
                    )
                } else {
                    glogd(logger, isDebugLoggingEnabled, "found SparseArray with unsupported type param (not Parcelable). giving up...")
                    BundleFunPostfixRet(
                        null,
                        BundleFunPostfixCategory.NOT_APPLICABLE
                    )
                }
            }
            else -> {
                glogd(logger, isDebugLoggingEnabled, "found parameterized type ${ksType.toTypeName()} that's not supported...")
                BundleFunPostfixRet(
                    null,
                    BundleFunPostfixCategory.NOT_APPLICABLE
                )
            }
        }
    }
    val classDecl = try {
        nonNullKsType.declaration as KSClassDeclaration
    } catch (ex: Exception) {
        glogd(logger, isDebugLoggingEnabled, "type ${nonNullKsType.toTypeName()} for bundle type has no params and we cannot find class declaration. giving up...")
        return BundleFunPostfixRet(
            null,
            BundleFunPostfixCategory.NOT_APPLICABLE
        )
    }
    // NOTE: kotlin.Array is Serializable, so all Array<(out) T> covariant need to be checked first
    if (isSubclassOf(classDecl, "java.io.Serializable", logger, isDebugLoggingEnabled)) {
        return BundleFunPostfixRet(
            "Serializable",
            BundleFunPostfixCategory.SUBCLASS_SERIALIZABLE_OR_PARCELABLE
        )
    }
    // NOTE: Bundle and Persistable Bundle are Parcelable so they must be handled before we reach here
    // we won't support PersistableBundle (via putALL) because there's no corollary get function
    // we will only support putBundle/getBundle
    if (isSubclassOf(classDecl, "android.os.Parcelable", logger, isDebugLoggingEnabled)) {
        return BundleFunPostfixRet(
            "Parcelable",
            BundleFunPostfixCategory.SUBCLASS_SERIALIZABLE_OR_PARCELABLE
        )
    }
    return BundleFunPostfixRet(
        null,
        BundleFunPostfixCategory.NOT_APPLICABLE
    )
}

fun getBundleFunPostfix(
    resolver: Resolver,
    ksPropertyDeclaration: KSPropertyDeclaration,
    logger: KSPLogger? = null,
    isDebugLoggingEnabled: Boolean? = false
): BundleFunPostfixRet {
    return getBundleFunPostfix(resolver, ksPropertyDeclaration.type.resolve(), logger, isDebugLoggingEnabled)
}

fun isBuiltInWithDefaultValue(resolver: Resolver, ksType: KSType): Boolean {
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

fun isSubclassOf(classDecl: KSClassDeclaration,
                 qualifiedNameOfSuper: String,
                 logger: KSPLogger? = null,
                 isDebugLoggingEnabled: Boolean? = false
                 ): Boolean {

    val superTypeClassDecls = classDecl.superTypes.toList()
        .mapNotNull { ksTypeReference -> ksTypeReference.resolve().declaration as? KSClassDeclaration }
    glogd(logger, isDebugLoggingEnabled, "${classDecl.qualifiedName?.asString()} isSubclassOf supertypes: ${superTypeClassDecls.toList()}")
    if (classDecl.qualifiedName?.asString() == qualifiedNameOfSuper) {
        return true
    }
    if (superTypeClassDecls.isEmpty()) {
        return false
    }
    if (superTypeClassDecls.any { ksClassDeclaration -> ksClassDeclaration.qualifiedName?.asString() == qualifiedNameOfSuper }) {
        return true
    }
    // recursively try supertypes
    return superTypeClassDecls
        .map { classDeclInterior -> isSubclassOf(classDeclInterior, qualifiedNameOfSuper, logger, isDebugLoggingEnabled) }
        .reduce { acc, b -> acc || b }
}
