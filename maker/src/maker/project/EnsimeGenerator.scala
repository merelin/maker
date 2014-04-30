package maker.project

import com.typesafe.zinc.Compiler
import java.io.{ File, FileWriter, Writer }
import maker.MakerProps
import maker.utils.Zip
import org.apache.commons.io.FileUtils

class EnsimeGenerator(props: MakerProps) {
  def generateModules(root: File, name: String, modules: List[Module]): Unit = {
    val writer = new FileWriter(new File(root, ".ensime"))
    try {
      writer.append(";; Generated by Maker\n\n")
      writer.append("(\n")
      writer.append("  :root-dir \"" + root.getAbsolutePath + "\"\n")
      writer.append("  :name \"" + name + "\"\n")

      // Java and Scala sources are shared between all modules
      val sharedSrcRefs = new File(root, "lib_srcdir_managed")
      writer.append("  :reference-source-roots (\"" + sharedSrcRefs.getAbsolutePath + "\")\n")
      val javaSrc = new File(props.JavaHome(), "src.zip")
      Zip.unzip(javaSrc, sharedSrcRefs)
      Zip.unzip(props.ProjectScalaLibrarySourceJar(), sharedSrcRefs)

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
    writer.append("      :module-name \"" + module.name + "\"\n")

    writer.append("      :depends-on-modules (\n")
    module.immediateUpstreamModules.foreach { dep =>
      writer.append("        \"" + dep.name + "\"\n")
    }
    writer.append("      )\n")

    writer.append("      :compile-deps (\n")
    def appendDeps(m: Module): Unit = {
      m.managedJars.foreach { dep =>
        writer.append("        \"" + dep.getAbsolutePath + "\"\n")
      }
    // BUG in ensime, it doesn't properly do transitive dependencies
    //m.immediateUpstreamModules.foreach(appendDeps)
    }
    appendDeps(module)

    writer.append("      )\n")

    // hack until src jars are supported
    val depSrcs = module.managedLibSourceDir
    if (depSrcs.exists) {
      val extracted = module.managedLibSourceDirDir
      FileUtils.deleteDirectory(extracted)
      extracted.mkdirs()

      module.managedLibSourceDir.list.filter(_.endsWith(".jar")).foreach { dep =>
        val from = new File(module.managedLibSourceDir, dep)
        props.log.debug("extracting " + from + " into " + extracted)
        Zip.unzip(from, extracted)
      }
    }

    writer.append("      :reference-source-roots (\n")
    def appendDepRefSrcs(m: Module): Unit = {
      writer.append("        \"" + m.managedLibSourceDirDir.getAbsolutePath() + "\"\n")
      // BUG in ensime, it doesn't do transitive reference sources
      //m.immediateUpstreamModules.foreach(appendDepRefSrcs)
    }
    appendDepRefSrcs(module)
    writer.append("      )\n")

    // TODO: sources for java and scala standard libraries

    writer.append("      :source-roots (\n")
    writer.append("        \"" + module.sourceDir.getAbsolutePath + "\"\n")
    writer.append("        \"" + module.testSourceDir.getAbsolutePath + "\"\n")
    writer.append("      )\n")

    writer.append("      :target \"" + module.outputDir.getAbsolutePath + "\"\n")
    writer.append("      :test-target \"" + module.testOutputFile.getAbsolutePath + "\"\n")

    writer.append("    )\n")
  }
}