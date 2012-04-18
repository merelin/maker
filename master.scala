println("\n ** Loading master build..\n")

import java.util.Properties
import java.io.File
import org.apache.log4j.Level._
import org.apache.commons.io.FileUtils._
import maker.project.Project
import maker.project.TopLevelProject
import maker.project.ProjectLib
import maker.Props
import maker.utils.FileUtils._
import maker.utils.Log
import maker.utils.Log._
import maker.RichProperties._
import maker.os.Command
import maker.os.Command._

:load maker/common.scala
:load maker/utils.scala
:load maker/titan-model.scala
:load maker/starling-modules.scala
:load maker/titan-modules.scala
