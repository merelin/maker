package starling.gui

import api._
import javax.swing._
import osgi.GuiBromptonActivator
import pages._
import scala.swing.event.WindowClosing
import scala.swing.event.MouseClicked
import scala.swing.event.ButtonClicked
import scala.swing.event.Event
import starling.gui.StandardUserSettingKeys._
import scala.swing._
import scala.swing.{ButtonGroup => ScalaButtonGroup}
import scala.swing.{AbstractButton=> ScalaAbstractButton}
import java.awt.event._
import scala.swing.Swing._
import collection.mutable.ListBuffer
import javax.security.auth.login.LoginException
import starling.utils.{StackTraceToString, Log}
import starling.gui.LocalCacheKeys._
import management.ManagementFactory
import xstream.GuiStarlingXStream
import starling.browser._
import common._
import starling.browser.service.internal.HeterogeneousMap
import service._
import java.awt.{Cursor, GraphicsEnvironment, Color, KeyboardFocusManager}
import starling.pivot._
import javax.swing.event.{ChangeEvent, ChangeListener}
import GuiUtils._
import starling.pivot.utils.PeriodPivotFormatter
import starling.fc2.api.FC2Facility
import starling.rmi.StarlingServer
import starling.auth.{Client}
import starling.auth.internal.{RealClient, ClientLogin}
import starling.browser.internal.{NotificationKeys, NotificationType, Notification}
import starling.trade.facility.TradeFacility
import starling.browser.internal.{NotificationKeys, NotificationType, Notification}
import starling.rabbiteventviewer.api.RabbitEventViewerService
import starling.reports.facility.ReportFacility
import org.joda.time.DateTime
import starling.daterange.{Timestamp, Day}
import starling.bouncyrmi.{MethodLogEvent, BouncyRMIClient}
import collection.immutable.Map
import starling.utils.ImplicitConversions._
import scalaz.Scalaz._

object StarlingServerNotificationHandlers {
  def notificationHandler = {
    import starling.gui.StarlingLocalCache._
    new NotificationHook {
      def handle(event:Event, cache:LocalCache, sendNotification:(Notification) => Unit) {
        event match {
          case DeskClosed(desk, timestamp) => {
            if(cache.desks.contains(desk)) {
              val old: Map[Desk, Map[Day, List[TradeTimestamp]]] = cache.localCache(DeskCloses)
              val oldCloses: Map[Day, List[TradeTimestamp]] = old.getOrElse(desk, Map())
              val newCloses: Map[Day, List[TradeTimestamp]] = oldCloses + (timestamp.closeDay -> (timestamp :: oldCloses.getOrElse(timestamp.closeDay, Nil)))
              val newMap = old + (desk -> newCloses)
              cache.localCache(DeskCloses) = newMap
              val text = "Imported book close for " + desk + " (" + timestamp.timestamp.toStringMinutes + ")"
              val notification =  Notification(text, StarlingIcons.icon("/icons/16x16_book.png"), NotificationType.Message, {})
              sendNotification(notification)
            }
          }
          case DeskCloseFailed(desk, timestamp, error) => {
            if(cache.desks.contains(desk)) {
              val old: Map[Desk, Map[Day, List[TradeTimestamp]]] = cache.localCache(DeskCloses)
              val oldCloses: Map[Day, List[TradeTimestamp]] = old.getOrElse(desk, Map())
              val newCloses: Map[Day, List[TradeTimestamp]] = oldCloses + (timestamp.closeDay -> (timestamp :: oldCloses.getOrElse(timestamp.closeDay, Nil)))
              val newMap = old + (desk -> newCloses)
              cache.localCache(DeskCloses) = newMap
              val text = "Import failed for " + desk + " (" + timestamp.timestamp.toStringMinutes + ")"
              val notification = Notification(text, StarlingIcons.icon("/icons/16x16_error.png"), NotificationType.Message, {})
              sendNotification(notification)
            }
          }
          case _ =>
        }
      }
    }

  }

}
/**
 * The entry point into the starling gui
 */
object GuiStart extends Log {

  def systemInfo = {
    import starling.utils.ImplicitConversions._

    val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment.getScreenDevices.toList
    val monitors = screenDevices.map(sd => {
      val dm = sd.getDisplayMode

      MonitorInfo(
        sd.toString,
        dm.getWidth,
        dm.getHeight,
        sd.getAvailableAcceleratedMemory.megs
      )
    })

    val system = ManagementFactory.getOperatingSystemMXBean
    val vm = ManagementFactory.getRuntimeMXBean

    val (totalMem, freeMem) = system match {
      case sun:com.sun.management.OperatingSystemMXBean => (sun.getTotalPhysicalMemorySize.megs.toInt, sun.getFreePhysicalMemorySize.megs.toInt)
      case _ => (-1,-1)
    }

    OSInfo(
      system.getName,
      system.getArch,
      system.getVersion,
      vm.getVmName,
      vm.getVmVendor,
      System.getProperty("java.runtime.version"),
      system.getAvailableProcessors,
      totalMem,
      freeMem,
      monitors.size,
      monitors
    )
  }

  def initCache(starlingServer:StarlingServer, fc2Service:FC2Facility, reportService:ReportFacility, tradeService:TradeFacility) = {

    val starlingInitialData = starlingServer.init()
    val fc2InitialData = fc2Service.init()
    val tradeInitialData = tradeService.init()
    val cacheMap = new HeterogeneousMap[LocalCacheKey]
    import LocalCache._
    import StarlingLocalCache._
    try {
      cacheMap(MethodLogIndex) = 0
      cacheMap(CurrentUser) = starlingInitialData.whoAmI
      cacheMap(AllUserNames) = starlingInitialData.allUserNames
      cacheMap(PricingGroups) = fc2InitialData.pricingGroups
      cacheMap(ExcelDataSets) = fc2InitialData.excelDataSets
      cacheMap(Snapshots) = fc2InitialData.snapshots
      val (observationDaysForPricingGroup, observationDaysForExcel) = fc2InitialData.observationDays
      cacheMap(ObservationDaysForPricingGroup) = observationDaysForPricingGroup
      cacheMap(ObservationDaysForExcel) = observationDaysForExcel
      cacheMap(ExcelLatestMarketDataVersion) = fc2InitialData.excelLatestMarketDataVersions
      cacheMap(PricingGroupLatestMarketDataVersion) = fc2InitialData.pricingGroupLatestMarketDataVersions
      cacheMap(LocalCacheKeys.ReportOptionsAvailable) = reportService.reportOptionsAvailable
      cacheMap(DeskCloses) = tradeInitialData.deskCloses
      cacheMap(IntradayLatest) = tradeInitialData.intradayLatest
      cacheMap(UKBusinessCalendar) = starlingInitialData.ukBusinessCalendar
      cacheMap(Desks) = tradeInitialData.desks
      cacheMap(GroupToDesksMap) = tradeInitialData.groupToDesksMap
      cacheMap(IsStarlingDeveloper) = starlingInitialData.isStarlingDeveloper
      cacheMap(EnvironmentRules) = fc2InitialData.environmentRuleLabels
      cacheMap(CurveTypes) = fc2InitialData.curveTypes
      cacheMap(LatestEmailEvent) = new Timestamp
      cacheMap
    } catch {
      case e : Throwable =>
        e.printStackTrace()
        throw e
    }
  }

  def addListeners(   cacheMap:HeterogeneousMap[LocalCacheKey],
                      starlingServer:StarlingServer,
                      reportService:ReportFacility,
                      fc2Service:FC2Facility,
                      tradeService:TradeFacility,
                      publisher: Publisher) {
    val localCacheUpdatePublisher = new scala.swing.Publisher() {}
    publisher.reactions += {
      case e => localCacheUpdatePublisher.publish(e)
    }

    localCacheUpdatePublisher.reactions += {
      case ExcelMarketListUpdate(values) => {
        cacheMap(ExcelDataSets) = values
      }
      case mds:MarketDataSnapshot=> {
        val snapshotsBySelection = cacheMap(Snapshots)

        val snapshots = snapshotsBySelection.get(mds.snapshotID.marketDataSelection)

        val newLabels = snapshots match {
          case None => List(mds.snapshotID)
          case Some(labels) => mds.snapshotID :: labels
        }

        cacheMap(Snapshots) = snapshotsBySelection.updated(mds.snapshotID.marketDataSelection, newLabels)
      }
      case PricingGroupMarketDataUpdate(pg, version, _, _) => {
        cacheMap(PricingGroupLatestMarketDataVersion) =
                cacheMap(PricingGroupLatestMarketDataVersion) + (pg -> version)
      }
      case ExcelMarketDataUpdate(excel, version) => {
        cacheMap(ExcelLatestMarketDataVersion) =
                cacheMap(ExcelLatestMarketDataVersion) + (excel -> version)
      }
      case IntradayUpdated(group, user, timestamp) => {
        cacheMap(IntradayLatest) = cacheMap(IntradayLatest) + (group -> (user, timestamp))
      }
      case ExcelObservationDay(name, day) => {
        val current = cacheMap(ObservationDaysForExcel)
        cacheMap(ObservationDaysForExcel) = {
          current.updated(name, current.getOrElse(name, Set()) + day)
        }
      }
      case PricingGroupObservationDay(pricingGroup, day) => {
        val current = cacheMap(ObservationDaysForPricingGroup)
        cacheMap(ObservationDaysForPricingGroup) = {
          current.updated(pricingGroup, current.getOrElse(pricingGroup, Set()) + day)
        }
      }
      case UserLoggedIn(username) => {
        val current = cacheMap(LocalCache.AllUserNames)
        if (!current.exists(_ == username)) {
          cacheMap(LocalCache.AllUserNames) = username :: current
        }
      }
      case TradesUpdated(desk, timestamp) => {
        val current = cacheMap(DeskCloses)
        cacheMap(DeskCloses) = current + (Desk.Titan -> (current(Desk.Titan) + (TradeTimestamp.magicLatestTimestampDay -> List(TradeTimestamp.makeMagicLatestTimestamp(timestamp)))))
      }
      case EmailSent(timeSent) => {
        cacheMap(LatestEmailEvent) = timeSent
      }
      case e:MethodLogEvent => cacheMap(MethodLogIndex) = e.id
    }
  }


  private def getMessage(t: Throwable): String = {
    val m = if (t.getMessage == null) {
      "Error"
    } else {
      t.getMessage
    }

    if (m.length > 60) {
      m.substring(0, 60) + " ... "
    } else {
      m
    }

    m
  }

  def showErrorThenExit(t: Throwable) {
    log.fatal("Failed to start starling: ", t)
    onEDT {
      GuiUtils.setLookAndFeel()
      new Frame {
        title = "Could not start Starling"
        iconImage = StarlingIcons.icon("/icons/32x32/status/weather-few-clouds.png").getImage
        val okButton = new Button("Ok") {reactions += {case ButtonClicked(e) => exit()}}
        contents = new MigPanel("insets n 0 n n") {
          val image = StarlingIcons.im("/icons/128x128_storm_dead_bird.png")
          val imagePanel = new FixedImagePanel(image)

          val message = getMessage(t)

          val l = new Label("Failed to start Starling: " + message)

          val textArea = new TextArea(StackTraceToString.string(t)) {
            editable = false
          }
          val scrollPane = new ScrollPane(textArea) {
            preferredSize = new Dimension(500, 200)
          }

          add(imagePanel, "ay top")
          add(l, "ay top, gaptop 40lp, split, flowy")
          add(scrollPane, "gaptop 30lp, wrap unrel, push, grow")
          add(okButton, "split, spanx, tag ok")
        }
        pack()
        centerOnScreen()
        defaultButton = okButton
        visible = true
        
        reactions += {case WindowClosing(w) => exit()}
        def exit() {System.exit(-1)}
      }
    }
  }
}

object StarlingUtilButtons {
  def create(context:PageContext) = {
    import StarlingLocalCache._
    def userStatsPage = PageFactory(_ => UserStatsPage(PivotPageState()))
    def runAsUserPage = PageFactory(_ => RunAsUserPage())
    def cannedHomePage = PageFactory(_ => CannedHomePage())
    def eventViewerPage = PageFactory(_ => EventViewerPage())
    def methodLogViewerPage = PageFactory(_ => MethodLogViewerPage(context.localCache.methodLogIndex))
    def gitLogPage = PageFactory(_ => GitLogPage(PivotPageState()))

    val tradesButton = new PageButton(
      "View User Stats",
      userStatsPage,
      StarlingIcons.im("/icons/32x32_stats.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_S, 0) )
    )

    val runAsUserButton = new PageButton(
      "Run As User",
      runAsUserPage,
      StarlingIcons.im("/icons/32x32_user_dark.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_U, 0) )
    )

    val cannedButton = new PageButton(
      "Canned Page",
      cannedHomePage,
      StarlingIcons.im("/icons/32x32_canned_launcher.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_C, 0) )
    )

    val eventViewerButton = new PageButton(
      "Starling Event Viewer",
      eventViewerPage,
      StarlingIcons.im("/icons/32x32_event.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_E, 0) )
    )

    val methodLogViewerButton = new PageButton(
      "Method Log Viewer",
      methodLogViewerPage,
      StarlingIcons.im("/icons/32x32_event.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_M, 0) )
    )

    val gitLogButton = new PageButton(
      "Git Log",
      gitLogPage,
      StarlingIcons.im("/icons/32x32_log.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_G, 0) )
    )

    List(tradesButton, runAsUserButton, cannedButton, eventViewerButton, methodLogViewerButton, gitLogButton)
  }
}

object StarlingHomeButtons {
  def create(context:PageContext) = {
    import StarlingLocalCache._

    def tradePage = {
      new PageFactory() {
        def create(serverContext: ServerContext) = {
          val initial = {
            val defaultSelection = (context.localCache.desks.headOption, None)
            val lastSelection = context.getSetting(StandardUserSettingKeys.InitialTradeSelection, defaultSelection)
            lastSelection match {
              case (_, Some(groups)) => {
                val validGroups = context.localCache.intradaySubgroups.keySet
                if (groups.subgroups.forall(g => validGroups.exists(vg => vg.startsWith(g)))) lastSelection else defaultSelection
              }
              case _ => lastSelection
            }
          }

          val deskWithTime = initial._1.flatMap(d => context.localCache.latestTimestamp(d).map(ts => (d, ts)))
          val intradayWithTime = initial._2.map(groups => (groups, context.localCache.latestTimestamp(groups)))

          TradeSelectionPage(TradePageParameters(
            deskWithTime, intradayWithTime,
            TradeExpiryDay(Day.today)), PivotPageState(false, PivotFieldParams(true, None)))
        }
      }
    }

    def marketDataPage = new PageFactory() {
      def create(serverContext: ServerContext) = {
        MarketDataPage.pageFactory(context, StandardMarketDataPageIdentifier(defaultMarketDataIdentifier), None, None)(serverContext)
      }
    }


    def curvePage = {
      new PageFactory {
        def create(serverContext: ServerContext) = {
          val curveLabel = CurveLabel(CurveTypeLabel("Price"), defaultMarketDataIdentifier, EnvironmentSpecificationLabel(
            context.localCache.populatedDays(defaultMarketDataIdentifier.selection).lastOption.getOrElse(Day.today),
            context.localCache.environmentRulesForPricingGroup(defaultMarketDataIdentifier.selection.pricingGroup).head
          ))

          val initialState = DefaultPivotState(PivotFieldsState(
            dataFields = List(Field("Price"), Field("Input")),
            columnFields = List(Field("Observation Time"), Field("Market")),
            rowFields = List(Field("Period"))
          ))

          new CurvePage(curveLabel, PivotPageState.default(initialState))
        }
      }
    }

    def defaultMarketDataIdentifier: MarketDataIdentifier = {
      val initialSelection = context.getSetting(StandardUserSettingKeys.InitialMarketDataSelection,
        MarketDataSelection(context.localCache.pricingGroups(None).headOption))
      val latestMarketDataVersion = context.localCache.latestMarketDataVersion(initialSelection)

      MarketDataIdentifier(initialSelection, latestMarketDataVersion)
    }

    val tradesButton = new PageButton(
      "Trades",
      tradePage,
      StarlingIcons.im("/icons/32x32_trades.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_T, 0) )
    )

    val refDataButton = new PageButton(
      "Reference Data",
      new PagePageFactory(ReferenceDataIndexPage),
      StarlingIcons.im("/icons/32x32_ref_data.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_R, 0) )
    )

    val marketDataButton = new PageButton(
      "Market Data",
      marketDataPage,
      StarlingIcons.im("/icons/32x32_market_data.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_M, 0) )
    )

    val curveViewerButton = new PageButton(
      "Curve Viewer",
      curvePage,
      StarlingIcons.im("/icons/32x32_curve_viewer.png"),
      Some( KeyStroke.getKeyStroke(KeyEvent.VK_C, 0) )
    )

    tradesButton :: refDataButton :: marketDataButton :: curveViewerButton :: Nil
  }
}


object StarlingSettings {

  def create(context:PageContext) = {

    val currentSettings = context.getSetting(ExtraFormattingInfo, PivotFormatter.DefaultExtraFormatInfo)
    val dp = currentSettings.decimalPlaces
    def saveSettings() {
      context.putSetting(ExtraFormattingInfo, ExtraFormatInfo(decimalPlacesPanel.decimalPlaces, dateRangeFormatPanel.dateRangeFormat))
    }
    lazy val decimalPlacesPanel = new MigPanel("insets n n n 0", "[" + StandardLeftIndent + "][p]") {
      def createSpinner(initialValue:Int) = {
        val maxDP = 10
        val spinnerModel = new SpinnerNumberModel(initialValue, 0, maxDP, 1) {
          addChangeListener(new ChangeListener {
            def stateChanged(e:ChangeEvent) {
              saveSettings()
            }
          })
        }
        new JSpinner(spinnerModel) {
          def format = {
            val num = getValue.asInstanceOf[Int]
            if (num > 0) {
              "#,##0." + List.fill(num)("0").mkString
            } else {
              "#,##0"
            }
          }
        }
      }

      def numFromText(t:String) = {
        val lastIndex = t.lastIndexOf(".")
        if (lastIndex == -1) {
          0
        } else {
          t.length - 1 - lastIndex
        }
      }

      val spinners = dp.getFormats.mapValuesEagerly(format => createSpinner(numFromText(format)))

      val explainCheckBox = new CheckBox("Unlimited on Explain Screen") {
        selected = dp.unlimitedOnExplainScreen
        tooltip = "Display the maximum number of decimal places when viewing the explain screen"
        reactions += {case ButtonClicked(_) => saveSettings()}
      }

      add(LabelWithSeparator("Decimal Places"), "spanx, growx, wrap")

      DecimalPlaces.names.zipWithIndex.foreach { case (name, index) =>
        add(new Label(name.capitalize + ":"), "skip 1")
        add(spinners(name), (index < DecimalPlaces.names.length) ? "wrap" | "wrap unrel")
      }

      add(explainCheckBox, "skip 1, spanx")

      def decimalPlaces = new DecimalPlaces(spinners.mapValuesEagerly(_.format), explainCheckBox.selected)
      def decimalPlaces_=(dp:DecimalPlaces) {
        dp.getFormats.map { case (name, format) => spinners(name).setValue(numFromText(format)) }
        explainCheckBox.selected = dp.unlimitedOnExplainScreen
      }
    }
    lazy val dateRangeFormatPanel = new MigPanel("insets n n n 0", "[" + StandardLeftIndent + "][p]") {
      import MonthFormat._
      val standardExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(Standard))
      val standardCapitalisedExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(StandardCapitalised))
      val shortExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(Short))
      val shortCapitalisedExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(ShortCapitalised))
      val shortDashExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(ShortDash))
      val shortDashCapitalisedExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(ShortDashCapitalised))
      val numericExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(Numeric))
      val reutersExtraInfo = ExtraFormatInfo(dateRangeFormat = DateRangeFormat(Reuters))

      val today = Day.today
      val sampleMonths = List(today.asMonthObject, today.addMonths(1).asMonthObject)

      val standardSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, standardExtraInfo).text).mkString("(", ", ", " ...)")
      val standardCapitalisedSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, standardCapitalisedExtraInfo).text).mkString("(", ", ", " ...)")
      val shortSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, shortExtraInfo).text).mkString("(", ", ", " ...)")
      val shortCapitalisedSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, shortCapitalisedExtraInfo).text).mkString("(", ", ", " ...)")
      val shortDashSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, shortDashExtraInfo).text).mkString("(", ", ", " ...)")
      val shortDashCapitalisedSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, shortDashCapitalisedExtraInfo).text).mkString("(", ", ", " ...)")
      val numericSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, numericExtraInfo).text).mkString("(", ", ", " ...)")
      val reutersSampleText = sampleMonths.map(m => PeriodPivotFormatter.format(m, reutersExtraInfo).text).mkString("(", ", ", " ...)")

      val standardLabel = new Label(standardSampleText)
      val standardCapitalisedLabel = new Label(standardCapitalisedSampleText)
      val shortLabel = new Label(shortSampleText)
      val shortCapitalisedLabel = new Label(shortCapitalisedSampleText)
      val shortDashLabel = new Label(shortDashSampleText)
      val shortDashCapitalisedLabel = new Label(shortDashCapitalisedSampleText)
      val numericLabel = new Label(numericSampleText)
      val reutersLabel = new Label(reutersSampleText)

      def createButton(name:String) = new RadioButton(name) {reactions += {case ButtonClicked(_) => saveSettings()}}

      val standardButton = createButton("Standard")
      val standardCapitalisedButton = createButton("Standard Capitalised")
      val shortButton = createButton("Short")
      val shortCapitalisedButton = createButton("Short Capitalised")
      val shortDashButton = createButton("Short Dash")
      val shortDashCapitalisedButton = createButton("Short Dash Capitalised")
      val numericButton = createButton("Numeric")
      val reutersButton = createButton("Reuters")

      val buttonToType = Map[ScalaAbstractButton,DateRangeFormat](
        standardButton -> DateRangeFormat(Standard),
        standardCapitalisedButton -> DateRangeFormat(StandardCapitalised),
        shortButton -> DateRangeFormat(Short),
        shortCapitalisedButton -> DateRangeFormat(ShortCapitalised),
        shortDashButton -> DateRangeFormat(ShortDash),
        shortDashCapitalisedButton -> DateRangeFormat(ShortDashCapitalised),
        numericButton -> DateRangeFormat(Numeric),
        reutersButton -> DateRangeFormat(Reuters))
      val typeToButton = buttonToType.map{_.swap}

      val group = new ScalaButtonGroup(buttonToType.keySet.toArray : _*)

      add(LabelWithSeparator("Month Format"), "spanx, growx, wrap")
      add(standardButton, "skip1")
      add(standardLabel, "wrap")
      add(shortButton, "skip 1")
      add(shortLabel, "wrap")
      add(shortDashButton, "skip 1")
      add(shortDashLabel, "wrap")
      add(standardCapitalisedButton, "skip1")
      add(standardCapitalisedLabel, "wrap")
      add(shortCapitalisedButton, "skip 1")
      add(shortCapitalisedLabel, "wrap")
      add(shortDashCapitalisedButton, "skip 1")
      add(shortDashCapitalisedLabel, "wrap")
      add(numericButton, "skip 1")
      add(numericLabel, "wrap")
      add(reutersButton, "skip 1")
      add(reutersLabel)

      def dateRangeFormat = {
        buttonToType(group.selected.get)
      }
      def dateRangeFormat_=(drf:DateRangeFormat) {
        group.select(typeToButton(drf))
      }

      dateRangeFormat = currentSettings.dateRangeFormat
    }

    new MigPanel("insets 0") {
      add(decimalPlacesPanel, "gapright unrel, ay top")
      add(dateRangeFormatPanel, "ay top")
      reactions += {
        case UserSettingUpdated(ExtraFormattingInfo) => {
          val extraFormatInfo = context.getSetting(ExtraFormattingInfo)
          decimalPlacesPanel.decimalPlaces = extraFormatInfo.decimalPlaces
          dateRangeFormatPanel.dateRangeFormat = extraFormatInfo.dateRangeFormat
        }
      }
      listenTo(context.remotePublisher)
    } :: Nil



  }
}