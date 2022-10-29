package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.MyBundle
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.jetbrains.annotations.Nls

private const val GITHUB_WORKFLOW_SCHEMA_URL = "https://json.schemastore.org/github-workflow.json"

class GHASchemaProviderFactory : JsonSchemaProviderFactory {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    return listOf<JsonSchemaFileProvider>(MyJsonSchemaFileProvider())
  }

  private class MyJsonSchemaFileProvider : JsonSchemaFileProvider {
    private var cachedVirtualFile: VirtualFile? = null

    override fun isAvailable(file: VirtualFile): Boolean {
      return GHAUtils.isWorkflowPath(file)
    }

    override fun getName(): @Nls String {
      return MyBundle.message("plugin.name")
    }

    override fun getSchemaFile(): VirtualFile? {
      if (cachedVirtualFile == null) {
        cachedVirtualFile = JsonFileResolver.urlToFile(GITHUB_WORKFLOW_SCHEMA_URL)
      }
      return cachedVirtualFile
    }

    override fun getSchemaType(): SchemaType {
      return SchemaType.remoteSchema
    }

    override fun getRemoteSource(): String {
      return GITHUB_WORKFLOW_SCHEMA_URL
    }
  }
}
