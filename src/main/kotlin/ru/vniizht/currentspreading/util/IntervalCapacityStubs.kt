package ru.vniizht.asuterkortes.util

import ru.vniizht.asuterkortes.dto.*
import java.time.LocalDateTime


fun intervalCapacityDirectResultDto(dto: DailyCapacityRequestDto): DailyCapacityResultDto {
        val dailyCapacityResult = DailyCapacityResultDto(
                id = dto.id,
                name = dto.name,
                changeTime = LocalDateTime.now(),
                schemaId = dto.schemaId,
                objects = listOf(),
                mainReport = MainReportDto(
                        type = "Ошибка в расчетах",
                        schedules = "",
                        categories = "",
                        window = "",
                        schema = "",
                        result = listOf(
                                ParameterDto(parameter = "Заданные", interval = "", trainCount = ""),
                                ParameterDto(parameter = "По расчёту", interval = "", trainCount = "")
                        ),
                        dailyCapacity = null,
                        electricalTrains = null,
                        largestTrains = null
                ),
                limitIntervals = listOf(
                        LimitIntervalDto(
                            zoneName = "Ошибка в расчетах",
                            downTrans = "0*",
                            convertTrans = "0*",
                            autoTrans = "0*",
                            rectifier = "0*",
                            voltage = "0*",
                            heat = "0*",
                            total = "0*"
                        )
                ),
                capacity = listOf(),
                limitTools = listOf(),
                type = "DC"
        )
    return dailyCapacityResult
}

fun intervalCapacityAlternateResultDto(dto: DailyCapacityRequestDto): DailyCapacityResultDto {
        val dailyCapacityResult = DailyCapacityResultDto(
                id = dto.id,
                name = dto.name,
                changeTime = LocalDateTime.now(),
                schemaId = dto.schemaId,
                objects = listOf(),
                mainReport = MainReportDto(
                        type = "Ошибка в расчетах",
                        schedules = "",
                        categories = "",
                        window = "",
                        schema = "",
                        result = listOf(
                                ParameterDto(parameter = "Заданные", interval = "", trainCount = ""),
                                ParameterDto(parameter = "По расчёту", interval = "", trainCount = "")
                        ),
                        largestTrains = null,
                        electricalTrains = null,
                        dailyCapacity = null
                ),
                limitIntervals = listOf(
                        LimitIntervalDto(
                            zoneName = "Ошибка в расчетах",
                            downTrans = "0*",
                            convertTrans = "0*",
                            autoTrans = "0*",
                            rectifier = "0*",
                            voltage = "0*",
                            heat = "0*",
                            total = "0*"
                        )
                ),
            capacity = listOf(),
            limitTools = listOf(),
            type = "AC"
        )
    return dailyCapacityResult
}

fun intervalCapacityDoubleAlternateResultDto(dto: DailyCapacityRequestDto): DailyCapacityResultDto {
    val dailyCapacityResult = DailyCapacityResultDto(
        id = dto.id,
        name = dto.name,
        changeTime = LocalDateTime.now(),
        schemaId = dto.schemaId,
        objects = listOf(),
        mainReport = MainReportDto(
            type = "Ошибка в расчетах",
            schedules = "",
            categories = "",
            window = "",
            schema = "",
            result = listOf(
                ParameterDto(parameter = "Заданные", interval = "", trainCount = ""),
                ParameterDto(parameter = "По расчёту", interval = "", trainCount = "")
            ),
            largestTrains = null,
            electricalTrains = null,
            dailyCapacity = null
        ),
        limitIntervals = listOf(
            LimitIntervalDto(
                zoneName = "Ошибка в расчетах",
                downTrans = "0*",
                convertTrans = "0*",
                autoTrans = "0*",
                rectifier = "0*",
                voltage = "0*",
                heat = "0*",
                total = "0*"
            )
        ),
        capacity = listOf(),
        limitTools = listOf(),
        type = "ACD"
    )
    return dailyCapacityResult
}
