package ru.vniizht.currentspreading.dao.jsonb

import ru.vniizht.asuterkortes.counter.tractive.AverageElement
import ru.vniizht.asuterkortes.dao.model.jsonb.ScheduledPeriod


data class TractiveCalculateResult(
        val averagingPeriod: Double,
        val voltage: Int,
        var elements: List<AverageElement>,
        val periods: List<ScheduledPeriod>,
        val report: String,
        var count: Int? = null
)