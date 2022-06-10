package ru.vniizht.asuterkortes.dto

import ru.vniizht.asuterkortes.dao.model.DirectNetwork
import ru.vniizht.currentspreading.dto.NetworkShortDto

data class DirectNetworkSaveDto(
        val id: Long,
        val fiderId: Long?,
        val fiderCount: Int?,
        val contactWireId: Long?,
        val contactWireCount: Int?,
        val powerWireId: Long?,
        val powerWireCount: Int?,
        val railId: Long?,
        val railCount: Int?
)

data class DirectNetworkShortDto(
        override val id: Long,
        override val name: String,
        val wiresResistance: Double,
        val railsResistance: Double?,
        val limitAmperage: Double,
        val limitTemperature: Int,
        val thermalConstant: Double,
        val contactAmperage: Double?
) : NetworkShortDto()

data class DirectNetworkFullDto(
        val id: Long,
        val name: String,
        val fiderId: Long?,
        val fiderCount: Int?,
        val contactWireId: Long?,
        val contactWireCount: Int?,
        val powerWireId: Long?,
        val powerWireCount: Int?,
        val railId: Long?,
        val railCount: Int?,
        val wiresResistance: Double,
        val railsResistance: Double?,
        val limitAmperage: Double,
        val limitTemperature: Int,
        val thermalConstant: Double,
        val contactAmperage: Double?,
        val limitWire: String = ""
)

fun DirectNetwork.toShortDto() = DirectNetworkShortDto(
        id = id!!,
        name = name,
        wiresResistance = wiresResistance,
        railsResistance = railsResistance,
        limitAmperage = limitAmperage,
        limitTemperature = limitTemperature,
        thermalConstant = thermalConstant,
        contactAmperage = contactAmperage
)

fun DirectNetwork.toFullDto() = DirectNetworkFullDto(
        id = id!!,
        name = name,
        wiresResistance = wiresResistance,
        railsResistance = railsResistance,
        limitAmperage = limitAmperage,
        limitTemperature = limitTemperature,
        thermalConstant = thermalConstant,
        contactAmperage = contactAmperage,
        fiderId = fider?.id,
        fiderCount = fiderCount,
        contactWireId = contactWire?.id,
        contactWireCount = contactWireCount,
        powerWireId = powerWire?.id,
        powerWireCount = powerWireCount,
        railId = rail?.id,
        railCount = railCount
)

fun getNullDirectNetwork() =
        DirectNetworkShortDto(
                id = -1,
                wiresResistance = 0.0,
                railsResistance = 0.0,
                contactAmperage = 0.0,
                thermalConstant = 1.0,
                limitTemperature = 9999,
                limitAmperage = 99999999.0,
                name = "Нулевой"
        )
