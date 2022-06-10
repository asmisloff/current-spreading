package ru.vniizht.currentspreading.dto

import com.fasterxml.jackson.annotation.*
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.IntegralIndexRecord
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.asuterkortes.dao.model.ParallelSchedule
import ru.vniizht.asuterkortes.dto.CapacityComputationRequest
import ru.vniizht.asuterkortes.dto.DailyCapacityRequestDto
import ru.vniizht.asuterkortes.dto.ScheduleParametersDto
import ru.vniizht.currentspreading.dao.Track
import ru.vniizht.currentspreading.dao.enums.CapacityType
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.util.round
import ru.vniizht.currentspreading.util.toDateTime
import java.time.LocalDateTime
import java.time.LocalDateTime.now


data class ParallelScheduleRequestDto(
    var id: Long?,
    override val name: String,
    val description: String,
    var schemaName: String,
    var trackName: String,
    override val schemaId: Long,
    var schemaType: SchemaType?,
    val trackId: Long,
    override val iterations: Int,
    val startTime: Int?,
    val totalTime: Int?,
    override var scheduleParameters: List<ScheduleParametersDto>?,
    val firstScheduleParameters: ScheduleParametersDto?,
    val secondScheduleParameters: ScheduleParametersDto?,
    var result: ParallelScheduleResultDto?,
    val changeTime: String = now().toDateTime(),
    val road: String?,
    val userLogin: String?
) : CapacityComputationRequest {

    companion object {
        fun from(dto: DailyCapacityRequestDto) = ParallelScheduleRequestDto(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            schemaName = dto.schemaName ?: "",
            trackName = dto.trackName ?: "",
            schemaId = dto.schemaId,
            schemaType = dto.schemaType,
            trackId = dto.trackId,
            iterations = dto.iterations,
            startTime = null,
            totalTime = null,
            scheduleParameters = dto.scheduleParameters,
            firstScheduleParameters = null,
            secondScheduleParameters = null,
            result = null,
            changeTime = dto.changeTime,
            road = dto.road,
            userLogin = dto.userLogin
        )
    }

}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "schemaType",
    defaultImpl = ParallelScheduleACResultDto::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ParallelScheduleACResultDto::class, name = "AC/ACD"),
    JsonSubTypes.Type(value = ParallelScheduleDCResultDto::class, name = "DC")
)
open class ParallelScheduleResultDto(
    open var id: Long?,
    open val changeTime: LocalDateTime = now(),
    open val schemaId: Long,
    open val name: String,
    open val integralIndexRecords: List<IntegralIndexRecord>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParallelScheduleACResultDto(
    override var id: Long?,
    override val name: String,
    override val changeTime: LocalDateTime,
    override val schemaId: Long,
    override val integralIndexRecords: List<IntegralIndexRecord>? = null,
//    val mainReport: ParallelScheduleMainReportDto? = null,
//    val minVoltages: List<VoltageAverageResultDto> = listOf(),
//    val stationsAverageResults: List<StationsAverageResultDto> = listOf(),
//    val fidersResults: List<FidersResultDto> = listOf()
) : ParallelScheduleResultDto(id, changeTime, schemaId, name, integralIndexRecords)

data class StationsAverageResultDto(
    val name: String,
    val minAmperage1: Double, // TODO может maxAmperage или просто amperage?
    val minAmperage3: Double,
    val minAmperage20: Double,
    val averageAmperage: Double,
    val downTrans1: Double,
    val downTrans10: Double,
    val downTrans60: Double,
    val downTransAverage: Double,
    val downTransOilTemperature: Double,
    val downTransNntTemperature: Double,
    val leftAverageVoltage: Int,
    val leftActiveEnergy: Int,
    val leftReactiveEnergy: Int,
    val rightAverageVoltage: Int,
    val rightActiveEnergy: Int,
    val rightReactiveEnergy: Int
)

data class StationEnergyDto(
    val name: String,
    val leftActiveEnergy: Double,
    val leftReactiveEnergy: Double,
    val rightActiveEnergy: Double,
    val rightReactiveEnergy: Double
)

data class FidersResultDto(
    val stationName: String,
    val fiders: List<FiderResultDto>
)

data class FiderResultDto(
    val fiderName: String,
    val amperage1: Double,
    val amperage3: Double,
    val amperage20: Double,
    val temperature1: Double,
    val temperature3: Double,
    val temperature20: Double,
    val typeName: String,
) : Verifiable()

data class VoltageAverageResultDto(
    val mpzName: String,
    val average1: Double,
    val average3: Double,
    val minVoltage: Double,
    val minTrack: Int,
    val minTrainNumber: Int,
    val minCoordinate: Double
)

data class ParallelScheduleMainReportDto(
    val zero: Tripple,
    val one: Tripple,
    val two: Tripple,
    val three: Tripple,
    val four: Tripple,
    val five: Tripple,
    val six: Tripple,
    val seven: Tripple,
    val eight: Tripple,
    val nine: Tripple,
    val ten: Tripple,
    val eleven: Tripple,
    val twelve: Tripple
)

data class Tripple(
    val one: String,
    val two: String,
    val three: String
)


fun ParallelScheduleRequestDto.toEntity(
    track: Track,
    schema: ElectricalSchema,
    type: CapacityType
) = ParallelSchedule(
    id = id,
    active = false,
    changeTime = now(),
    name = name,
    description = description,
    type = type,
    track = track,
    schema = schema,
    iterations = iterations,
    startTime = startTime ?: 0,
    finishTime = totalTime?.let { startTime?.plus(it) } ?: 0,
    scheduleParameters = listOf(
        firstScheduleParameters!!,
        secondScheduleParameters!!
    ),//TODO переделать при переходе на множественные пути
    result = result,
    userLogin = userLogin
)

fun ParallelSchedule.toDto() = ParallelScheduleRequestDto(
    id = id!!,
    name = name,
    description = description,
    scheduleParameters = scheduleParameters,
    iterations = iterations,
    schemaId = schema!!.id!!,
    schemaName = schema.name,
    trackName = track!!.name,
    trackId = track.id!!,
    firstScheduleParameters = scheduleParameters[0],
    secondScheduleParameters = scheduleParameters[1],
    startTime = startTime,
    totalTime = finishTime - startTime,
    result = result,
    schemaType = schema.type,
    changeTime = changeTime.toDateTime(),
    road = track.road,
    userLogin = userLogin
)

data class ParallelDcResults(
    val totalEnergy: Int,
    val lostEnergy: Int,
    val recuperation: Int,
    val limits: ParallelDcLimits
)

data class ParallelDcLimits(
    var rectifierAmperage: Pair<Double, String>,
    var rectifier: Pair<Double, String>,
    var convertTrans: Pair<Double, String>,
    var downTrans: Pair<Double, String>,
    var minVoltage: Pair<Double, String>,
    var averageVoltage: Pair<Double, String>,
    var contactHeat: Pair<Double, String>,
    var suckingHeat: Pair<Double, String>
)

data class ParallelAcResults(
    val totalActiveEnergy: Int,
    val totalReactiveEnergy: Int,
    val lostEnergy: Int,
    val recuperation: Int,
    val limits: ParallelAcLimits,
    val minVoltages: List<VoltageAverageResultDto> = listOf(),
    val stationsAverageResults: List<StationsAverageResultDto> = listOf(),
    val fidersResults: List<FidersResultDto> = listOf()
)

data class ParallelAcLimits(
    var transformer: Pair<Double, String>,
    var temperature: Pair<Int, String>,
    var minVoltage: Pair<Double, String>,
    var averageVoltage: Pair<Double, String>,
    var contactHeat: Pair<Double, String>,
    var suckingHeat: Pair<Double, String>
)

data class ParallelAcdResults(
    val totalActiveEnergy: Int,
    val totalReactiveEnergy: Int,
    val lostEnergy: Int,
    val recuperation: Int,
    val limits: ParallelAcdLimits,
    val minVoltages: List<VoltageAverageResultDto> = listOf(),
    val stationsAverageResults: List<StationsAverageResultDto> = listOf(),
    val fidersResults: List<FidersResultDto> = listOf()
)

data class ParallelAcdLimits(
    var transformer: Pair<Double, String>,
    var autoTrans: Pair<Double, String>,
    var temperature: Pair<Int, String>,
    var minVoltage: Pair<Double, String>,
    var averageVoltage: Pair<Double, String>,
    var contactHeat: Pair<Double, String>,
    var suckingHeat: Pair<Double, String>
)

// Постоянный ток

data class ParallelScheduleDCResultDto(
    override var id: Long?,
    override val changeTime: LocalDateTime,
    override val schemaId: Long,
    override val name: String,
    val mainReport: ParallelScheduleDCMainReportDto,
    val maxRectifierAmperages: List<MaxRectifierAmperagesDto>,
    val rectifierLoadFactors: List<RectifierLoadFactorsDto>,
    val mixingTransformerLoadFactors: List<MixingTransformerLoadFactorsDto>,
    val stepDownTransformerLoadFactors: List<StepDownTransformerLoadFactorsDto>,
    val wiresHeating: List<FidersResultDto>,
    val minVoltages: List<MinVoltageDto>
) : ParallelScheduleResultDto(id, changeTime, schemaId, name)

open class Verifiable {
    val verificationResults: MutableMap<String, VerificationResult> = HashMap()
}

data class ParallelScheduleMainReportDCEntry(
    val value: Double?,
    val comment: String
) : Verifiable() {
    init {
        verificationResults["value"] = VerificationResult(1.0, "")
    }
}

data class ParallelScheduleDCMainReportDto(
    val t0: ParallelScheduleMainReportDCEntry,
    val airTemp: ParallelScheduleMainReportDCEntry,
    val tracks: ParallelScheduleMainReportDCEntry,
    val energyReceived: ParallelScheduleMainReportDCEntry,
    val energyReturned: ParallelScheduleMainReportDCEntry,
    val limitRectAmp: ParallelScheduleMainReportDCEntry,
    val limitRectLoadFactor: ParallelScheduleMainReportDCEntry,
    val limitMixingTransLoadFactor: ParallelScheduleMainReportDCEntry,
    val limitStepDownTransLoadFactor: ParallelScheduleMainReportDCEntry,
    val minVoltage: ParallelScheduleMainReportDCEntry,
    val minAvg3Voltage: ParallelScheduleMainReportDCEntry,
    val limitCNTemp: ParallelScheduleMainReportDCEntry,
    val limitSuckingTemp: ParallelScheduleMainReportDCEntry
)

data class MaxRectifierAmperagesDto(
    val substationName: String,
    val maxAvg05: Double,
    val maxAvg2: Double,
    val maxAvg15: Double,
    val maxAvg30: Double,
    val avgAmp: Double,
    val avgVoltage: Double,
    val energyReceived: Double,
    val energyReturned: Double,
) : Verifiable()

data class RectifierLoadFactorsDto(
    val substationName: String,
    val maxAvg05: Double,
    val maxAvg2: Double,
    val maxAvg15: Double,
    val maxAvg30: Double,
    val avg: Double
) : Verifiable()

data class MixingTransformerLoadFactorsDto(
    val substationName: String,
    val maxAvg1: Double,
    val maxAvg2: Double,
    val maxAvg5: Double,
    val maxAvg15: Double,
    val maxAvg30: Double,
    val avg: Double
) : Verifiable()

data class StepDownTransformerLoadFactorsDto(
    val substationName: String,
    val maxAvg1: Double,
    val maxAvg10: Double,
    val avg: Double,
    @get:JsonProperty("tWinding")
    val tWinding: Double,
    @get:JsonProperty("tOil")
    val tOil: Double
) : Verifiable()

data class MinVoltageDto(
    val zone: String,
    val minVoltagesByTrack: List<MinVoltageForTrackDto>
)

data class MinVoltageForTrackDto(
    val trackNumber: Int,
    val min: Double?,
    val minAvg3: Double?,
    val minAvg1: Double? = null, // todo: для пассажирских поездов при скоростях от 160 до 250 км/ч
    val routeIndex: Int,
    val coord: Double?
) : Verifiable()

/**
 * Результат верификации значения интегрального показателя
 * @param safetyFactor коэффициент запаса надежности
 * @param explanation строка с пояснением
 */
data class VerificationResult(val safetyFactor: Double, val explanation: String) {
    constructor(value: Double, limitValue: Double)
            : this(limitValue / value, "Доп. знач. ${limitValue.round(2)}")

    constructor(value: Double, limitValue: Int)
            : this(limitValue / value, "Доп. знач. $limitValue")
}