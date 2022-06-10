package ru.vniizht.asuterkortes.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.vladmihalcea.hibernate.type.util.ObjectMapperSupplier
import org.apache.commons.math3.complex.Complex

class CustomObjectMapperSupplier : ObjectMapperSupplier {

    override fun get(): ObjectMapper {
        val m = ObjectMapper().findAndRegisterModules()
        m.registerModule(
            SimpleModule("Complex")
                .addSerializer(ComplexSerializer())
                .addDeserializer(Complex::class.java, ComplexDeserializer())
        )

        return m
    }

}