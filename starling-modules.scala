println("\n ** Loading Starling build...\n")


lazy val makerProps : Props = file("Maker.conf")
lazy val starlingProperties : Properties = file("props.conf")

def project(name : String) = new Project(
  name, 
  file(name),
  libDirs = List(file(name, "lib_managed"), file(name, "lib"), file(name, "maker-lib"), file(".maker/scala-lib")),
  resourceDirs = List(file(name, "resources"), file(name, "test-resources")),
  props = makerProps,
  unmanagedProperties = starlingProperties
)

lazy val manager = project("manager")
lazy val utils = project("utils") dependsOn manager
lazy val osgirun = project("osgirun").copy(libDirs = List(new File("osgirun/lib_managed"), new File("osgirun/lib"), new File("osgirun/osgi_jars")))
lazy val booter = project("booter")
lazy val concurrent = project("concurrent") dependsOn utils
lazy val titanReturnTypes = project("titan.return.types")
lazy val quantity = project("quantity") dependsOn(utils, titanReturnTypes)
lazy val osgiManager = project("osgimanager") dependsOn utils
lazy val singleClasspathManager = project("singleclasspathmanager") dependsOn osgiManager
lazy val pivot = project("pivot") dependsOn quantity
lazy val daterange = project("daterange") dependsOn(utils, titanReturnTypes)
lazy val pivotUtils = project("pivot.utils") dependsOn(daterange, pivot)
lazy val starlingDTOApi = project("starling.dto.api") dependsOn(titanReturnTypes :: utils :: trademgmtModelDeps : _*)
lazy val maths = project("maths") dependsOn (daterange, quantity)
lazy val props = project("props") dependsOn utils
lazy val auth = project("auth") dependsOn props
lazy val bouncyrmi = project("bouncyrmi") dependsOn auth
lazy val loopyxl = project("loopyxl") dependsOn auth
lazy val browserService = project("browser.service") dependsOn manager
lazy val browser = project("browser") dependsOn browserService
lazy val guiapi = project("gui.api") dependsOn (browserService, bouncyrmi, pivotUtils)
lazy val fc2Facility = project("fc2.facility") dependsOn guiapi
lazy val curves = project("curves") dependsOn (maths, guiapi)
lazy val instrument = project("instrument") dependsOn (curves, titanReturnTypes)
lazy val reportsFacility = project("reports.facility") dependsOn guiapi
lazy val rabbitEventViewerApi = project("rabbit.event.viewer.api") dependsOn(pivot, guiapi)
lazy val tradeFacility = project("trade.facility") dependsOn guiapi
lazy val gui = project("gui") dependsOn (fc2Facility, tradeFacility, reportsFacility, browser, rabbitEventViewerApi, singleClasspathManager)
lazy val starlingClient = project("starling.client") dependsOn (starlingDTOApi, bouncyrmi)
lazy val dbx = project("dbx") dependsOn instrument
lazy val databases = project("databases") dependsOn (pivot, concurrent, starlingDTOApi, dbx)
lazy val titan = project("titan") dependsOn databases
lazy val services = project("services").copy(resourceDirs = List(new File("services", "resources"), new File("services", "test-resources"))) dependsOn (curves, concurrent, loopyxl, titan, gui, titanReturnTypes)
lazy val rabbitEventViewerService = project("rabbit.event.viewer.service") dependsOn (rabbitEventViewerApi, databases, services)
lazy val tradeImpl = project("trade.impl") dependsOn (services, tradeFacility)
lazy val metals = project("metals").copy(resourceDirs = List(new File("metals", "resources"), new File("metals", "test-resources"))) dependsOn tradeImpl
lazy val reportsImpl = project("reports.impl") dependsOn services

lazy val webservice = {
  lazy val name = "webservice"
  lazy val libs = List(file(name, "lib_managed"), file(name, "lib"), file(name, "lib-jboss"), file(name, "maker-lib"), file(".maker/scala-lib"))
  lazy val resources =  List(file(name, "resources"), file(name, "test-resources"))
  new Project(
    name,
    file(name),
    libDirs = libs,
    resourceDirs = resources,
    props = makerProps
  ) dependsOn (utils :: manager :: props :: daterange :: starlingDTOApi :: quantity :: instrument :: (logisticsModelDeps ::: trademgmtModelDeps) : _*)
}

lazy val startserver = project("startserver") dependsOn (reportsImpl, metals, starlingClient, webservice, rabbitEventViewerService)
lazy val launcher = project("launcher") dependsOn (startserver, booter)
lazy val starling = new TopLevelProject("starling", List(launcher), makerProps, List(ProjectLib(manager.name, true)))

def runDevLauncher = {
  launcher.compile
  launcher.runMain(
    "starling.launcher.DevLauncher")(
    commonLaunchArgs : _*)()
}

def runServer = {
  launcher.compile
  launcher.runMain(
    "starling.startserver.Server")(
    commonLaunchArgs : _*)()
}

def writeClasspath{
  val cp = launcher.compilationClasspath
  writeToFile(file("launcher-classpath.sh"), "export STARLING_CLASSPATH=" + cp)
}
