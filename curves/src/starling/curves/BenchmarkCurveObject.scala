package starling.curves

import starling.market.Commodity
import starling.daterange.Period
import starling.quantity.Quantity
import starling.daterange.DayAndTime
import starling.marketdata._
import starling.utils.ImplicitConversions._

/**
 * Class for atomic benchmark data
 */
case class AreaBenchmarkAtomicKey(area: AreaCode, commodity: Commodity, grade: GradeCode,
  override val ignoreShiftsIfPermitted : Boolean = false
) 
  extends AtomicDatumKey(AreaBenchmarkCurveKey(commodity), (area, grade), ignoreShiftsIfPermitted)
{
  def periodKey : Option[Period] = None
  def nullValue = Quantity(0.0, commodity.representativeMarket.priceUOM)
  def forwardStateValue(originalAtomicEnv: AtomicEnvironment, forwardDayAndTime: DayAndTime) = {
    originalAtomicEnv.apply(this)
  }
}

/**
 * Benchmark curve key for benchmark area data
 */
case class AreaBenchmarkCurveKey(commodity : Commodity) extends NonHistoricalCurveKey[GradeAreaBenchmarkData]{
  override def typeName = "Price"
  def marketDataKey = GradeAreaBenchmarkMarketDataKey(commodity)
  def underlying = commodity.toString + " Benchmark"
  def buildFromMarketData(marketDay : DayAndTime, marketData : GradeAreaBenchmarkData) : CurveObject = {
    AreaBenchmarkCurveObject(marketDay, marketData)
  }
}

/**
 * Benchmark Location Curve for benchmarks using grade and area location as keys
 */
case class AreaBenchmarkCurveObject(marketDayAndTime : DayAndTime, marketData : GradeAreaBenchmarkData) extends CurveObject {
  val marketDataMap = marketData.areaData.withDefaultValue(Quantity.NULL)
  type CurveValuesType = Quantity

  def apply(point : AnyRef) = point match {
    case (area: AreaCode, grade: GradeCode) => {
      val benchmark: Quantity = marketDataMap((grade, area))

      benchmark
    }
  }
}

case class CountryBenchmarkAtomicKey(commodity: Commodity, country: NeptuneCountryCode,
  override val ignoreShiftsIfPermitted: Boolean = false
)
  extends AtomicDatumKey(CountryBenchmarkCurveKey(commodity), country, ignoreShiftsIfPermitted)
{
  def periodKey : Option[Period] = None
  def nullValue = Quantity(0.0, commodity.representativeMarket.priceUOM)
  def forwardStateValue(originalAtomicEnv: AtomicEnvironment, forwardDayAndTime: DayAndTime) = {
    originalAtomicEnv.apply(this)
  }
}

/**
 * Benchmark curve key for benchmark location data
 */
case class CountryBenchmarkCurveKey(commodity : Commodity) extends NonHistoricalCurveKey[CountryBenchmarkData]{
  override def typeName = "Price"
  def marketDataKey = CountryBenchmarkMarketDataKey(commodity)
  def underlying = commodity.toString + " Benchmark"
  def buildFromMarketData(marketDay : DayAndTime, marketData : CountryBenchmarkData) : CurveObject = {
    CountryBenchmarkCurveObject(marketDay, marketData)
  }
}


/**
 * Benchmark Location Curve for benchmarks using grade and location as keys
 */
case class CountryBenchmarkCurveObject(marketDayAndTime : DayAndTime, marketData : CountryBenchmarkData) extends CurveObject {
  val countryData = marketData.countryData.withDefaultValue(Quantity.NULL)
  type CurveValuesType = Quantity

  def apply(point : AnyRef) = point match {
    case country : NeptuneCountryCode => {
      val benchmark: Quantity = countryData(country)

      benchmark
    }
  }
}