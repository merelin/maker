/*
 * Copyright (c) 2011-2012, Alex McGuire, Louis Botterill
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package maker.task.tasks

import maker.project._
import maker.task._
import maker.utils.FileUtils._
import maker.utils.Stopwatch
import maker.utils.maven.IvyLock
import org.apache.commons.io.FileUtils._

/**
 * Creates jars that are ready for deployment.
 */
case class CreateDeployTask(project: Project, buildTests: Boolean, version: Option[String] = None) extends Task {
  def baseProject = project

  private val log = project.log
  val baseOutputDir = file(project.rootAbsoluteFile, "/target-maker/deploy/")
  val jarsDir = file(baseOutputDir, "/jars/")
  val thirdPartyJars = file(baseOutputDir, "/thirdpartyjars/")
  val binDir = file(baseOutputDir, "/bin/")

  def name = "Create Deploy"

  def module = project
  def upstreamTasks = project.allUpstreamModules.map(PackageMainJarTask) ::: {
    if (buildTests) project.allUpstreamModules.map(PackageTestJarTask)
    else Nil
  }

  def exec(results: Iterable[TaskResult], sw: Stopwatch) = {
    println("Running CreateDeployTask")
    IvyLock.synchronized {
      doPublish()
    }
    DefaultTaskResult(this, true, sw)
  }

  protected def doPublish(): Unit = {
    log.info("Creating deployment directory: " + baseOutputDir)
    baseOutputDir.deleteAll()
    baseOutputDir.mkdirs()

    copyDirectoryAndPreserve(file(project.rootAbsoluteFile, "/bin/"), binDir)

    val appJars = project.allUpstreamModules map { m =>
      val out = file(jarsDir, m.outputArtifact.getName)
      copyFile(m.outputArtifact, out)
      out.relativeTo(baseOutputDir).getPath
    }

    val tpJars = for {
      m <- project.allModules
      j <- m.classpathJars
    } yield {
      val out = file(thirdPartyJars, "/" + j.getName)
      copyFile(j, out)
      out.relativeTo(baseOutputDir).getPath
    }

    val classpathString = (appJars ::: tpJars).distinct.mkString(":")
    writeToFile(file(baseOutputDir, "/bin/deploy-classpath.sh"), "export CLASSPATH=" + classpathString)

    if (buildTests) {
      val testJarsDir = file(baseOutputDir, "/testjars/")
      testJarsDir.mkdirs()
      val testJars = project.allUpstreamModules map { m =>
        val out = file(testJarsDir, m.testOutputArtifact.getName)
        copyFile(m.testOutputArtifact, out)
        out.relativeTo(baseOutputDir).getPath
      }

      val testClasspathString = (appJars ::: tpJars ::: testJars).distinct.mkString(":")
      writeToFile(file(baseOutputDir, "/bin/deploy-test-classpath.sh"), "export CLASSPATH=" + testClasspathString)
      version.foreach(v => writeToFile(file(project.rootAbsoluteFile, "/version.txt"), v))
    }
  }
}
