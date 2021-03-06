package maker.project

import com.typesafe.zinc.Compiler
import java.io.{ File, FileWriter, Writer }
import maker.MakerProps
import org.apache.commons.io.FileUtils

class EnsimeGenerator(props: MakerProps) {
  def generateModules(root: File, name: String, modules: List[Module]): Unit = {
    val writer = new FileWriter(new File(root, ".ensime"))
    try {
      writer.append(";; Generated by Maker\n\n")
      writer.append("(\n")
      writer.append("  :scala-version \"" + props.ProjectScalaVersion.stringValue + "\"\n")
      writer.append("  :java-flags (\"-Xmx8g\" \"-XX:+UseConcMarkSweepGC\" \"-Xss2m\")\n") // hack
      writer.append("  :java-home \"" + props.JavaHome().getAbsolutePath + "\"\n")

      writer.append("  :root-dir \"" + root.getAbsolutePath + "\"\n")
      writer.append("  :cache-dir \"" + new File(root.getAbsolutePath, ".ensime_cache") + "\"\n")
      writer.append("  :name \"" + name + "\"\n")

      // Java and Scala sources are shared between all modules
      writer.append("  :reference-source-roots (\n")
      writer.append("    \"" + new File(props.JavaHome(), "src.zip").getAbsolutePath + "\"\n")
      writer.append("  )\n")

      writer.append("  :subprojects (\n")

      modules.foreach(appendModule(writer, _))

      writer.append(
        """  )
          |)
          |""".stripMargin)
    } finally writer.close()
  }

  private def appendModule(writer: Writer, module: Module): Unit = {
    writer.append("    (\n")
    writer.append("      :name \"" + module.name + "\"\n")
    writer.append("      :depends-on-modules (\n")
    module.immediateUpstreamModules.foreach { dep =>
      writer.append("        \"" + dep.name + "\"\n")
    }
    writer.append("      )\n")

    writer.append("      :compile-deps (\n")
    // bit of redundancy, add the scala-library to all modules
    writer.append("        \"" + props.ProjectScalaLibraryJar().getAbsolutePath + "\"\n")

    def appendDeps(m: Module): Unit = {
      // hack: avoid duplicates already pulled in by upstream
      def got(m: Module): List[String] =
        m.managedJars.map(_.getName).toList ::: m.immediateUpstreamModules.flatMap(got)
      val existing = m.immediateUpstreamModules.flatMap(got)
      m.managedJars.filterNot { jar =>
        existing.contains(jar.getName)
      }.foreach { dep =>
        writer.append("        \"" + dep.getAbsolutePath + "\"\n")
      }
    }
    appendDeps(module)
    writer.append("      )\n")

    writer.append("      :reference-source-roots (\n")
    writer.append("        \"" + props.ProjectScalaLibrarySourceJar().getAbsolutePath + "\"\n")
    def appendDepRefSrcs(m: Module): Unit = {
      if (!m.managedLibSourceDir.exists) return
      def archives(m: Module): List[String] = {
        val filenames = m.managedLibSourceDir.list
        if (filenames == null) Nil
        else filenames.toList.filter{ f =>
          f.endsWith(".jar") || f.endsWith(".zip")
        }
      }
      // hack: avoid duplicates already pulled in by upstream
      def got(m: Module): List[String] =
        archives(m) ::: m.immediateUpstreamModules.flatMap(got)
      val existing = m.immediateUpstreamModules.flatMap(got)
      archives(m) filterNot { jar =>
        existing.contains(jar)
        } foreach { dep =>
          val from = new File(m.managedLibSourceDir, dep)
          writer.append("        \"" + from.getAbsolutePath + "\"\n")
        }
    }
    appendDepRefSrcs(module)
    writer.append("      )\n")

    writer.append("      :source-roots (\n")
    writer.append("        " + module.sourceDirs.map(_.getAbsolutePath).mkString("\"","\" \"","\"") + "\n")
    writer.append("        " + module.testSourceDirs.map(_.getAbsolutePath).mkString("\"","\" \"","\"") + "\n")
    writer.append("      )\n")

    writer.append("      :target \"" + module.outputDir.getAbsolutePath + "\"\n")
    writer.append("      :test-target \"" + module.testOutputFile.getAbsolutePath + "\"\n")

    writer.append("    )\n")
  }
}
