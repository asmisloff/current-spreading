package ru.vniizht.asuterkortes.dto

import ru.vniizht.asuterkortes.dao.model.CrossingCapacity
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.currentspreading.dao.Track
import ru.vniizht.currentspreading.dao.enums.CapacityType
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.util.toDateTime
import java.time.LocalDateTime
import java.time.LocalDateTime.now


data class DailyCapacityRequestDto(
    var id: Long?,
    override val name: String,
    val description: String,
    var schemaName: String?,
    var schemaType: SchemaType?,
    var trackName: String?,
    override val schemaId: Long,
    val trackId: Long,
    override val iterations: Int,
    val stationDurability: Double = 0.999,
    val networkDurability: Double = 0.999,
    val toolsDurability: Double = 0.999,
    var windowTime: Int?,
    val trainInterval: Int,
    var trackParameters: List<TrackParameterDto>?,
    override var scheduleParameters: List<ScheduleParametersDto>?,
    var result: DailyCapacityResultDto?,
    var firstTrackTraffic: TrackTrafficDto?,//TODO убрать при переходе на множественные пути
    var secondTrackTraffic: TrackTrafficDto?,//TODO убрать при переходе на множественные пути
    var firstScheduleParameters: ScheduleParametersDto?,//TODO убрать при переходе на множественные пути
    var secondScheduleParameters: ScheduleParametersDto?,//TODO убрать при переходе на множественные пути
    val trainString: String? = null,//TODO убрать при переходе на множественные пути
    val changeTime: String = now().toDateTime(),
    val road: String?,
    val userLogin: String?,
    val capacityType: String? = null
) : CapacityComputationRequest

interface CapacityComputationRequest {
    val scheduleParameters: List<ScheduleParametersDto>?
    val name: String
    val schemaId: Long
    val iterations: Int
}

data class ScheduleParametersDto(
    val tractionCountId: Long,
    val tractionDirection: String?,
    val connected: Boolean,
    val largestMass: Int,
    val middleMass: Int,
    val totalCount: Int,
    val interval: Int,
    val regime: String
)

data class TrackTrafficDto(
    val secondaryCount: Int,
    val secondaryCoefficient: Double,
    val suburbanCount: Int,
    val suburbanCoefficient: Double,
    val heavyCount: Int,
    val heavyCoefficient: Double,
    val middleCount: Int,
    val middleCoefficient: Double
)

data class TrackParameterDto(
    val priority: String, //'cargo' | 'passenger'
    val motionType: String, // 'odd' | 'even' | 'double'
    val windowTime: Int,
    val firstTrackTraffic: TrackTrafficDto,
    val secondTrackTraffic: TrackTrafficDto,
    val trainString: String
)

data class DailyCapacityResultDto(
    var id: Long?,
    val changeTime: LocalDateTime = now(),
    val schemaId: Long,
    val name: String,
    val objects: List<SchemaObjectDto>,
    var mainReport: MainReportDto,
    var limitIntervals: List<LimitIntervalDto>,
    var capacity: List<CapacityDto>,
    var limitTools: List<LimitToolsDto>,
    val type: String = "DC",
    var capacityType: String? = null
)

data class SchemaObjectDto(
    val coordinate: String,
    val type: String,
    val name: String,
    val convertorsCount: String,
    val voltage: String,
    val disabledOptions: String?
)

data class MainReportDto(
    val type: String,
    val schema: String,
    val schedules: String,
    val categories: String,
    val window: String,
    val result: List<ParameterDto>,
    val dailyCapacity: String?,
    val largestTrains: String?,
    val electricalTrains: String?
)

data class ParameterDto(
    val parameter: String,
    val interval: String,
    val trainCount: String
)

data class LimitIntervalDto(
    val zoneName: String,
    var downTrans: String,
    var convertTrans: String,
    var autoTrans: String = "0*",
    var rectifier: String,
    var voltage: String,
    var heat: String,
    var total: String
)

data class LimitIntervalsWithStatus(
    val zoneName: String,
    var downTrans: Pair<Int, Boolean> = Pair(41, false),
    var convertTrans: Pair<Int, Boolean> = Pair(41, false),
    var autoTrans: Pair<Int, Boolean> = Pair(41, false),
    var rectifier: Pair<Int, Boolean> = Pair(41, false),
    var voltage: Pair<Int, Boolean> = Pair(41, false),
    var heat: Pair<Int, Boolean> = Pair(41, false),
    var total: Pair<Int, Boolean> = Pair(41, false)
)

data class CapacityDto(
    val zoneName: String,
    val downTrans: String,
    val convertTrans: String,
    val autoTrans: String = "",
    val rectifier: String,
    val voltage: String,
    val heat: String,
    val total: String
)

data class LimitToolsDto(
    val zoneName: String,
    val limitTools: MutableList<ToolWithAnalogDto>
)

data class ToolWithAnalogDto(
    val toolText: String,
    val analogs: List<String>
)

fun DailyCapacityRequestDto.toEntity(track: Track, schema: ElectricalSchema, type: CapacityType): CrossingCapacity {
    val resultScheduleParameters = if (scheduleParameters.isNullOrEmpty()) {
        firstScheduleParameters?.let {
            secondScheduleParameters?.let {
                listOf(
                    firstScheduleParameters!!,
                    secondScheduleParameters!!
                )
            }
        } ?: listOf()
    } else {
        scheduleParameters!!
    }
    val resultTrackTraffic: List<TrackParameterDto> = if (trackParameters.isNullOrEmpty()) {
        firstTrackTraffic?.let {
            secondTrackTraffic?.let {
                listOf(
                    TrackParameterDto(
                        priority = "cargo",
                        motionType = "odd",
                        windowTime = windowTime ?: 11,
                        trainString = trainString ?: " ",
                        firstTrackTraffic = firstTrackTraffic!!,
                        secondTrackTraffic = firstTrackTraffic!!
                    ),
                    TrackParameterDto(
                        priority = "cargo",
                        motionType = "even",
                        windowTime = windowTime ?: 22,
                        trainString = trainString ?: "     ",
                        firstTrackTraffic = secondTrackTraffic!!,
                        secondTrackTraffic = secondTrackTraffic!!
                    )
                )
            }
        } ?: listOf()
    } else {
        trackParameters!!
    }
    val resultWindowTime = trackParameters?.first()?.windowTime ?: 77

    return CrossingCapacity(
        id = id,
        name = name,
        changeTime = now(),
        description = description,
        active = false,
        windowTime = resultWindowTime,
        toolsDurability = toolsDurability,
        networkDurability = networkDurability,
        stationDurability = stationDurability,
        schema = schema,
        iterations = iterations,
        track = track,
        scheduleParameters = resultScheduleParameters,//TODO переделать при переходе на множественные пути
        trackParameters = resultTrackTraffic,//TODO переделать при переходе на множественные пути
        trainInterval = trainInterval,
        result = result,
        type = type,
        userLogin = userLogin
    )
}

fun CrossingCapacity.toDto() = DailyCapacityRequestDto(
    id = id!!,
    name = name,
    description = description,
    trainInterval = trainInterval,
    trackParameters = trackParameters,
    scheduleParameters = scheduleParameters,
    iterations = iterations,
    stationDurability = stationDurability,
    networkDurability = networkDurability,
    toolsDurability = toolsDurability,
    windowTime = windowTime,
    schemaId = schema!!.id!!,
    schemaName = schema.name,
    trackName = track!!.name,
    trackId = track.id!!,
    firstTrackTraffic = if (trackParameters.size > 0) trackParameters[0].firstTrackTraffic else null,
    secondTrackTraffic = if (trackParameters.size > 1) trackParameters[1].firstTrackTraffic else null,
    firstScheduleParameters = scheduleParameters[0],
    secondScheduleParameters = scheduleParameters[1],
    result = result,
    road = track.road,
    changeTime = changeTime.toDateTime(),
    schemaType = schema.type,
    userLogin = userLogin,
    capacityType = type.russianName
)

enum class PartRegime(val russianName: String) {
    ALL("все"),
    NONE("0"),
    DOUBLE("1/2"),
    TRIPLE("1/3"),
    FOURTH("1/4")
}

val russianNamesToPartRegime = PartRegime.values().map { it.russianName to it }.toMap()

fun getPartRegimeOfRussianNames(russianName: String): PartRegime =
    russianNamesToPartRegime[russianName]
        ?: throw IllegalArgumentException("Unknown CargoPart with russianName: $russianName")

fun limitToolsToLimitToolsDirectReportDto(limitTools: List<LimitToolsDto>) =
    limitTools.map {
        LimitToolsDirectDailyCapacityReportDto(
            zoneName = it.zoneName,
            limitTools = it.limitTools.map { it.toolText }.joinToString(separator = ", ")
        )
    }

fun limitToolsToLimitToolsReportDto(limitTools: List<LimitToolsDto>) =
    limitTools.map {
        LimitToolsDirectDailyCapacityReportDto(
            zoneName = it.zoneName,
            limitTools = it.limitTools.map { it.toolText }.joinToString(separator = ", ")
        )
    }

data class LimitToolsDirectDailyCapacityReportDto(
    val zoneName: String,
    val limitTools: String
)
