package com.codahale.jerkson.ser

import org.codehaus.jackson.JsonGenerator
import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.map.annotate.JsonCachable
import java.lang.reflect.Modifier
import com.codahale.jerkson.util.CaseClassSigParser
import org.codehaus.jackson.map.{SerializationConfig, SerializerProvider, JsonSerializer}

@JsonCachable
class CaseClassSerializer[A <: Product](config: SerializationConfig,
                                        klass: Class[_],
                                        classLoader: ClassLoader) extends JsonSerializer[A] {
  private val params = CaseClassSigParser.parse(klass, config.getTypeFactory, classLoader).map(_._1).toArray
  private val nonIgnoredFields = klass.getDeclaredFields.filterNot { f =>
    f.getAnnotation(classOf[JsonIgnore]) != null ||
      (f.getModifiers & Modifier.TRANSIENT) != 0 ||
      f.getName.contains("$") ||
      !params.contains(f.getName)
  }

  private val methods = klass.getDeclaredMethods
                                .filter { _.getParameterTypes.isEmpty }
                                .map { m => m.getName -> m }.toMap
  
  def serialize(value: A, json: JsonGenerator, provider: SerializerProvider) {
    json.writeStartObject()
    for (field <- nonIgnoredFields) {
      val methodOpt = methods.get(field.getName)
      val fieldValue: Object = methodOpt.map { _.invoke(value) }.getOrElse(field.get(value))
      if (fieldValue != None) {
        val fieldName = methodOpt.map { _.getName }.getOrElse(field.getName)
        provider.defaultSerializeField(fieldName, fieldValue, json)
      }
    }
    json.writeEndObject()
  }
}
