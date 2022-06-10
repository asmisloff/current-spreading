package ru.vniizht.asuterkortes.dto

import ru.vniizht.currentspreading.dao.UkCompensationDevice
import ru.vniizht.currentspreading.dao.enums.getCompensationDeviceTypeOfRussianNames
import ru.vniizht.currentspreading.dto.ReferenceFullDto
import java.time.LocalDateTime


data class UkCompensationDeviceDto(
    override val id: Long?,
    val changeTime: LocalDateTime = LocalDateTime.now(),
    override val name: String,
    val regulationType: String = "",
    val regulationDiapason: Double?,
    val nominalPower: Double,
    val usefulPower: Double,
    val resistance: Double = 27.5 * 27.5 / (usefulPower / 1000)
) : ReferenceFullDto() {

    override fun toEntity() = UkCompensationDevice(
        id = id,
        name = usefulPower.toString(),
        nominalPower = nominalPower,
        regulationDiapason = regulationDiapason,
        regulationType = getCompensationDeviceTypeOfRussianNames(regulationType),
        resistance = resistance,
        usefulPower = usefulPower
    )
}
