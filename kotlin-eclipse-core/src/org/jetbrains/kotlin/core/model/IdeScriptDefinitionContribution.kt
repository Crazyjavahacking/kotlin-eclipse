package org.jetbrains.kotlin.core.model

import org.jetbrains.kotlin.core.builder.KotlinPsiManager
import org.jetbrains.kotlin.core.utils.ProjectUtils
import org.jetbrains.kotlin.core.utils.asResource
import org.jetbrains.kotlin.core.utils.isInClasspath
import org.jetbrains.kotlin.core.utils.javaProject
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.asSuccess
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class IdeScriptDefinitionContribution: ScriptDefinitionContribution {
    override val priority: Int = 10

    override val definition: KotlinScriptDefinition
        get() = IdeScriptDefinition()
}

private class IdeScriptDefinition: KotlinScriptDefinition(ScriptTemplateWithArgs::class) {
    override val dependencyResolver: DependenciesResolver
        get() = object: DependenciesResolver {
            override fun resolve(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult {
                val scriptResource = scriptContents.file?.asResource
                val javaProject = scriptResource?.javaProject

                return if (scriptResource != null && scriptResource.isInClasspath && javaProject != null) {
                    ScriptDependencies(
                            classpath = scriptResource.javaProject?.let { ProjectUtils.collectClasspathWithDependenciesForBuild(it) }.orEmpty(),
                            sources = KotlinPsiManager.getFilesByProject(javaProject.project).map { it.location.toFile() }
                    ).asSuccess()
                } else {
                    ScriptDependencies.Empty.asSuccess()
                }
            }
        }
}