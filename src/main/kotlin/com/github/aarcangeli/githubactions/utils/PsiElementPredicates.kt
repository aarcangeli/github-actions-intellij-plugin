package com.github.aarcangeli.githubactions.utils

import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue

class KeyTextCondition(private val keyText: String) : PatternCondition<YAMLKeyValue>("has key $keyText") {
  override fun accepts(t: YAMLKeyValue, context: ProcessingContext?): Boolean {
    return t.keyText == keyText
  }
}
