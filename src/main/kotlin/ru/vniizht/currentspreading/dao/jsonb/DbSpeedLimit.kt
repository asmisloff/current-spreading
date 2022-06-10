package ru.vniizht.currentspreading.dao.jsonb

import ru.vniizht.asuterkortes.counter.tractive.SpeedLimit

data class DbSpeedLimit(
    val startCoordinate: Double,
    val endCoordinate: Double,
    val limit: Int
) {
    fun toSpeedLimit() = SpeedLimit(
        coordinate = startCoordinate,
        limit = limit
    )
}