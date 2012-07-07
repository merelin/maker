println("\n ** Loading Starling build..\n")

import java.util.Properties
import java.io.File
import org.apache.log4j.Level._
import org.apache.commons.io.FileUtils._
import maker.project._
import maker.project.TopLevelProject
import maker.project.ProjectLib
import maker.Props
import maker.utils.FileUtils._
import maker.utils.Log
import maker.utils.Log._
import maker.RichProperties._
import maker.utils.os.Command
import maker.utils.os.Command._
import maker.utils.ModuleId._
import maker.utils.GroupAndArtifact
import maker.task.BuildResult

//:load maker/common.scala
import Common._

//:load maker/utils.scala
import Utils._

// titan model is a bit of a mess, so model build stubbed out for now
//:load maker/titan-model.scala
import TitanModel._

//:load maker/starling-modules.scala
import Starling._

import Starling.starling._

println("\nStarling build loaded\n\nNote: for convenience the 'starling' project is in the root scope, clean, test etc will act on that unless a project is specified (e.g. utils.clean...)\n")

