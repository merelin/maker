package starling.webservice

import net.liftweb.json._

import RichJValue._
import java.lang.{String, Class}


object JsonDeserializer {
  def deserialize(text: String)(implicit formats: Formats): Any = extract(JsonParser.parse(text).uncapitalize)
  def pretty(text: String)(implicit formats: Formats): String = Printer.compact(render(JsonParser.parse(text)))

  private def extract(json: JValue)(implicit formats: Formats): Any = json match {
    case JArray(elements) => elements.map(extract)
    case other => Extraction.extract(json, TypeInfo(classFrom(json, formats.typeHintFieldName), None))
  }

  private def classFrom(value: JValue, TypeHintFieldName: String): Class[_] = value match {
    case JObject(JField(TypeHintFieldName, JString(className)) :: _) => Class.forName(className)
    case _ => throw new Exception("Could not obtain type information from: " + value)
  }
}