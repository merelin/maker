package starling.webservice.meta

import javax.ws.rs._


import core.Context
import scalaz._
import Scalaz._
import starling.utils.ImplicitConversions._
import java.lang.annotation.Annotation
import com.thoughtworks.paranamer.BytecodeReadingParanamer
import java.lang.reflect.{Type, Method}
import com.trafigura.services.Example
import org.jboss.resteasy.annotations.{Suspend, Form}

case class WebService(uri: String, serviceType: String, methods: List[WebMethod])

object WebService {
  def fromClass(serviceClass: Class[_]): WebService = {
    val paths: List[Path] = allClasses(serviceClass).map(_.getAnnotation(classOf[Path])).filterNot(_ == null)
    val path = paths.headOption.getOrThrow("Missing Path annotation")

    val methods: List[Method] = allMethods(serviceClass)
    val webMethods: List[WebMethod] = methods.flatMapO(webMethod).distinct
    WebService(path.value, serviceClass.getName, webMethods)
  }

  private def webMethod(method: Method): Option[WebMethod] = try {
    val matchingMethods = allClasses(method.getDeclaringClass)
      .safeMap(_.getMethod(method.getName, method.getParameterTypes : _*)).filterNot(_ == null)

    def getAnnotation[T <: Annotation](annotation: Class[T]): Option[T] = {
      matchingMethods.map(_.getAnnotation(annotation)).filterNot(_ == null).headOption
    }

    val optPath: Option[String] = getAnnotation(classOf[Path]).map(_.value).filterNot(_.isEmpty)

    def verb[T <: Annotation](verbAnnotation: Class[T]): Option[String] =
      if (getAnnotation(verbAnnotation).isEmpty) None else Some(verbAnnotation.getSimpleName)

    val optVerb = verb(classOf[GET]) orElse verb(classOf[PUT]) orElse verb(classOf[POST]) orElse verb(classOf[DELETE])
    val bindings: List[Option[String]] = merge(matchingMethods.map(_.getParameterAnnotations.toList.map(binding)))
    val webParas = webParameters(method, bindings)
    val example = getAnnotation(classOf[Example]).map(_.value()).getOrElse("")

    (optPath, optVerb) partialMatch {
      case (Some(path), Some(verb)) =>
        WebMethod(path.hashCode, path, verb, method.getName, stripStuff(method.getGenericReturnType.toString), webParas, example)
    }
  } catch {
    case e => {
//      Console.err.println(method.getName + "." + method.getDeclaringClass)
//      e.printStackTrace;
      None
    }
  }

  def stripStuff(typeName: String) = typeName.strip("scala.collection.immutable.", "scala.").stripPrefix("class ")

  def merge[T](options: List[List[Option[T]]]): List[Option[T]] = {
    val empty = none[T].replicate[List](options.head.size)

    (empty /: options)((left, right) => (left zip right) map { case (l, r) => l orElse r } )
  }

  private def webParameters(method: Method, bindings: List[Option[String]]): List[WebMethodParameter] = {
    val zipped = method.getGenericParameterTypes zip bindings zip paranamer.lookupParameterNames(method) toList

    zipped.map { case ((clazz, binding), name) =>
      WebMethodParameter(name.takeWhile(_ != '$'), stripStuff(clazz toString), binding getOrElse "None")
    }
  }

  private def binding(annotations: Array[Annotation]) = annotations.toList.find(ResteasyTypes.matchingAnnotation).map(_ match {
    case _: PathParam => "Path"
    case _: QueryParam => "Query"
    case _: HeaderParam => "Header"
    case _: FormParam => "Form"
    case _: CookieParam => "Cookie"
    case _: MatrixParam => "Matrix"
  })

  private val paranamer = new BytecodeReadingParanamer

  private def allMethods(serviceClass: Class[_]): List[Method] = allClasses(serviceClass).flatMap(_.getMethods.toList).distinct

  private def allClasses(serviceClass: Class[_]): List[Class[_]] = {
    def recurse(clazz: Class[_]): List[Class[_]] = if (clazz == null) Nil else {
      clazz :: clazz.getInterfaces.toList.flatMap(recurse) ::: recurse(clazz.getSuperclass)
    }

    recurse(serviceClass).distinct
  }
}

case class WebMethod(id: Int, uri: String, verb: String, name: String, returnType: String, parameters: List[WebMethodParameter],
                     example: String)

case class WebMethodParameter(name: String, parameterType: String, binding: String)

object ResteasyTypes {
  def matchingAnnotation(annotation: Annotation) =  annotationTypes.contains(annotation.annotationType())

  val annotationTypes = Set(classOf[PathParam], classOf[QueryParam], classOf[HeaderParam], classOf[FormParam],
    classOf[CookieParam], classOf[MatrixParam], classOf[Form], classOf[Suspend], classOf[Context]).asInstanceOf[Set[Class[_ <: Annotation]]]
}