package starling.market

import java.lang.String
import starling.daterange.ObservationTimeOfDay._
import starling.utils.StarlingEnum
import starling.utils.ImplicitConversions._
import starling.daterange.{Day, ObservationTimeOfDay}

trait DeliveryType {
  val name: String
  override def toString = name
}
case object DailyDelivery extends DeliveryType {
  val name = "Daily Delivery"
}
case object MonthlyDelivery extends DeliveryType {
  val name = "Monthly Delivery"
}

case class FuturesExchange(name: String, deliveryType: DeliveryType, closeTime:ObservationTimeOfDay) {
  lazy val markets = Market.futuresMarkets.filter(_.exchange == this)
  lazy val marketsByCommodityName = markets.toMapWithKeys(_.commodity.name.toLowerCase)
}

/**
 * Neptune uses contracts on COMEX and LME to price its trades
 */
trait NeptunePricingExchange extends FuturesExchange{
  def inferMarketFromCommodityName(neptuneCommodityName : String) : FuturesMarket
  def inferMarketFromCommodityCode(neptuneCommodityCode : String) : FuturesMarket
}

object NeptunePricingExchange{
  def fromNeptuneCode(code : String) : NeptunePricingExchange = Map(
    "CMX" → FuturesExchangeFactory.COMEX,
    "LME" → FuturesExchangeFactory.LME
  ).get(code) match {
    case Some(exchange) => exchange
    case None => throw new IllegalStateException("No exchange for Neptune code " + code)
  }
}

object FuturesExchangeFactory extends StarlingEnum(classOf[FuturesExchange], (f: FuturesExchange) => f.name) {
  val LME = new FuturesExchange("LME", DailyDelivery, LMEClose) with NeptunePricingExchange{
    def inferMarketFromCommodityName(neptuneCommodityName: String) = neptuneCommodityName match {
      case "Copper"	            => Market.LME_COPPER
      case "Lead"	              => Market.LME_LEAD
      case "Zinc"	              => Market.LME_ZINC
      case "Tin"	              => Market.LME_TIN
      case "Primary Aluminium"	=> Market.LME_ALUMINIUM
      case "Nickel"	            => Market.LME_NICKEL
      case "Aluminium Alloy"	  => Market.LME_ALUMINIUM_ALLOY
      case "Steel"	            => Market.LME_STEEL_BILLETS
      case _ => throw new IllegalStateException("No known LME market for Neptune commodity " + neptuneCommodityName)
    }
    def inferMarketFromCommodityCode(neptuneCommodityCode: String) = neptuneCommodityCode match {
      case "CAD"	            => Market.LME_COPPER
      case "PB"	              => Market.LME_LEAD
      case "ZN"	              => Market.LME_ZINC
      case "SN"	              => Market.LME_TIN
      case "AHD"	            => Market.LME_ALUMINIUM
      case "NI"	              => Market.LME_NICKEL
      case "AA"	              => Market.LME_ALUMINIUM_ALLOY
      case "STL"	            => Market.LME_STEEL_BILLETS
      case _ => throw new IllegalStateException("No known LME market for Neptune commodity code " + neptuneCommodityCode)
    }

    /**
     * The month dates follows something like this rule
     * 1. Shift today's month by 'nMonthsAhead'
     * 2. If the date produced has a day number greater than the last day of the month (e.g. 30th Feb), then move to the month's last day
     * 3. If the day is a non-working day then
     *  a) For Friday/Saturday (bank holiday) - move to the previous business day
     *  b) For Sunday/Monday (bank holiday) - move to the next business day, unless this would move us to the next month in which case move back
     *  c) Any other holiday - just move back
     *
     * These are based on a chat with the LME, the holiday behaviour I've made up - but not the weekends.
     * TODO - put in exact rule once we've received the copy of the regs from the LME
     */
    def monthDate(marketDay : Day, nMonthsAhead : Int) : Day = {
      val month = marketDay.containingMonth + nMonthsAhead
      val d : Int = month.lastDay.dayNumber min marketDay.dayNumber
      val cal = Market.cals.LME
      val firstBusDayInMonth = cal.thisOrNextBusinessDay(month.firstDay)
      val lastBusDayInMonth = cal.thisOrPreviousBusinessDay(month.lastDay)

      var day = Day(month.y, month.m, d)
      if (!cal.isBusinessDay(day)) {
        if (day.isSaturday || day.isFriday)
          day = cal.previousBusinessDay(day)
        else if (day.isSunday || day.isMonday)
          day = cal.nextBusinessDay(day)
        else
          day = cal.previousBusinessDay(day)
      }
      (day max firstBusDayInMonth) min lastBusDayInMonth
    }

    def threeMonthDate(marketDay : Day) = monthDate(marketDay, nMonthsAhead = 3)
    def twoMonthDate(marketDay : Day) = monthDate(marketDay, nMonthsAhead = 2)

  }

  val COMEX = new FuturesExchange("COMEX", MonthlyDelivery, COMEXClose) with NeptunePricingExchange{
    def inferMarketFromCommodityName(neptuneCommodityName: String) = neptuneCommodityName match {
      case "Copper"	            => Market.COMEX_HIGH_GRADE_COPPER
      case _ => throw new IllegalStateException("No known COMEX market for Neptune commodity " + neptuneCommodityName)
    }
    def inferMarketFromCommodityCode(neptuneCommodityCode: String) = neptuneCommodityCode match {
      case "CAD"	            => Market.COMEX_HIGH_GRADE_COPPER
      case _ => throw new IllegalStateException("No known COMEX market for Neptune commodity code " + neptuneCommodityCode)
    }
  }
  val NYMEX = new FuturesExchange("NYMEX", MonthlyDelivery, Default)
  val SFS = new FuturesExchange("SFS", MonthlyDelivery, SHFEClose)
  val BALTIC = new FuturesExchange("Baltic Exchange", MonthlyDelivery, Default)
  val ICE = new FuturesExchange("ICE", MonthlyDelivery, Default)
  val MDEX = new FuturesExchange("MDEX", MonthlyDelivery, Default)
  val EXBXG = new FuturesExchange("EXBXG", MonthlyDelivery, Default)
}