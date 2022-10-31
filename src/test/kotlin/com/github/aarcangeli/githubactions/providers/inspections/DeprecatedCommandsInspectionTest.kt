package com.github.aarcangeli.githubactions.providers.inspections

import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/DeprecatedCommandsInspection")
class DeprecatedCommandsInspectionTest : BasePlatformTestCase() {

  override fun getTestDataPath() = "src/test/testData/inspections/DeprecatedCommandsInspection"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(DeprecatedCommandsInspection())
  }

  fun testAddPath() {
    doTestHighlighting(".github/workflows/addPath.yml")
  }

  fun testSaveState() {
    doTestHighlighting(".github/workflows/saveState.yml")
  }

  fun testSetEnv() {
    doTestHighlighting(".github/workflows/setEnv.yml")
  }

  fun testSetOutput() {
    doTestHighlighting(".github/workflows/setOutput.yml")
  }

  fun testCommandInScalarText() {
    doTestHighlighting(".github/workflows/commandInScalarText.yml")
  }

  fun testCommandInQuoteText1() {
    doTestHighlighting(".github/workflows/commandInQuoteText1.yml")
  }

  fun testCommandInQuoteText2() {
    doTestHighlighting(".github/workflows/commandInQuoteText2.yml")
  }

  fun testOutsideWorkflows() {
    doTestHighlighting(".github/outsideWorkflows.yml")
  }

  private fun doTestHighlighting(@TestDataFile filePaths: String) {
    myFixture.testHighlighting(true, false, false, filePaths)
  }

}
