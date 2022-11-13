package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.references.ActionReference
import com.github.aarcangeli.githubactions.references.InputPropertyReference
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class GHAReferenceContributor : PsiReferenceContributor(), DumbAware {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(YAMLScalar::class.java).withLanguage(YAMLLanguage.INSTANCE),
      GHAReferenceProvider()
    )
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(YAMLKeyValue::class.java).withLanguage(YAMLLanguage.INSTANCE),
      InputReferenceProvider()
    )
  }

  private class GHAReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      // verify it is a valid step
      GHAUtils.getStepFromUses(element as YAMLScalar) ?: return PsiReference.EMPTY_ARRAY
      return arrayOf(ActionReference(element))
    }
  }

  private class InputReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      // verify it is a valid with attribute
      GHAUtils.getStepFromInput(element as YAMLKeyValue) ?: return PsiReference.EMPTY_ARRAY
      return arrayOf(InputPropertyReference(element))
    }
  }
}
