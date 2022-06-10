package ru.vniizht.currentspreading.dto

import ru.vniizht.currentspreading.dao.enums.getLocomotiveCurrentByRussianName
import ru.vniizht.currentspreading.dao.enums.getLocomotiveTypeByRussianName
import ru.vniizht.asuterkortes.dao.model.jsonb.BrakingCharacteristics
import ru.vniizht.asuterkortes.dao.model.jsonb.LocomotiveResistanceToMotion
import ru.vniizht.asuterkortes.dao.model.jsonb.MotorThermalCharacteristics
import ru.vniizht.currentspreading.dao.Locomotive
import ru.vniizht.currentspreading.dao.jsonb.ElectricalPosition
import java.time.LocalDateTime

data class LocomotiveFullDto(
    val id: Long?,
    val changeTime: String?,
    val name: String = "",
    val current: String = "",
    val type: String = "",
    val power: Double,
    val weight: Double,
    val length: Double,
    val maxSpeed: Int,
    val motorType: String? = null,
    val powerSelfConsumption: Double? = null,
    val amperageSelfConsumption: Double? = null,
    val nominalCurrent: Int? = getLocomotiveCurrentByRussianName(current).nominal,
    val resistanceToMotion: LocomotiveResistanceToMotion,
    val electricalCharacteristics: List<ElectricalPosition>,
    val motorThermalCharacteristics: MotorThermalCharacteristics = MotorThermalCharacteristics(),
    val brakingCharacteristics: BrakingCharacteristics? = null
) {

    fun toEntity() = Locomotive(
            id = id,
            changeTime = LocalDateTime.now(),
            name = name,
            current = getLocomotiveCurrentByRussianName(current),
            type = getLocomotiveTypeByRussianName(type),
            power = power,
            weight = weight,
            length = length,
            maxSpeed = maxSpeed,
            motorType = motorType,
            powerSelfConsumption = powerSelfConsumption,
            amperageSelfConsumption = amperageSelfConsumption,
            motorThermalCharacteristics = motorThermalCharacteristics,
            resistanceToMotion = resistanceToMotion,
            electricalCharacteristics = electricalCharacteristics,
            brakingCharacteristics = brakingCharacteristics
    )
}