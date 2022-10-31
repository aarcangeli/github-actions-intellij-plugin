package com.github.aarcangeli.githubactions.providers.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/ReplaceDeprecatedCommandQuickFix")
class ReplaceDeprecatedCommandQuickFixTest : BasePlatformTestCase() {

  override fun getTestDataPath() = "src/test/testData/inspections/ReplaceDeprecatedCommandQuickFix"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(DeprecatedCommandsInspection())
  }

  @Test
  fun testAddPath1() {
    myFixture.configureByFile(".github/workflows/addPath1.yml")
    val fixes = myFixture.getAllQuickFixes()
    assertEquals(1, fixes.size)
    myFixture.launchAction(fixes[0])
    myFixture.checkResultByFile(".github/workflows/addPath1_after.yml")
  }
}
