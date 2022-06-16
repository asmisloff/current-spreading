package ru.vniizht.currentspreading.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import ru.vniizht.asuterkortes.counter.tractive.ROUND_VALUE
import ru.vniizht.asuterkortes.counter.tractive.TIME_SLOT
import ru.vniizht.asuterkortes.dto.TrafficSchedulesStationDto
import ru.vniizht.currentspreading.dao.Locomotive
import ru.vniizht.currentspreading.dao.Track
import ru.vniizht.currentspreading.dao.TractiveCalculate
import ru.vniizht.currentspreading.dao.Train
import ru.vniizht.currentspreading.util.round
import ru.vniizht.currentspreading.util.toDateTime

data class TractionCountResultDto(
    val id: Long,
    val text: String,
    val graphicResult: List<TractionCountGraphicDto>,
    val stations: List<TrafficSchedulesStationDto>,
    val overheatTolerance: Double
)

data class TractionCountGraphicDto(
    val c: Double, //coordinate
    val s: Double, //speed
    val a: Double, //amperage
    val t: Double, //temperature
    val ma: Double, // motor amperage
    var sl: Int = 0, //speedLimit
    var p: Double = 0.0, //profile
    var i: Double = 0.0,
    val aa: Double = 0.0//i
)

data class TractionCountProfileForGraphicDto(
    val startCoordinate: Double,
    val endCoordinate: Double,
    val startHeight: Double,
    val i: Double
)

data class TractionCountRequestDto(
    val id: Long?,
    val locomotive: Locomotive,
    val train: Train,
    val track: Track,
    val timeSlot: Double = TIME_SLOT,
    val averagingPeriod: Double = (TIME_SLOT * ROUND_VALUE).round(3), //TODO 4,5 сек. добавить усреднение, если будут проблемы с производительностью
    val recuperation: Boolean = false,
    val idleOptimisation: Boolean = false,
    val reverse: Boolean = false,
    val adhesionCoefficient: Double = 1.0,
    val startSpeed: Double = 0.0,
    val speedZone: Int? = null,
    val voltage: Int? = null,
    val categoryId: Long,
    val trackNumber: Int,
    val onlyLoop: Boolean,
    val stops: List<StopDto> = listOf(),
    val tractionRates: List<TractionRateDto>,
    val initialOverheat: Double,
)

data class StopDto(
    val stationId: Long,
    val time: Int
)

data class TractionRateDto(
    val stationId: Long,
    val rate: Double // Кратность тяги. В данной реализации эквивалентна количеству локомотивов.
)

data class TractionCountSaveDto(
    val id: Long,
    val description: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TractionShortDto(
    val id: Long,
    val name: String,
    val changeTime: String,
    val track: String,
    val description: String,
    val locomotive: String,
    val train: String,
    val trackNumber: Int,
    val direction: String,
    val shortDescription: String,
    val weight: Int,
    val category: String,
    val road: String
)

fun TractiveCalculate.toShortDto() = TractionShortDto(
    id = id!!,
    changeTime = changeTime.toDateTime(),
    track = track.name,
    locomotive = locomotive.name,
    train = train.name,
    description = description,
    name = "${track.name}: $description",
    trackNumber = singleTrack.trackNumber,
    weight = weight,
    direction = if (direction) "нечет" else "чет",
    shortDescription = "${result.averagingPeriod}_${description}_${category.name}_${locomotive.name}_${weight}_${if (direction) "нечет" else "чет"}",
    category = category.name,
    road = track.road
)

data class RealVoltageDto(
    val coordinate: Double,
    var voltage: Double
)

data class TractionCountDtoPair(
    val odd: List<TractionShortDto>,
    val even: List<TractionShortDto>
)
