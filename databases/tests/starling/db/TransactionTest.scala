package starling.db

import starling.utils.StarlingTest
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.testng.Assert._
import org.testng.annotations.{BeforeTest, Test}

class TransactionTest extends DBTest {

  @Test
  def testInsideTransaction {
    inEmptyTestTable {
      db => {
        db.inTransaction {
          writer => {
            writer.insert("Test", Map("name" -> "one"))
            writer.insert("Test", Map("name" -> "two"))
            val res = Set() ++ db.queryWithResult("select * from Test", Map()) {
              rs => rs.getString("name")
            }
            assertEquals(Set("one", "two"), res)
          }
        }
      }
    }
  }

  @Test
  def testCommit {
    inEmptyTestTable {
      db => {
        db.inTransaction {
          writer => {
            writer.insert("Test", Map("name" -> "one"))
            writer.insert("Test", Map("name" -> "two"))
          }
        }
      }
      val res = Set() ++ db.queryWithResult("select * from Test", Map()) {
        rs => rs.getString("name")
      }
      assertEquals(Set("one", "two"), res)
    }
  }

  @Test
  def testAbort {
    inEmptyTestTable {
      db => {
        var caughtException = false
        try {
          db.inTransaction {
            writer => {
              writer.insert("Test", Map("name" -> "one"))
              writer.insert("Test", Map("name" -> "two"))
              throw new Exception("fail")
            }
          }
        } catch {
          case _ => {
            caughtException = true
            val res = db.queryWithResult("select * from Test", Map()) {
              rs => rs.getString("name")
            }
            assertTrue(res.isEmpty, "not empty: " + res)
          }
        }
        assertTrue(caughtException, "Didn't throw exception")
      }
    }
  }

  private def inEmptyTestTable(f: DB => Unit) = {
    val connection = getConnection("jdbc:derby:memory:transactionTest;create=true");
    val ds = new SingleConnectionDataSource(connection, true)
    val db = new DB(ds)
    //    try {
    //      db.inTransaction {
    //        writer => {
    //          writer.update(creatTestTable)
    //        }
    //      }
    //      try {
    //        f(db)
    //      } finally {
    //        db.inTransaction {
    //          writer => {
    //            writer.update("drop table Test")
    //          }
    //        }
    //      }
    //    } finally {
    //      connection.close
    //    }
    try {
      db.inTransaction {
        writer => {
          writer.update(creatTestTable)
          try {
            f(db)
          } finally {
            writer.update("drop table Test")
          }
        }
      }
    } finally {
      connection.close
    }
  }

  private val creatTestTable = """
CREATE TABLE Test (
  id int GENERATED By DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  name varchar(50) DEFAULT NULL
)"""
}
