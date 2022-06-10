package ru.vniizht.asuterkortes.dto

import ru.vniizht.currentspreading.dao.Transformer
import ru.vniizht.currentspreading.dto.ReferenceFullDto
import java.time.LocalDateTime
import java.time.LocalDateTime.now


data class TransformerDto(
        override val id: Long?,
        val changeTime: LocalDateTime = now(),
        override val name: String,
        val type: String?,
        val power: Int,
        val voltage: Double,
        val schema: String?,
        val voltageRegulation: Double?,
        val voltageShortCircuit: Double,
        val power_loss_short_circuit: Double,
        val power_loss_no_load: Double,
        val amperage_no_load: Double
) : ReferenceFullDto() {

    override fun toEntity() = Transformer(
            id = id,
            name = name,
            voltage = voltage,
            type = type,
            power = power,
            amperage_no_load = amperage_no_load,
            power_loss_no_load = power_loss_no_load,
            power_loss_short_circuit = power_loss_short_circuit,
            schema = schema,
            voltageRegulation = voltageRegulation,
            voltageShortCircuit = voltageShortCircuit

    )
}

fun TransformerDto.isBranched() = name.endsWith("**")

fun TransformerDto.isNotBranched() = !name.endsWith("**")
