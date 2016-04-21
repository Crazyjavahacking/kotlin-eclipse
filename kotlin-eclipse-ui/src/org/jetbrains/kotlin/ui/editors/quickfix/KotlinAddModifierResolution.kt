/*******************************************************************************
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *******************************************************************************/
package org.jetbrains.kotlin.ui.editors.quickfix

import org.eclipse.core.resources.IFile
import org.eclipse.swt.graphics.Image
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.core.builder.KotlinPsiManager
import org.jetbrains.kotlin.eclipse.ui.utils.EditorUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.KtModifierList
import org.eclipse.jface.text.IDocument
import org.jetbrains.kotlin.psi.KtPsiFactory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.ui.editors.quickassist.replace
import org.jetbrains.kotlin.ui.editors.quickassist.insertAfter
import org.jetbrains.kotlin.ui.editors.quickassist.remove
import org.jetbrains.kotlin.ui.editors.quickassist.insertBefore
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Diagnostic
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.lexer.KtTokens

fun DiagnosticFactory<*>.createAddModifierFix(modifier: KtModifierKeywordToken): KotlinDiagnosticQuickFix {
    return createAddModifierFix(modifier, KtModifierListOwner::class.java)
}

fun <T : KtModifierListOwner> DiagnosticFactory<*>.createAddModifierFix(
        modifier: KtModifierKeywordToken, 
        modifierOwnerClass: Class<T>): KotlinDiagnosticQuickFix {
    val thisFactory = this
    return object : KotlinDiagnosticQuickFix {
        override fun getResolutions(diagnostic: Diagnostic): List<KotlinMarkerResolution> {
            val modifierListOwner = PsiTreeUtil.getNonStrictParentOfType(diagnostic.psiElement, modifierOwnerClass)
            if (modifierListOwner == null) return emptyList()
            
            if (modifier == KtTokens.ABSTRACT_KEYWORD && modifierListOwner is KtObjectDeclaration) return emptyList()
            
            return listOf(KotlinAddModifierResolution(modifierListOwner, modifier))
        }
        
        override fun canFix(diagnostic: Diagnostic): Boolean {
            return diagnostic.factory == thisFactory // this@createFactory ?
        }
    }
}

class KotlinAddModifierResolution(
        private val element: KtModifierListOwner,
        private val modifier: KtModifierKeywordToken) : KotlinMarkerResolution {
    
    companion object {
        private val modalityModifiers = setOf(ABSTRACT_KEYWORD, OPEN_KEYWORD, FINAL_KEYWORD)
        
        fun getElementName(modifierListOwner: KtModifierListOwner): String {
            var name: String? = null
            if (modifierListOwner is PsiNameIdentifierOwner) {
                val nameIdentifier = modifierListOwner.nameIdentifier
                if (nameIdentifier != null) {
                    name = nameIdentifier.text
                }
            } else if (modifierListOwner is KtPropertyAccessor) {
                name = modifierListOwner.namePlaceholder.text
            }
            if (name == null) {
                name = modifierListOwner.text
            }
            return "'$name'"
        }
    }
    
    override fun apply(file: IFile) {
        addModifier(element, modifier)
    }
    
    override fun getLabel(): String? {
        if (modifier in modalityModifiers) {
            return "Make ${getElementName(element)} ${modifier.value}"
        }
        return "Add '${modifier.value}' modifier"
    }
}

private fun addModifier(owner: KtModifierListOwner, modifier: KtModifierKeywordToken) {
    val ktFile = owner.getContainingKtFile()
    val eclipseFile = KotlinPsiManager.getEclipseFile(ktFile)
    if (eclipseFile == null) return 
    
    val elementDocument = EditorUtil.getDocument(eclipseFile)
    
    val modifierList = owner.modifierList
    if (modifierList == null) {
        val anchor = owner.firstChild!!
            .siblings(forward = true)
            .dropWhile { it is PsiComment || it is PsiWhiteSpace }
            .first()
        
        insertBefore(anchor, "${modifier.value} ", elementDocument)
        
        return
    } else {
        addModifier(modifierList, modifier, elementDocument)
    }
}

private fun addModifier(modifierList: KtModifierList, modifier: KtModifierKeywordToken, elementDocument: IDocument) {
    if (modifierList.hasModifier(modifier)) return
    
    val newModifier = KtPsiFactory(modifierList).createModifier(modifier)
    val modifierToReplace = MODIFIERS_TO_REPLACE[modifier]
            ?.mapNotNull { modifierList.getModifier(it) }
            ?.firstOrNull()

    if (modifier == FINAL_KEYWORD && !modifierList.hasModifier(OVERRIDE_KEYWORD)) {
        if (modifierToReplace != null) {
            remove(modifierToReplace, elementDocument)
            if (modifierList.firstChild == null) {
                remove(modifierList, elementDocument)
            }
        }
        
        return
    }
    if (modifierToReplace != null) {
        replace(modifierToReplace, newModifier.text, elementDocument)
    } else {
        val newModifierOrder = MODIFIERS_ORDER.indexOf(modifier)

        fun placeAfter(child: PsiElement): Boolean {
            if (child is PsiWhiteSpace) return false
            if (child is KtAnnotation) return true // place modifiers after annotations
            val elementType = child.node!!.elementType
            val order = MODIFIERS_ORDER.indexOf(elementType)
            return newModifierOrder > order
        }

        val lastChild = modifierList.getLastChild()
        val anchor = lastChild?.siblings(forward = false)?.firstOrNull(::placeAfter)
        
        if (anchor == null) return
        
        insertAfter(anchor, newModifier.text, elementDocument)
    }
}

private val MODIFIERS_TO_REPLACE = mapOf(
        OVERRIDE_KEYWORD to listOf(OPEN_KEYWORD),
        ABSTRACT_KEYWORD to listOf(OPEN_KEYWORD, FINAL_KEYWORD),
        OPEN_KEYWORD to listOf(FINAL_KEYWORD, ABSTRACT_KEYWORD),
        FINAL_KEYWORD to listOf(ABSTRACT_KEYWORD, OPEN_KEYWORD),
        PUBLIC_KEYWORD to listOf(PROTECTED_KEYWORD, PRIVATE_KEYWORD, INTERNAL_KEYWORD),
        PROTECTED_KEYWORD to listOf(PUBLIC_KEYWORD, PRIVATE_KEYWORD, INTERNAL_KEYWORD),
        PRIVATE_KEYWORD to listOf(PUBLIC_KEYWORD, PROTECTED_KEYWORD, INTERNAL_KEYWORD),
        INTERNAL_KEYWORD to listOf(PUBLIC_KEYWORD, PROTECTED_KEYWORD, PRIVATE_KEYWORD)
)

private val MODIFIERS_ORDER = listOf(PUBLIC_KEYWORD, PROTECTED_KEYWORD, PRIVATE_KEYWORD, INTERNAL_KEYWORD,
                                     FINAL_KEYWORD, OPEN_KEYWORD, ABSTRACT_KEYWORD,
                                     OVERRIDE_KEYWORD,
                                     INNER_KEYWORD, ENUM_KEYWORD, COMPANION_KEYWORD, INFIX_KEYWORD, OPERATOR_KEYWORD)