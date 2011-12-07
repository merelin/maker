package starling.webservice

import java.lang.reflect.Type
import java.lang.annotation.Annotation
import javax.ws.rs.core.{MultivaluedMap, MediaType}
import java.lang.{String, Class}
import java.io.OutputStream
import javax.ws.rs.ext.{Provider, MessageBodyWriter}
import javax.ws.rs.Produces


@Provider
@Produces(Array("application/json"))
class JsonSerializerMessageBodyWriter extends MessageBodyWriter[Any] {
  private implicit val formats = EDMFormats

  def writeTo(value: Any, clazz : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType,
              httpHeaders: MultivaluedMap[String, AnyRef], entityStream: OutputStream) {

    entityStream.write(serialize(clazz, value).getBytes)
  }

  def getSize(value: Any, clazz : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType) = {
    serialize(clazz, value).getBytes.length
  }

  def isWriteable(clazz : Class[_], genericType: Type, annotations: Array[Annotation], mediaType: MediaType) = {
    true
  }

  private def serialize(clazz: Class[_], value: Any): String = if (clazz.isPrimitive || clazz == classOf[String]) {
    value.toString
  } else {
    JsonSerializer().serialize(value)
  }
}