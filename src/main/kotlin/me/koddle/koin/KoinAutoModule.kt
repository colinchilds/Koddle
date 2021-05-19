@file:Suppress("unused")

package me.koddle.koin

import com.google.common.reflect.ClassPath
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Definition
import org.koin.core.definition.DefinitionFactory
import org.koin.core.definition.Kind
import org.koin.core.definition.Options
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.experimental.builder.getArguments
import org.koin.experimental.builder.getFirstJavaConstructor
import kotlin.reflect.KClass

fun buildModulesForPackage(pkg: String): Module {
    val classes = getPackageClasses(pkg)
    return module {
        for (kclass in classes) {
            single(kclass) { create(kclass, this) }
        }
    }
}

@Suppress("UnstableApiUsage")
fun getPackageClasses(pkg: String): List<KClass<*>> {
    return ClassPath
        .from(ClassLoader.getSystemClassLoader())
        .getTopLevelClassesRecursive(pkg)
        .map { Class.forName(it.name).kotlin }
        .toList()
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> create(kClass: KClass<T>, context: Scope): T {
    lateinit var instance: T
    val ctor = kClass.getFirstJavaConstructor()
    val args = getArguments(ctor, context)
    instance = ctor.newInstance(*args) as T
    return instance
}

fun <T: Any> Module.single(
    kclass: KClass<out T>,
    qualifier: Qualifier? = null,
    createdAtStart: Boolean = false,
    override: Boolean = false,
    definition: Definition<T>
): BeanDefinition<T> {
    val beanDefinition = DefinitionFactory.createSingle(
        kclass,
        qualifier,
        definition = definition)
    declareDefinition(beanDefinition, Options(createdAtStart, override))
    return beanDefinition
}

fun <T: Any> DefinitionFactory.createSingle(
    kclass: KClass<out T>,
    qualifier: Qualifier? = null,
    scopeName: Qualifier? = null,
    definition: Definition<T>
): BeanDefinition<T> {
    return DefinitionFactory.createDefinition(
        kclass,
        qualifier,
        definition,
        Kind.Single,
        scopeName
    )
}

fun <T: Any> DefinitionFactory.createDefinition(
    kclass: KClass<out T>,
    qualifier: Qualifier?,
    definition: Definition<T>,
    kind: Kind,
    scopeName: Qualifier?
): BeanDefinition<T> {
    val beanDefinition = BeanDefinition<T>(qualifier, scopeName, kclass)
    beanDefinition.definition = definition
    beanDefinition.kind = kind
    return beanDefinition
}
