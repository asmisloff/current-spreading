package ru.vniizht.asuterkortes.dto

import ru.vniizht.asuterkortes.dao.model.jsonb.ScheduledPeriod

data class TrafficScheduleDto(
        val id: Long = -1,
        val tractionCountId: Long,
        val stations: List<TrafficSchedulesStationDto>,
        val trains: List<TrafficSchedulesTrainsDto>
)

data class TrafficSchedulesTrainsDto (
        val number: Int,
        val category: String,
        val positions: List<ScheduledPeriod>
)

data class TrafficSchedulesStationDto (
        val name: String,
        val coordinate: Double
)

data class TrafficScheduleRequestDto(
        val id: Long?,
        val tractionCountId: Long,
        var interval: Int = 15,
        var quantity: Int = 1,
        var startTime: Int = 0
        // TODO добавить номер трэка
)

data class TrafficSchedulesSaveDto(
        val id: Long,
        val name: String,
        val description: String?
)

data class SchedulesShortDto(
        val id: Long,
        val name: String,
        val tractionCount: String,
        val description: String
)
