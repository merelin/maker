package starling.gui

import api._
import java.lang.String
import pages.{AbstractPivotPage, SingleTradePage, PivotPageState}
import starling.pivot.view.swing.MigPanel
import swing.event.ButtonClicked
import starling.pivot.model._
import swing.{Label, Button}
import java.awt.Dimension
import starling.pivot._
import java.lang.reflect.Method
import net.sf.cglib.proxy.{MethodProxy, MethodInterceptor, Enhancer}
import starling.rmi.StarlingServer
import collection.Seq
import starling.quantity.{UOM, Quantity}
import starling.auth.User
import starling.daterange.{Month, Year, Day}
import collection.immutable.{Map, Iterable}

/**
 * An alternative StarlingBrowser for testing gui features quickly
 */

object CannedLauncher {
  def main(args:Array[String]) {
    System.setProperty("log4j.configuration", "utils/resources/log4j.properties")
    val nullServer = {
        val e = new Enhancer();
        e.setSuperclass(classOf[StarlingServer])
        e.setCallback(new MethodInterceptor() {
          def intercept(obj:Object, method:Method,
                        args:Array[Object], proxy:MethodProxy):Object = {
            val name = method.getName
            if (name=="tradeSystems") {
              List(TradeSystemLabel("canned", "can"))
            } else if (name=="readSettings") {
              new UserSettings
            } else if (name=="version") {
              Version("canned", "cannedHostname", "cannedDatabase", false, None)
            } else if (name=="desks") {
              List(Desk("Canned"))
            } else if (name=="userReports") {
              List()
            } else if (name=="extraLayouts") {
              List()
            } else if (name=="observationDays") {
              (Map(),Map())
            } else if (name=="whoAmI") {
              User.Dev
            } else if (name=="bookmarks") {
              List()
            } else {
              null
            }
          }
        })
        e.create().asInstanceOf[StarlingServer]
      }
    val publisher = new scala.swing.Publisher() {}
    Launcher.start(nullServer, publisher, new CannedHomePage, None)
//    Launcher.start(nullServer, publisher, CannedPivotReportPage(PivotPageState(false, PivotFieldParams(true, None))))
    /*Launcher.start(nullServer, publisher, CannedPivotReportPage(PivotPageState(false,
      PivotFieldParams(true, Some(PivotFieldsState(List(Field("PV")), List(Field("Trader"), Field("Product"),
        Field("Lots"), Field("Strike")), List(Field("Trade")), List(), Totals(true,true,true)))), "")))*/
  }
}

class NullPageData extends PageData

case class CannedHomePage() extends Page {
  def text = "Home"
  def build(reader: PageBuildingContext) = { new PageData{} }
  def createComponent(context: PageContext, data:PageData, bookmark:Bookmark, browserSize:Dimension) = {
    new CannedHomePagePageComponent(context)
  }
  def icon = StarlingIcons.im("/icons/tablenew_16x16.png")
}

class CannedHomePagePageComponent(pageContext:PageContext) extends MigPanel("") with PageComponent {
  val runButton = new Button {
    text = "Run"
    reactions += {
      case ButtonClicked(b) => pageContext.goTo(CannedPivotReportPage(PivotPageState(false, PivotFieldParams(true, None))))
    }
  }
  add(runButton)
  add(new Button {
    text = "Run Editable"
    reactions += {
      case ButtonClicked(b) => pageContext.goTo(EditableCannedPivotReportPage(PivotPageState(false, PivotFieldParams(true, None))))
    }
  })
  add(new Button {
    text = "Run Editable Specified"
    reactions += {
      case ButtonClicked(b) => pageContext.goTo(EditableSpecifiedCannedPivotReportPage(PivotPageState(false, PivotFieldParams(true, None))))
    }
  })
  add(new Button {
    text = "Run Diff"
    reactions += {
      case ButtonClicked(b) => pageContext.goTo(DiffCannedPivotReportPage(PivotPageState(false, PivotFieldParams(true, None))))
    }
  })
  add(new Button {
    text = "Run Slow"
    reactions += {
      case ButtonClicked(b) => pageContext.goTo(SlowCannedPivotReportPage(PivotPageState(false, PivotFieldParams(true, None))))
    }
  })
  add(new Button {
    text = "View Trade"
    reactions += {
      case ButtonClicked(b) => pageContext.goTo(SingleTradePage(TradeIDLabel(1234.toString, TradeSystemLabels.TrinityTradeSystem), Some(Desk("Trinity")), TradeExpiryDay(Day.today), None))
    }
  })

  override def pageShown = runButton.requestFocusInWindow
}

case class CannedDrilldownPage(fields:Seq[(Field,Any)]) extends Page {
  def text = "Canned drilldown"
  def build(reader: PageBuildingContext) = new PageData() {}
  def createComponent(context: PageContext, data:PageData, bookmark:Bookmark, browserSize:Dimension) = {
    new MigPanel("") with PageComponent {
      add(new Label("Drilldown"), "span")
      for ((field,value) <- fields) {
        add(new Label("   " + field.name + " => " + value), "span")
      }
    }
  }
  def icon = StarlingIcons.im("/icons/tablenew_16x16.png")
}

case class SlowCannedPivotReportPage(pivotPageState:PivotPageState) extends AbstractPivotPage(pivotPageState) {
  override def text = "Slow Canned Pivot Report"
  override def layoutType = Some("Canned")
  def dataRequest(pageBuildingContext:PageBuildingContext) = {
    Thread.sleep(5*1000);
    PivotTableModel.createPivotData(new CannedDataSource, pivotPageState.pivotFieldParams)
  }
  def selfPage(pivotPageState:PivotPageState, edits:PivotEdits) = SlowCannedPivotReportPage(pivotPageState)
  override def finalDrillDownPage(fields:Seq[(Field, Selection)], pageContext:PageContext, ctrlDown:Boolean) = pageContext.goTo(CannedDrilldownPage(fields), ctrlDown)
}

case class DiffCannedPivotReportPage(pivotPageState:PivotPageState) extends AbstractPivotPage(pivotPageState) {
  override def text = "Diff Canned Pivot Report"
  override def layoutType = Some("Canned")
  def dataRequest(pageBuildingContext:PageBuildingContext) = {
    val cannedDataSource = new CannedDataSource
    PivotTableModel.createPivotData(new DiffPivotTableDataSource(cannedDataSource, cannedDataSource, "D-1"), pivotPageState.pivotFieldParams)
  }
  def selfPage(pivotPageState:PivotPageState, edits:PivotEdits) = DiffCannedPivotReportPage(pivotPageState)
}

case class CannedPivotReportPage(pivotPageState:PivotPageState) extends AbstractPivotPage(pivotPageState) {
  override def text = "Canned Pivot Report"
  override def layoutType = Some("Canned")
  def dataRequest(pageBuildingContext:PageBuildingContext) = {
    PivotTableModel.createPivotData(new CannedDataSource, pivotPageState.pivotFieldParams)
  }
  def selfPage(pivotPageState:PivotPageState, edits:PivotEdits) = CannedPivotReportPage(pivotPageState)
  override def finalDrillDownPage(fields:Seq[(Field, Selection)], pageContext:PageContext, ctrlDown:Boolean) = pageContext.goTo(CannedDrilldownPage(fields), ctrlDown)
}

case class EditableCannedPivotReportPage(pivotPageState:PivotPageState) extends AbstractPivotPage(pivotPageState) {
  override def text = "Editable Canned Pivot Report"
  override def layoutType = Some("Canned")
  def dataRequest(pageBuildingContext:PageBuildingContext) = {
    PivotTableModel.createPivotData(new EditableCannedDataSource, pivotPageState.pivotFieldParams)
  }
  def selfPage(pPS:PivotPageState, edits:PivotEdits) = copy(pivotPageState = pPS)
  override def finalDrillDownPage(fields:Seq[(Field, Selection)], pageContext:PageContext, ctrlDown:Boolean) = pageContext.goTo(CannedDrilldownPage(fields), ctrlDown)
}

case class EditableSpecifiedCannedPivotReportPage(pivotPageState:PivotPageState, edits:PivotEdits=PivotEdits.Null) extends AbstractPivotPage(pivotPageState, edits) {
  override def text = "Editable Canned Pivot Report With Specified Values"
  override def layoutType = Some("Canned")
  def dataRequest(pageBuildingContext:PageBuildingContext) = {
    val ds = (new EditableSpecifiedCannedDataSource).editable.get.withEdits(edits)
    PivotTableModel.createPivotData(ds, pivotPageState.pivotFieldParams)
  }
  def selfPage(pPS:PivotPageState, edits0:PivotEdits) = copy(pivotPageState = pPS, edits = edits0)
  override def finalDrillDownPage(fields:Seq[(Field, Selection)], pageContext:PageContext, ctrlDown:Boolean) = pageContext.goTo(CannedDrilldownPage(fields), ctrlDown)

  override def save(starlingServer:StarlingServer, edits:PivotEdits) = {
    println("EditableSpecifiedCannedPivotReportPage saved these " + " edits " + edits)
    true
  }
}

object CannedDeltaPivotFormatter extends PivotFormatter {
  def format(value:Any, formatInfo:ExtraFormatInfo) = {
    StandardPivotQuantityFormatter.format(value, formatInfo).copy(longText = Some("Delta explanation"))
  }
}

/**
 * A data source used for testing the scaling of the pivot table.
 */
class CannedDataSource extends UnfilteredPivotTableDataSource {
  private val num = 1000

  private val traders = List("corin", "brian", "kieth","alex","mike", "Iamatraderwithareallylongnameitisreallyverylongohyesitis")
//  private val traderBookLookup = List("alex")
  private val products = List("BRENT", "WTI", "COAL","GAS","PAPER","ABC")
  private val strikes = List(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160)
  private val expiryMonths = Year(2009).toListOfMonths
  private val lots = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 50, 100, 500, 1000, 5000, 10000, 50000, 100000)
  val localFields : List[FieldDetails] = new SumPivotQuantityFieldDetails("PV") {
    override def isDataField = true
    override def parser = PivotQuantityPivotParser
  } :: new SumPivotQuantityFieldDetails("Delta") {
    override def isDataField = true
    override def formatter:PivotFormatter = CannedDeltaPivotFormatter
  } :: new SumPivotQuantityFieldDetails("Gamma") {
    override def isDataField = true
  } :: List("Trade", "Trader", "Product", "Strike", "Expiry", "Lots").map{ f => new FieldDetails(f)}

  val random = new java.util.Random(1234567890L)
  private val theData : List[Map[Field, Any]] = {
    val l = for(i <- 0 until num) yield {
      val trade = "T"+i
      val trader = traders(random.nextInt(traders.size))
      val product = products(random.nextInt(products.size))
      val strike = strikes(random.nextInt(strikes.size))
      val expiry = expiryMonths(random.nextInt(expiryMonths.size))
      val lot = lots(random.nextInt(lots.size))
      val pv = Quantity(random.nextGaussian * 100.0, UOM.USD)
      val gamma = random.nextGaussian * 10.0
      Map(localFields(3).field -> trade,localFields(4).field -> trader, localFields(5).field -> product,
        localFields(6).field -> strike, localFields(7).field -> expiry, localFields(8).field -> lot,
        localFields(0).field -> PivotQuantity(pv), localFields(2).field -> PivotQuantity(gamma))
    }
    l.toList
  } ::: {
    (for(i <- 0 until num) yield {
      val trade = "T"+i
      val trader = traders(random.nextInt(traders.size))
      val pv = Quantity(random.nextGaussian * 100.0, UOM.USD)
      val gamma = random.nextGaussian * 10.0
      val delta = random.nextGaussian * 100.0
      Map(localFields(3).field -> trade,localFields(4).field -> trader,
        localFields(0).field -> PivotQuantity(pv), localFields(1).field -> PivotQuantity(delta),
        localFields(2).field -> PivotQuantity(gamma))
    }).toList
  }

  override def drillDownGroups = List(
    DrillDownInfo(PivotAxis(List(), List(Field("Strike")),List(), false)),
    DrillDownInfo(PivotAxis(List(), List(Field("Expiry")),List(), false)),
    DrillDownInfo(PivotAxis(List(), List(Field("Trade")),List(), false)))

  /*override def initialState = new PivotFieldsState(columns = {
    ColumnTrees(List(
      ColumnTree(Field("PV"), true), ColumnTree(Field("Gamma"), true)))
  }, rowFields = List(Field("Trader"), Field("Strike")))*/
  override def initialState = new PivotFieldsState(columns = {
    ColumnTrees(List(
      ColumnTree(Field("PV"), true), ColumnTree(Field("Gamma"), true)))
  }, rowFields = List(Field("Trader")))

  def unfilteredData(pfs : PivotFieldsState) = theData

  val fieldDetailsGroups = {
    val (group1Fields, group2Fields) = localFields.splitAt(localFields.size / 2)
    List(FieldDetailsGroup("Group 1", group1Fields), FieldDetailsGroup("Group 2", group2Fields))
  }
  override def zeroFields = Set(Field("Delta"))
  override def toString = "BigCannedDataSource"
}

class EditableCannedDataSource extends CannedDataSource {
  override def editable = Some(new EditPivot {
    def save(edits:PivotEdits) = {println("SAVE : " + edits); true}
    def editableToKeyFields = Map(Field("PV") -> Set(Field("Lots")), Field("Gamma") -> Set(Field("Lots"), Field("Product"), Field("Strike")))
    def withEdits(edits:PivotEdits) = null
  })

}

class EditableSpecifiedCannedDataSource extends UnfilteredPivotTableDataSource {
  private val traders = List("corin", "brian", "kieth","alex","mike", "Iamatraderwithareallylongnameitisreallyverylongohyesitis")
//  private val months = List(Month(2011, 1),Month(2011, 2),Month(2011, 3))
  private val markets = List("BRENT", "WTI", "COAL","GAS","PAPER","ABC", "abe", "What", "Which", "when")

  def data:List[Map[Field, Any]] = {
    val random = new java.util.Random(1234567890L)
    (for (trader <- traders; market <- markets) yield {
      if (random.nextInt(9) > 3) {
        Some(Map(Field("Trader") -> trader, Field("Market") -> market, Field("Volume") -> 20))
      } else {
        None
      }
    }).flatten ::: List(Map(Field("Trader") -> "alex", Field("Market") -> "Unused", Field("Volume") -> 15))
  }

  val marketFieldDetails = new FieldDetails("Market") {
    override def parser = new CannedMarketPivotParser(markets.toSet + "Unused")
  }

  def fieldDetailsGroups = List(FieldDetailsGroup("Group 1", FieldDetails("Trader"), marketFieldDetails, new SumIntFieldDetails("Volume")))
  def unfilteredData(pfs:PivotFieldsState) = data

  override def editable = Some(new EditPivot {
    def save(edits:PivotEdits) = {println("SAVE : " + edits); true}
    def editableToKeyFields = Map(Field("Volume") -> Set(Field("Trader"), Field("Market")))
    def withEdits(edits:PivotEdits):PivotTableDataSource = {
      if (edits.isEmpty) {
        EditableSpecifiedCannedDataSource.this
      } else {
        val d:List[Map[Field, Any]] = EditableSpecifiedCannedDataSource.this.data
        new EditableSpecifiedCannedDataSource {
          override def data = {
            println("")
            println("!!! Edits : " + (edits.edits.size, edits))
            println("")

            val dWithDeletesAndAmends = d.map(m => {
              val key = Map(Field("Trader") -> m(Field("Trader")), Field("Market") -> m(Field("Market")))
              m.map { case (field, value) => {
                edits.editFor(key, field) match {
                  case None => field -> value
                  case Some((matchedKey, edit)) => field -> edit.applyEdit(matchedKey, field, value)
                }
              }
            }})

            val addedRows = edits.newRows.zipWithIndex.map{case (row,index) => {
              Map() ++ fieldDetailsMap.keySet.map(f => {
                f -> NewValue(row.get(f), index, PivotEdits.Null.withAddedRow(row))
              })
            }}.toList

            dWithDeletesAndAmends ::: addedRows
          }
        }
      }
    }
  })

  override def initialState = new PivotFieldsState(rowFields = List(Field("Trader"), Field("Market")), columns = ColumnTrees(Field("Volume"), true))
}

class CannedMarketPivotParser(markets:Set[String]) extends PivotParser {
  def parse(text:String) = {
    val lowerCaseMarkets = markets.map(_.trim().toLowerCase)
    if (lowerCaseMarkets(text.trim.toLowerCase)) {
      (text, text)
    } else {
      throw new Exception("Unknown Market")
    }
  }
  override def acceptableValues = markets
}