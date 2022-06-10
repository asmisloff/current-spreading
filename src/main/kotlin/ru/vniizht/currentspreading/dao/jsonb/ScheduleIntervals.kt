package ru.vniizht.currentspreading.dao.jsonb

data class ScheduleIntervals(
    val synchronousSingleDispatching: Int,
    val synchronousSingleArrival: Int,
    val synchronousArrival: Int,
    val spacings: List<Spacing>
)

data class Spacing(
    val leftCategoryId: Long,
    val intervals: List<CategoryToInterval>
)

data class CategoryToInterval(
    val topCategoryId: Long,
    val interval: Int
)

