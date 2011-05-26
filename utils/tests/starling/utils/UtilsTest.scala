package starling.utils

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.WordSpec
import starling.utils.Utils._

// http://en.wikipedia.org/wiki/Comma-separated_values
class UtilsTest extends WordSpec with ShouldMatchers {
  "csvConverter" should {
    "Fields are separated by commas" in {
      csvConverter.convertRow(List("1997", "Ford", "E350")) should be === """1997,Ford,E350"""
    }

    "Spaces are considered part of a field and should not be ignored" in {
      csvConverter.convertRow(List("1997", " Ford ", "E350")) should not
        (be === csvConverter.convertRow(List("1997", "Ford", "E350")))
    }

    "Fields with embedded commas must be enclosed within double-quote characters" in {
      csvConverter.convertRow(List("1997", "Ford", "E350", "Super, luxurious truck")) should be === """1997,Ford,E350,"Super, luxurious truck""""
    }

    "Fields with embedded double-quote characters must be enclosed within double-quote characters and " +
    "each of the embedded double-quote characters must be represented by a pair of double-quote characters." in {
      csvConverter.convertRow(List("1997", "Ford", "E350", "Super, \"luxurious\" truck")) should be ===
        """1997,Ford,E350,"Super, ""luxurious"" truck""""
    }

    "Fields with embedded line breaks must be enclosed within double-quote characters" in {
      csvConverter.convertRow(List("1997", "Ford", "E350", "Go get one now\nthey are going fast")) should be ===
        """1997,Ford,E350,"Go get one now""" + "\n" + """they are going fast""""
    }

    "Fields may always be enclosed within double-quote characters, whether necessary or not." in {
      csvConverter.convertRow(List("\"1997\"", "\"Ford\"", "\"E350\"")) should be === """"1997","Ford","E350""""
    }

    "rows are separated by newlines" in {
      csvConverter.convert(List(List("a", "b"), List("c", "d"))) should be === "a,b\nc,d"
    }
  }

  "tableConverter" should {
    "convert grid to html" in {
      tableConverter.convert(List(List("a", "b", ""), List("", "c", "d"))) should be ===
        "<table border='1'><tr><td>a</td><td>b</td><td>&nbsp;</td></tr>\n<tr><td>&nbsp;</td><td>c</td><td>d</td></tr></table>"
    }
  }
}