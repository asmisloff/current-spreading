package ru.vniizht.currentspreading.dto

import ru.vniizht.currentspreading.dao.enums.BrakeType
import ru.vniizht.currentspreading.dao.enums.LocomotiveCurrent

data class CurrentSpreadingTractiveRequestDto(
    val locomotiveCurrent: LocomotiveCurrent,
    val direction: Direction,
    val brakeType: BrakeType,
    val carQty: Int
)

enum class Direction { Left, Right }
