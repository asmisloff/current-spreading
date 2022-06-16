package ru.vniizht.currentspreading.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.apache.commons.math3.complex.Complex
import org.springframework.boot.jackson.JsonComponent

@JsonComponent
class ComplexSerializer : StdSerializer<Complex>(Complex::class.java) {

    override fun serialize(value: Complex?, gen: JsonGenerator?, provider: SerializerProvider?) {
        gen!!.writeStartObject()
        gen.writeNumberField("re", value?.real ?: Double.NaN)
        gen.writeNumberField("im", value?.imaginary ?: Double.NaN)
        gen.writeEndObject()
    }

}

@JsonComponent
class ComplexDeserializer : StdDeserializer<Complex>(Complex::class.java) {

    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Complex {
        val node = p!!.codec.readTree<JsonNode>(p)
        return Complex(node["re"].numberValue().toDouble(), node["im"].numberValue().toDouble())
    }

}