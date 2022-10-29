package com.github.aarcangeli.githubactions.domain

enum class ShellType {
  Cmd,
  Bash,
  PowerShell,
  PowerShellCore,
  Python,

  /**
   * Custom shell specified by the user
   *
   * @see <a href="https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#custom-shell">Custom Shell</a>
   */
  Custom
}
