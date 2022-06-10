package ru.vniizht.asuterkortes.dao.model.jsonb

data class ScheduledPeriod(
        val coordinate: Double,
        var inTime: Double,
        var outTime: Double,
        val step: Int
)