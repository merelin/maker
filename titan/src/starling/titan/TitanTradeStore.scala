package starling.titan

import starling.tradestore.TradeStore
import starling.instrument.TradeableType
import starling.pivot.{DrillDownInfo, PivotAxis, PivotFieldsState, Field}
import starling.richdb.{RichDB, RichInstrumentResultSetRow}
import starling.utils.Broadcaster
import starling.instrument.TradeSystem
import starling.pivot.FieldDetails
import starling.gui.api.{Desk, TradesUpdated}
import starling.instrument.physical.PhysicalMetalForward

object TitanTradeStore {
  val quotaID_str = "Quota ID"
  val quotaQuantity_str = "Quota Quantity"
  val assignmentID_str = "Assignment ID"
  val titanTradeID_str = "Titan Trade ID"
  val inventoryID_str = "Inventory ID"
  val groupCompany_str = "Group Company"
  val comment_str = "Comment"
  val submitted_str = "Submitted"
  val shape_str = "Shape"
  val contractFinalised_str = "Contract Finalised"
  val tolerancePlus_str = "Tolerance Plus"
  val toleranceMinus_str = "Tolerance Minus"

  val labels = List(quotaID_str, assignmentID_str, titanTradeID_str, inventoryID_str, groupCompany_str, comment_str, submitted_str, shape_str, contractFinalised_str, tolerancePlus_str, toleranceMinus_str)
}

class TitanTradeStore(db: RichDB, broadcaster:Broadcaster, tradeSystem:TradeSystem)
        extends TradeStore(db, broadcaster, tradeSystem) {
  val tableName = "TitanTrade"
  def createTradeAttributes(row:RichInstrumentResultSetRow) = {
    val quotaID = row.getString("quotaID")
    val quotaQuantity = row.getQuantity("quotaQuantity")
    val titanTradeID = row.getString("titanTradeID")
    val inventoryID = row.getString("inventoryID")
    val groupCompany = row.getString("groupCompany")
    val comment = row.getString("Comment")
    val submitted = row.getDay("Submitted")
    val shape = row.getString("Shape")
    val contractFinalised = row.getString("ContractFinalised")
    val tolerancePlus = row.getPercentage("TolerancePlus")
    val toleranceMinus = row.getPercentage("ToleranceMinus")

    TitanTradeAttributes(quotaID, quotaQuantity, titanTradeID, Option(inventoryID), groupCompany, comment, submitted, shape, contractFinalised, tolerancePlus, toleranceMinus)
  }

  def pivotInitialState(tradeableTypes:Set[TradeableType[_]]) = {
    PivotFieldsState(List(Field("Trade Count")))
  }

  def pivotDrillDownGroups() = {
    List(
      DrillDownInfo(PivotAxis( List(), List(), List(), false)),
      DrillDownInfo(PivotAxis( List(), List(Field("Instrument"), Field("Commodity")), List(), false)),
      instrumentFilteredDrillDown
    )
  }

  override val tradeAttributeFieldDetails =
    TitanTradeStore.labels.map{ label => FieldDetails(label)}

  override def tradesChanged() = {
   broadcaster.broadcast(TradesUpdated(Desk.Titan, cachedLatestTimestamp.get))
  }
  def getAllForwards() : List[PhysicalMetalForward] = {
    null
  }
  def getForward(titanTradeID : String) : PhysicalMetalForward = {
    null
  }
  def updateForward(fwd : PhysicalMetalForward){
//    fwd.asStarlingTrades...
  }
}

