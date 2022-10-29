package com.github.aarcangeli.githubactions.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ActionCommandTest {

  @Test
  fun parse() {
    val command = ActionCommand.tryParse("::workflow-command parameter1={data},parameter2={data}::{command value}")
    assertNotNull(command)
    assertEquals("workflow-command", command?.command)
    assertEquals("{data}", command?.getProperty("parameter1"))
    assertEquals("{data}", command?.getProperty("parameter2"))
    assertEquals("{command value}", command?.data)
  }

}
