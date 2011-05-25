package starling.utils


import org.testng.Assert._
import starling.quantity.{Quantity, UOM}
import starling.pivot.PivotQuantity

object QuantityTestUtils{
  /** Assert that UOMs match and that values are within tolerance
   */
  def assertQtyEquals(actual : Quantity, expected : Quantity, tol : Double = 0.0, message : String = ""){
    val extra = if (actual.uom != expected.uom) " (" + actual.uom + "!=" + expected.uom + ")" else ""
    assertEquals(actual.value, expected.value, tol, message + extra)
    assertEquals(actual.uom, expected.uom, message)
  }

  def assertPivotQtyEquals(actualPQ : PivotQuantity, expected : Quantity, tol : Double = 0.0, message : String = ""){
    val actual = actualPQ.quantityValue.get
    assertEquals(actual.value, expected.value, tol, message)
    assertEquals(actual.uom, expected.uom, message)
  }

  /** Assert that the percentage difference between two quantities is small
   */
  def assertQtyClose(actual : Quantity, expected : Quantity, tol : Double = 0.0, min : Double = 1e-9, message : String = ""){
    assertEquals(actual.uom, expected.uom, message)
    val (a, e) = (actual.value, expected.value)
    if (a.abs < min){
      // Dealing with very small values,
      assert(a * e >= 0, "Signs differ " + message)
      assert(e.abs < 2 * min, "More than double " + message)
    } else {
      val diff = (a - e) / a
      assert(diff.abs <= tol, "Expected " + expected + ", got " + actual + "\ndiff = "+ diff + "\n" +  message)
    }
  }
}
