package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.references.LocalActionReference
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.jetbrains.yaml.psi.YAMLFile

@TestDataPath("\$CONTENT_ROOT/testData/references")
class GHAReferenceContributorTest : BasePlatformTestCase() {
  override fun getTestDataPath() = "src/test/testData/references"

  fun testLocalPath() {
    // prepare test data
    val file = myFixture.configureByFile(".github/workflows/localPath.yml") as YAMLFile
    val targetFile = myFixture.configureByFile(".github/actions/test-action/action.yml")

    // verify local reference
    val reference = file.findReferenceAt(file.text.indexOf("/test-action"))
    TestCase.assertNotNull(reference)
    TestCase.assertNotNull(reference is LocalActionReference)
    val resolved = reference!!.resolve()
    TestCase.assertNotNull(resolved)
    TestCase.assertEquals(resolved, targetFile)
  }
}
