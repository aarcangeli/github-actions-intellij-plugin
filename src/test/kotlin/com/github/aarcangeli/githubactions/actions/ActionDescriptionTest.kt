package com.github.aarcangeli.githubactions.actions

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionDescriptionTest {

  @Test
  fun `Action and version`() {
    val action = ActionDescription.fromString("actions/checkout@v2")
    assertEquals("actions", action.owner)
    assertEquals("checkout", action.repo)
    assertNull(action.path)
    assertEquals("v2", action.ref)
    assertEquals("actions/checkout", action.getFullName())
  }

  @Test
  fun `Action, version and path 1`() {
    val action = ActionDescription.fromString("owner/repo/sub-action@v2")
    assertEquals("owner", action.owner)
    assertEquals("repo", action.repo)
    assertEquals("sub-action", action.path)
    assertEquals("v2", action.ref)
    assertEquals("owner/repo", action.getFullName())
  }

  @Test
  fun `Action, version and path 2`() {
    val action = ActionDescription.fromString("owner/repo/sub-action/sub-sub@v2")
    assertEquals("owner", action.owner)
    assertEquals("repo", action.repo)
    assertEquals("sub-action/sub-sub", action.path)
    assertEquals("v2", action.ref)
    assertEquals("owner/repo", action.getFullName())
  }

  @Test
  fun `missing version 1`() {
    val action = ActionDescription.fromString("actions/checkout")
    assertEquals("actions", action.owner)
    assertEquals("checkout", action.repo)
    assertNull(action.path)
    assertNull(action.ref)
  }

  @Test
  fun `missing version 2`() {
    val action = ActionDescription.fromString("actions/checkout@")
    assertEquals("actions", action.owner)
    assertEquals("checkout", action.repo)
    assertNull(action.path)
    assertNull(action.ref)
  }

  @Test
  fun `missing repo 1`() {
    val action = ActionDescription.fromString("actions")
    assertEquals("actions", action.owner)
    assertNull(action.repo)
    assertNull(action.path)
    assertNull(action.ref)
  }

  @Test
  fun `missing repo 2`() {
    val action = ActionDescription.fromString("actions@")
    assertEquals("actions", action.owner)
    assertNull(action.repo)
    assertNull(action.path)
    assertNull(action.ref)
  }

  @Test
  fun `missing repo 3`() {
    val action = ActionDescription.fromString("actions/")
    assertEquals("actions", action.owner)
    assertNull(action.repo)
    assertNull(action.path)
    assertNull(action.ref)
  }

  @Test
  fun `docker 1`() {
    val action = ActionDescription.fromString("docker://ubuntu:latest")
    assertNull(action.owner)
    assertNull(action.repo)
    assertNull(action.path)
    assertNull(action.ref)
    assertEquals("ubuntu", action.dockerPath)
    assertEquals("latest", action.dockerTag)
  }

  @Test
  fun `docker 2`() {
    val action = ActionDescription.fromString("docker://ghcr.io/OWNER/IMAGE_NAME")
    assertNull(action.owner)
    assertNull(action.repo)
    assertNull(action.path)
    assertNull(action.ref)
    assertEquals("ghcr.io/OWNER/IMAGE_NAME", action.dockerPath)
    assertNull(action.dockerTag)
  }

  @Test
  fun `validity action`() {
    assertTrue(ActionDescription.fromString("owner/action@main").isValid())
    assertTrue(ActionDescription.fromString("owner/action/path@main").isValid())
    assertTrue(ActionDescription.fromString("my-owner/my-action/path with spaces@versions/v1-2.3").isValid())
  }

  @Test
  fun `validity docker`() {
    assertTrue(ActionDescription.fromString("docker://ghcr.io/OWNER/IMAGE_NAME:tag").isValid())
  }

  @Test
  fun `validity local`() {
    assertTrue(ActionDescription.fromString("./my-dir/with space/hello").isValid())
  }

  @Test
  fun `validity invalid actions`() {
    // spaces
    assertFalse(ActionDescription.fromString("o wner/action@main").isValid())
    assertFalse(ActionDescription.fromString("owner/act ion@main").isValid())
    assertFalse(ActionDescription.fromString("owner/action@ma in").isValid())
    assertFalse(ActionDescription.fromString(" owner/action@main").isValid())
    assertFalse(ActionDescription.fromString("owner/action@main ").isValid())
    // missing ref
    assertFalse(ActionDescription.fromString("owner/action").isValid())
    assertFalse(ActionDescription.fromString("owner/action@").isValid())
    // missing ref
  }

}
