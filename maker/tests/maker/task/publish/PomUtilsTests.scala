package maker.task.publish

import org.scalatest.FreeSpec
import maker.utils.FileUtils._
import maker.project.TestModule
import maker.project.Project
import maker.Props

class PomUtilsTests extends FreeSpec {
  "test generated file for module" in {
    withTempDir{
      dir => 
        val props = Props.initialiseTestProps(dir)
        val a = TestModule(file(dir, "a"), "a", props)
        val b = TestModule(file(dir, "b"), "b", props, List(a))
        val c = new Project("c", dir, List(b), props ++ ("GroupId", "PomUtilsTests"))

        // Not needed as we aren't running tests - and ivy will just try to get them
        file(dir, "a/external-resources").delete
        file(dir, "b/external-resources").delete

        assert(
          PomUtils.pomXml(c, "42") ===
          """|<?xml version="1.0" encoding="UTF-8"?>
             |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             |    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
             |
             |  <modelVersion>4.0.0</modelVersion>
             |  <groupId>PomUtilsTests</groupId>
             |  <artifactId>c</artifactId>
             |  <packaging>jar</packaging>
             |  <version>42</version>
             |  <dependencies>
             |    <dependency>
             |      <groupId>MakerTestGroupID</groupId>
             |      <artifactId>a</artifactId>
             |      <version>42</version>
             |      <scope>compile</scope>
             |    </dependency>
             |    <dependency>
             |      <groupId>MakerTestGroupID</groupId>
             |      <artifactId>b</artifactId>
             |      <version>42</version>
             |      <scope>compile</scope>
             |    </dependency>
             |  </dependencies>
             |</project>
             |""".stripMargin 
         )
    }

  }
}
