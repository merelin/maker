package starling.titan

import com.trafigura.edm.logistics.inventory._
import com.trafigura.edm.shared.types.TitanId
import com.trafigura.tradinghub.support.GUID
import com.trafigura.tradecapture.internal.refinedmetal._
import com.trafigura.edm.tradeservice.EdmGetTrades
import starling.utils.{Stopwatch, Log}
import com.trafigura.services.valuation.TradeManagementCacheNotReady
import com.trafigura.edm.trades.{CompletedTradeState, PhysicalTrade => EDMPhysicalTrade}


// for some strange reason EDM trade service converts titan quota ID with prefix NEPTUNE:
case class NeptuneId(id : String) {
  import NeptuneId._
  lazy val identifier: String = id match {
    case NeptuneIdFormat(id) => id
    case other => other
  }
  def titanId : TitanId = TitanId(identifier)
}

object NeptuneId {
  val NeptuneIdFormat = "NEPTUNE:(.*)".r
  def apply(titanId : TitanId) : NeptuneId = {
    NeptuneId(Option(titanId).map(_.value).getOrElse("No Titan ID"))
  }
}


/**
 * logistics service interface
 */
object LogisticsServices {
  type EdmAssignmentServiceWithGetAllAssignments = EdmAssignmentService with Object { def getAllAssignments() : List[EDMAssignment] }
  type EdmInventoryServiceWithGetAllInventory = EdmInventoryService with Object {
    def getAllInventoryLeaves() : List[EDMInventoryItem]
    def getAllInventory() : LogisticsInventoryResponse
  }
}

import LogisticsServices._

trait TitanLogisticsAssignmentServices extends ServiceProxy[EdmAssignmentServiceWithGetAllAssignments]
trait TitanLogisticsInventoryServices extends ServiceProxy[EdmInventoryServiceWithGetAllInventory]

trait ServiceProxy[T] {
  val service : T
}

trait TitanLogisticsServices {
  val assignmentService : TitanLogisticsAssignmentServices
  val inventoryService : TitanLogisticsInventoryServices
}


/**
 * Tactical ref data, service proxies / data
 *   also includes the trademgmt EDM trade serivce, this should be refactored to  separate out at some point
 */
trait TitanTacticalRefData {

  val edmMetalByGUID: Map[GUID, Metal]
  val futuresExchangeByID: Map[String, Market]
  val counterpartiesByGUID: Map[GUID, Counterparty]
  val shapesByGUID : Map[GUID, Shape]
  val gradeByGUID : Map[GUID, Grade]
  val locationsByGUID : Map[GUID, Location]
  val destLocationsByGUID : Map[GUID, DestinationLocation]
  val groupCompaniesByGUID : Map[GUID, GroupCompany]
}

trait TitanEdmTradeService extends Log {
  val titanGetEdmTradesService : EdmGetTrades

 def getTrade(id : TitanId) : EDMPhysicalTrade = {
    try {
      titanGetEdmTradesService.get(id).asInstanceOf[EDMPhysicalTrade]
    }
    catch {
      case e : Throwable => throw new ExternalTitanServiceFailed(e)
    }
  }

  def getAllCompletedTrades() : List[EDMPhysicalTrade] = {
    try {
      val sw = new Stopwatch()
      val edmTradeResult = titanGetEdmTradesService.getAll()
      log.info("Are EDM Trades available " + edmTradeResult.cached + ", took " + sw)
      //if (!edmTradeResult.cached) throw new TradeManagementCacheNotReady
      log.info("Got Edm Trade results " + edmTradeResult.cached + ", trade result count = " + edmTradeResult.results.size)
      val edmTrades = edmTradeResult.results.map(_.trade.asInstanceOf[EDMPhysicalTrade]).flatMap(Option(_)).filter(pt => pt.state == CompletedTradeState)

      // temporary code, trademgmt are sending us null titan ids
      val (nullIds, validIds) = edmTrades.span(_.titanId == null)
      if (nullIds.size > 0) {
        log.error("Null Titan trade IDs found!")
        log.error("null ids \n%s\n%s".format(nullIds, validIds))
        //assert(false, "Null titan ids found - fatal error")
      }
      edmTrades
    }
    catch {
      case e : Throwable => {
        e.getCause match {
          case _ : TradeManagementCacheNotReady => {
            Log.info("Trade management cache not ready")
            Nil
          }
          case _ => throw new ExternalTitanServiceFailed(e)
        }
      }
    }
  }
}

trait TitanServices extends TitanTacticalRefData with TitanEdmTradeService

class ExternalTitanServiceFailed(cause : Throwable) extends Exception(cause)
