package starling.schemaevolution

import starling.db.DBWriter
import starling.dbx.QueryBuilder._
import starling.marketdata.MarketDataTypeName
import starling.utils.ImplicitConversions._


case class MarketDataPatchUtil(writer: DBWriter) {
  def deleteMarketData(removeOrphaned: Boolean, types: MarketDataTypeName*): Unit =
    deleteMarketData(removeOrphaned, types.map(_.name).toList)

  def deleteMarketData(removeOrphaned: Boolean = true, typeNames: scala.List[String]) {
    writer.update("DELETE FROM MarketDataValue WHERE extendedKey IN (" +
      "SELECT DISTINCT id FROM MarketDataExtendedKey WHERE marketDataType IN (%s))" % typeNames.map(_.quote).mkString(", "))

    writer.delete("MarketDataExtendedKey", "marketDataType" in typeNames)

    if (removeOrphaned) removeOrphanedMarketDataValueKeys
  }

  def removeOrphanedMarketDataValueKeys {
    // a more efficient alternative could be to query for them before removing the MarketDataValues, but that could result in a large 'in clause'
    writer.update("DELETE FROM MarketDataValueKey WHERE id NOT IN (SELECT DISTINCT valueKey FROM MarketDataValue)")
  }
}