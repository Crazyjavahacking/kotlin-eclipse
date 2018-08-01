package org.jetbrains.kotlin.core.model

import org.eclipse.core.internal.resources.Workspace
import org.eclipse.core.resources.IWorkspaceRoot
import org.eclipse.core.resources.ResourcesPlugin
import org.jetbrains.kotlin.core.log.KotlinLogger
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
                val p = ResourcesPlugin.getWorkspace().root.findFilesForLocationURI(scriptContents.file?.toURI())
                KotlinLogger.logInfo("$p")
                return ScriptDependencies.Empty.asSuccess()
            }

        }
}