package ru.vniizht.currentspreading.dto

import ru.vniizht.currentspreading.dao.WireAndRail


data class WireAndRailDto(
    override val id: Long?,
    val changeTime: String?,
    override val name: String,
    val directResistance: Double,
    val alternateResistance: Double,
    val radius: Double,
    val limitAmperage: Double? = null,
    val limitTemperature: Int? = null,
    val thermalConstant: Double? = null
) : ReferenceFullDto() {

    override fun toEntity() = WireAndRail(
        id = id,
        name = name,
        alternateResistance = alternateResistance,
        directResistance = directResistance,
        limitAmperage = limitAmperage,
        limitTemperature = limitTemperature,
        radius = radius,
        thermalConstant = thermalConstant
    )
}
