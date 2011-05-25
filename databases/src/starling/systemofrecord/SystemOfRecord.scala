package starling.systemofrecord


import starling.instrument._
import org.joda.time.DateTime
import java.lang.String
import starling.richdb.{RichDB, RichResultSetRow}
import starling.utils._
import sql.{SqlRenderer, Query}
import oracle.net.aso.q
import starling.trade.Trade

abstract class SystemOfRecord(externalDB: RichDB) {
  protected def readers: List[InstrumentReader]

  def trade(tradeID: String)(f: Trade => Unit)

  def allTrades(f: Trade => Unit) : (Int, Set[String])

  /**
   * f is called with each Trade read in.
   * Returns a unique error count and a set of unique errors
   */
  protected def allTrades(query : String, parameters : Map[String, Any])(f: Trade => Unit) : (Int, Set[String]) = {
    var uniqueErrors = Set[String]()
    var errorCount = 0
    externalDB.query(query, parameters) {
      rs => {
        try {
          val nullTrade = createNullTrade(rs)

          val trade = try {
            val ins = createInstrument(rs)
            addCostsToTrade(nullTrade.copyWithInstrument(ins), rs)
          } catch {
            case e => {
              if (!uniqueErrors.contains(e.toString)) {
                Log.warn("Error Trade: " + e + " " + nullTrade.tradeID + " " + nullTrade.tradeDay)
                uniqueErrors = uniqueErrors + e.toString
              }
              errorCount += 1
              nullTrade.copyWithInstrument(ErrorInstrument(StackTraceToString.string(e).trim))
            }
          }
          f(trade)
        }
        catch {
          case e:AssertionError => throw e
          case e => Log.error("Problem creating empty trade: " + rs, e)
        }
      }
    }
    (errorCount, uniqueErrors)
  }

  protected def allTrades(q: Query)(f: Trade => Unit) : (Int, Set[String]) = {

    val renderer: SqlRenderer = new SqlRenderer()
    val sql = renderer.render(q)
    allTrades(sql.query, sql.parameters)(f)
  }

  protected def createInstrument(rs: RichResultSetRow): Tradeable = {
    try {
      readers.filter(r => r.canHandle(rs)) match {
        case reader :: Nil => reader.create(rs)
        case Nil => throw new Exception("No readers matched: " + rs)
        case r => throw new Exception("Too many readers (" + r + ") matched: " + rs)
      }
    }
    catch {
      case m:MatchError => throw new Exception("A broken reader is throwing a match error instead of returning false", m)
    }
  }

  protected def createNullTrade(rs: RichResultSetRow) : Trade

  protected def addCostsToTrade(trade: Trade, rs: RichResultSetRow) : Trade = trade
}
