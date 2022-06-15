package ru.vniizht.currentspreading.dto

import ru.vniizht.currentspreading.dao.enums.BrakeType
import ru.vniizht.currentspreading.dao.enums.LocomotiveCurrent

data class CurrentSpreadingTractiveRequestDto(
    val locomotiveCurrent: LocomotiveCurrent,
    val direction: Direction,
    val brakeType: BrakeType,
    val carQty: Int,
    val tractionRate: Double = 1.0
)

enum class Direction { LeftToRight, RightToLeft }
