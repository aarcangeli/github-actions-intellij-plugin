package com.github.aarcangeli.githubactions

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@TestDataPath("\$CONTENT_ROOT/testData")
abstract class GHABaseTestCase : BasePlatformTestCase() {
  override fun getTestDataPath() = "src/test/testData"
}
