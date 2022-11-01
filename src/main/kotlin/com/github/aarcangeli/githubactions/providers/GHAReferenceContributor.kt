package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.domain.StepElement
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

class GHAReferenceContributor : PsiReferenceContributor(), DumbAware {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(YAMLScalar::class.java).withLanguage(YAMLLanguage.INSTANCE),
      GHAReferenceProvider()
    )
  }

  private class GHAReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      // verify it is a valid step
      getContainingStep(element as YAMLScalar) ?: return PsiReference.EMPTY_ARRAY

      val description = ActionDescription.fromString(element.textValue)

      // for standard actions and docker actions, open the repository page
      description.toUrl()?.let {
        return arrayOf(WebReference(element, it))
      }

      return emptyArray()
    }

    private fun getContainingStep(element: YAMLScalar): StepElement? {
      val uses = element.parent as? YAMLKeyValue ?: return null
      if (uses.keyText != "uses") return null
      val step = uses.parent as? YAMLMapping ?: return null
      return StepElement.fromYaml(step)
    }
  }
}
