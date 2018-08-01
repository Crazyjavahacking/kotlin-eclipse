package org.jetbrains.kotlin.core.model

import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.ScriptDefinitionProvider

private const val EXTENSION_POINT_ID = "org.jetbrains.kotlin.core.scriptDefinitionContribution"

class EclipseScriptDefinitionProvider: ScriptDefinitionProvider {
    private val scriptDefinitions: List<KotlinScriptDefinition> by lazy {
        loadExecutableEP<ScriptDefinitionContribution>(EXTENSION_POINT_ID)
                .mapNotNull { it.createProvider() }
                .sortedByDescending { it.priority }
                .map { it.definition }
    }

    override fun findScriptDefinition(fileName: String) =
            scriptDefinitions.firstOrNull { it.isScript(fileName) }

    override fun isScript(fileName: String) =
        scriptDefinitions.any { it.isScript(fileName) }
}

interface ScriptDefinitionContribution {
    val priority: Int
    val definition: KotlinScriptDefinition
}