package com.github.aarcangeli.githubactions.commands

/**
 * Represents a GitHub Action command.
 *
 * ES: <pre>"::workflow-command parameter1={data},parameter2={data}::{command value}"</pre>
 *
 * Ref: https://docs.github.com/en/actions/using-workflows/workflow-commands-for-github-actions
 */
class ActionCommand(val command: String) {

  private val properties: MutableMap<String, String> = HashMap()

  var data: String? = null
    private set

  fun getProperty(key: String): String? {
    return properties[key.lowercase()]
  }

  private fun addProperty(key: String, value: String) {
    assert(key.isNotEmpty())
    assert(value.isNotEmpty())
    properties[key.lowercase()] = value
  }

  companion object {
    /**
     * Parses a command string into an ActionCommand.
     */
    fun tryParse(message: String): ActionCommand? {
      val msg = message.trim()
      if (!msg.startsWith("::")) {
        return null
      }

      val commandEndIndex = msg.indexOf("::", 2)
      if (commandEndIndex < 0) {
        return null
      }

      // read command
      val cmdInfo = msg.substring(2, commandEndIndex)
      val spaceIndex = cmdInfo.indexOf(' ')
      val workflowCommand = if (spaceIndex < 0) cmdInfo else cmdInfo.substring(0, spaceIndex)
      val result = ActionCommand(workflowCommand.lowercase())

      // read properties
      if (spaceIndex > 0) {
        val properties = cmdInfo.substring(spaceIndex + 1).trim()
        for (property in properties.split(",")) {
          if (property.contains('=')) {
            val (key, value) = property.split('=')
            if (key.isNotEmpty() && value.isNotEmpty()) {
              result.addProperty(key, value)
            }
          }
        }
      }

      // read data
      val data = msg.substring(commandEndIndex + 2).trim()
      if (data.isNotEmpty()) {
        result.data = data
      }

      return result
    }
  }

}
