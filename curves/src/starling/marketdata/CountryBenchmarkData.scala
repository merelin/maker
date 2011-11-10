package starling.marketdata

import starling.daterange.Day
import starling.market.Commodity
import starling.pivot._
import pivotparsers.{SpecifiedValuesParser, DayPivotParser}
import starling.quantity.Quantity
import scalaz.Scalaz._
import starling.utils.ImplicitConversions._
case class CountryBenchmarkData(countryData: NestedMap[NeptuneCountryCode, Day, Quantity]) extends MarketData {
  def size = countryData.nestedSize
}

class CountryBenchmarkDataType(referenceData: ReferenceDataLookup = ReferenceDataLookup.Null) extends MarketDataType {
  type dataType = CountryBenchmarkData
  type keyType = CountryBenchmarkMarketDataKey

  val commodityField = FieldDetails("Commodity", new SpecifiedValuesParser(Commodity.metalsCommodities.map(_.name).toSet))
  val areaField = FieldDetails("Area")
  val countryCodeField = FieldDetails.coded("Country", referenceData.countries.values)
  val effectiveFromField = FieldDetails("Effective From", DayPivotParser)
  val benchmarkPriceField = FieldDetails.createMeasure("Benchmark Price",
    parser0 = BenchmarkPricePivotParser, formatter0 = PivotQuantitySetPivotFormatter)

  def extendedKeys = List(commodityField)
  override def valueKeys = List(countryCodeField, effectiveFromField)
  override def derivedFieldDetails = List(areaField)
  def valueFieldDetails = List(benchmarkPriceField)

  val initialPivotState = PivotFieldsState(
    dataFields = List(benchmarkPriceField.field),
    rowFields = List(commodityField.field, countryCodeField.field, effectiveFromField.field),
    filters = List( Field("Observation Time") -> SomeSelection(Set("Default")) )
  )

  def createKey(row: Row) = CountryBenchmarkMarketDataKey(Commodity.fromName(row.string(commodityField)))

  def createValue(rows: List[Row]) = {
    val data = rows.map { row =>
      NeptuneCountryCode(row.string(countryCodeField)) → (row[Day](effectiveFromField), row.quantity(benchmarkPriceField))
    }

    CountryBenchmarkData(data.toNestedMap)
  }

  protected def fieldValuesFor(key: CountryBenchmarkMarketDataKey) = Row(commodityField.field → key.commodity.name)

  def rows(key: CountryBenchmarkMarketDataKey, data: CountryBenchmarkData) = data.countryData.mapNested {
    case (countryCode, effectiveFrom, price) => Row(
      commodityField.field → key.commodity.name,
      countryCodeField.field → countryCode.code,
      areaField.field → referenceData.areaFor(countryCode).map(_.name).getOrElse("Unknown"),
      effectiveFromField.field → effectiveFrom,
      benchmarkPriceField.field → price.pq
    )
  }
}

case class CountryBenchmarkMarketDataKey(commodity: Commodity) extends MarketDataKey {
  type marketDataType = CountryBenchmarkData
  type marketDataDBType = CountryBenchmarkData
  def typeName = MarketDataTypeName("CountryBenchmark")
  def subTypeKey = commodity.toString
  def fields = Set(Field("Commodity"))
}

// Prefixing these classes with 'Neptune' because they aren't really countries, they are Country + Location
case class NeptuneCountryCode(code: String) {
  override def toString = code
}
case class NeptuneCountry(code: NeptuneCountryCode, name: String, area: Option[Area]) extends Tupleable {
  def tuple = (code.code, name)
  override def toString = name
}
