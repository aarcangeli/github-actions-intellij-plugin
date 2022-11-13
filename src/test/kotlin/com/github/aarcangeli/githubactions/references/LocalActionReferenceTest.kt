package com.github.aarcangeli.githubactions.references

import com.github.aarcangeli.githubactions.GHABaseTestCase
import com.intellij.psi.search.searches.ReferencesSearch
import junit.framework.TestCase
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar

internal class LocalActionReferenceTest : GHABaseTestCase() {

  private lateinit var localPath: YAMLFile
  private lateinit var actionFile: YAMLFile

  override fun setUp() {
    super.setUp()

    localPath = myFixture.configureByFile("references/.github/workflows/localPath.yml") as YAMLFile

    // copy test files to the project folder
    actionFile = myFixture.configureByFile("references/.github/test-target/action.yml") as YAMLFile
  }

  fun testLocalPath() {
    // verify local reference
    val reference = localPath.findReferenceAt(localPath.text.indexOf("./.github/test-target"))!!
    TestCase.assertNotNull(reference is LocalActionReference)
    val resolved = reference.resolve()
    TestCase.assertNotNull(resolved)
    TestCase.assertEquals(resolved, actionFile)
  }

  fun testUsages() {
    // setup, get referenced value
    val references = ReferencesSearch.search(actionFile).findAll()
    TestCase.assertEquals(references.size, 1)
    TestCase.assertTrue(references.first() is LocalActionReference)
    TestCase.assertTrue(references.first().element is YAMLScalar)
    TestCase.assertEquals(references.first().element.containingFile, localPath)
    TestCase.assertEquals(references.first().element.text, "./.github/test-target")
  }
}
