package org.jetbrains.kotlin.ui

import org.eclipse.core.resources.*
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.ui.PlatformUI
import org.jetbrains.kotlin.core.builder.KotlinPsiManager
import org.jetbrains.kotlin.core.model.KotlinAnalysisFileCache
import org.jetbrains.kotlin.core.model.KotlinScriptEnvironment
import org.jetbrains.kotlin.core.model.getEnvironment
import org.jetbrains.kotlin.core.model.runJob
import org.jetbrains.kotlin.script.ScriptDependenciesProvider
import org.jetbrains.kotlin.ui.editors.KotlinScriptEditor
import kotlin.script.experimental.dependencies.ScriptDependencies

class ScriptClasspathUpdater : IResourceChangeListener {
    override fun resourceChanged(event: IResourceChangeEvent) {
        val delta = event.delta ?: return
        delta.accept(object : IResourceDeltaVisitor {
            override fun visit(delta: IResourceDelta?): Boolean {
                val resource = delta?.resource ?: return false
                if (resource is IFile) {
                    tryUpdateScriptClasspath(resource)
                    return false
                }
                
                return true
            }
        })
    }
}

internal fun tryUpdateScriptClasspath(file: IFile) {
    if (findEditor(file) == null) return
    
    val environment = getEnvironment(file) as? KotlinScriptEnvironment ?: return

    val dependenciesProvider = ScriptDependenciesProvider.getInstance(environment.project)
    
    runJob("Check script dependencies", Job.DECORATE, null, {
    	val newDependencies = dependenciesProvider.getScriptDependencies(KotlinPsiManager.getParsedFile(file))
//        KotlinLogger.logInfo("Check for script definition: ${dependenciesProvider}")
//        KotlinLogger.logInfo("New dependencies: ${newDependencies?.classpath?.joinToString("\n") { it.absolutePath }}")
        StatusWithDependencies(Status.OK_STATUS, newDependencies)
    }) { event ->
        val editor = findEditor(file)
        val statusWithDependencies = event.result
        val newDependencies = (statusWithDependencies as? StatusWithDependencies)?.dependencies
        if (file.isAccessible && editor != null) {
//            KotlinLogger.logInfo("Set new dependencies!!")
        	editor.reconcile {
        		KotlinScriptEnvironment.updateDependencies(file, newDependencies)
        		KotlinAnalysisFileCache.resetCache()
            }
        }
        else {
//            KotlinLogger.logInfo("Don't set new dependencies: accessible (${file.isAccessible}), editor (${editor})")
        }
    }
}

private data class StatusWithDependencies(val status: IStatus, val dependencies: ScriptDependencies?): IStatus by status

private fun findEditor(scriptFile: IFile): KotlinScriptEditor? =
    PlatformUI.getWorkbench().workbenchWindows.asSequence()
            .flatMap { it.pages.asSequence() }
            .flatMap { it.editorReferences.asSequence() }
            .mapNotNull { it.getEditor(false) }
            .filterIsInstance(KotlinScriptEditor::class.java)
            .firstOrNull { it.eclipseFile == scriptFile }
