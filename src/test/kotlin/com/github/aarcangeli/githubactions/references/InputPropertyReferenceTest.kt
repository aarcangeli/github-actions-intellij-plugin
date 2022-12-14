package com.github.aarcangeli.githubactions.references

import com.github.aarcangeli.githubactions.GHABaseTestCase
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import junit.framework.TestCase
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

internal class InputPropertyReferenceTest : GHABaseTestCase() {
  private lateinit var localPath: YAMLFile
  private lateinit var actionFile: YAMLFile

  override fun setUp() {
    super.setUp()

    localPath = myFixture.configureByFile("references/.github/workflows/localPath.yml") as YAMLFile

    // copy test files to the project folder
    actionFile = myFixture.configureByFile("references/.github/test-target/action.yml") as YAMLFile
  }

  fun testReference() {
    // verify local reference
    val reference = localPath.findReferenceAt(localPath.text.indexOf("value:"))
    TestCase.assertNotNull(reference)
    TestCase.assertNotNull(reference is InputPropertyReference)
    val resolved = reference!!.resolve()

    // verify resolved element
    TestCase.assertNotNull(resolved)
    TestCase.assertTrue(resolved is YAMLKeyValue)
    TestCase.assertEquals("value", (resolved as YAMLKeyValue).keyText)
  }

  fun testUsages() {
    // setup, get referenced value
    val offset = localPath.text.indexOf("value:")
    val inputDefinition = localPath.findReferenceAt(offset)!!.resolve() as YAMLKeyValue

    val references = ReferencesSearch.search(inputDefinition).findAll()
    TestCase.assertEquals(1, references.size)
    TestCase.assertTrue(references.first() is InputPropertyReference)
    TestCase.assertEquals(offset, references.first().element.textOffset)
    TestCase.assertFalse(references.first().isSoft)
  }

  fun testUsagesFromOffset() {
    // setup, get referenced value
    val inputDefinition = actionFile.findElementAt(actionFile.text.indexOf("value:"))!!.parentOfType<YAMLKeyValue>()!!

    val references = ReferencesSearch.search(inputDefinition).findAll()
    TestCase.assertEquals(1, references.size)
    TestCase.assertTrue(references.first() is InputPropertyReference)
    TestCase.assertEquals(references.first().element.text, "value: test-value")
    TestCase.assertFalse(references.first().isSoft)
  }
}
