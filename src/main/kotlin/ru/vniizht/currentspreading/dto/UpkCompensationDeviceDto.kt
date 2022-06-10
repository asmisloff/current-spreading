package ru.vniizht.asuterkortes.dto

import ru.vniizht.currentspreading.dao.UpkCompensationDevice
import ru.vniizht.currentspreading.dao.enums.getCompensationDeviceTypeOfRussianNames
import ru.vniizht.currentspreading.dto.ReferenceFullDto
import java.time.LocalDateTime


data class UpkCompensationDeviceDto(
    override val id: Long?,
    val changeTime: LocalDateTime = LocalDateTime.now(),
    override val name: String,
    val regulationType: String = "",
    val regulationDiapason: Double?,
    val nominalPower: Double,
    val nominalAmperage: Double,
    val resistance: Double = nominalPower / (nominalAmperage * nominalAmperage)
) : ReferenceFullDto() {

    override fun toEntity() = UpkCompensationDevice(
        id = id,
        name = "$nominalPower $nominalAmperage",
        nominalPower = nominalPower,
        regulationDiapason = regulationDiapason,
        regulationType = getCompensationDeviceTypeOfRussianNames(regulationType),
        resistance = resistance,
        nominalAmperage = nominalAmperage
    )
}
