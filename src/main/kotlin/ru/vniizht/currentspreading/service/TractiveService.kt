package ru.vniizht.currentspreading.service

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.vniizht.asuterkortes.counter.tractive.*
import ru.vniizht.asuterkortes.dao.model.jsonb.ResistanceToMotion
import ru.vniizht.asuterkortes.dao.model.jsonb.ScheduledPeriod
import ru.vniizht.currentspreading.repository.TrackCategoryRepository
import ru.vniizht.asuterkortes.dto.NamesDto
import ru.vniizht.asuterkortes.dto.TrafficSchedulesStationDto
import ru.vniizht.currentspreading.core.schedule.OrderedList
import ru.vniizht.currentspreading.core.schedule.toOrderedList
import ru.vniizht.currentspreading.core.tractive.averaging
import ru.vniizht.currentspreading.core.tractive.tractiveCount
import ru.vniizht.currentspreading.dao.*
import ru.vniizht.currentspreading.dao.enums.*
import ru.vniizht.currentspreading.dao.jsonb.*
import ru.vniizht.currentspreading.dto.*
import ru.vniizht.currentspreading.dto.Direction.*
import ru.vniizht.currentspreading.repository.*
import ru.vniizht.currentspreading.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Service
class TractiveService(
    private val locomotiveRepository: LocomotiveRepository,
    private val trainRepository: TrainRepository,
    private val tractiveCalculateRepository: TractiveCalculateRepository,
    private val trackCategoryRepository: TrackCategoryRepository,
    private val trackRepository: TrackRepository,
    private val schemaRepository: ElectricalSchemaRepository,
    private val trainService: TrainService,
    private val carRepository: CarRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun performTractiveComputation(
        dto: TractionCountRequestDto,
        voltages: OrderedList<RealVoltageDto>? = null
    ): TractiveCalculate {
        val locomotive = dto.locomotive
        val train = dto.train
        val track = trackRepository.getById(dto.trackId)
        val reverse = dto.reverse

        validate(dto, locomotive)

        val singleTrack = track.singleTracks.first { it.trackNumber == dto.trackNumber }
        val sortedStations = singleTrack.stations.sortedBy { it.coordinate }
        val firstStation = sortedStations.first()
        val lastStation = sortedStations.last()
        val speedZone = dto.speedZone ?: when (locomotive.type) {
            LocomotiveType.FREIGHT_LOCOMOTIVE -> 15
            LocomotiveType.ELECTRIC_TRAIN -> 10
            LocomotiveType.PASSENGER_LOCOMOTIVE -> 2
        }
        val voltage = if (!(dto.voltage == null || dto.voltage == 0)) dto.voltage else locomotive.current.nominal
        val recuperation = if (locomotive.brakingCharacteristics == null) false else dto.recuperation
        val positions = locomotive.electricalCharacteristics.map { it.toCounterPosition() }

        val recuperationPositions = mutableListOf<Position>()
        locomotive.brakingCharacteristics?.let { brakingCharacteristics ->
            recuperationPositions.add(
                Position(
                    "Ограничивающая",
                    brakingCharacteristics.limit.map { it.toCounterCharacteristic() })
            )
            recuperationPositions.add(
                Position(
                    "Максимальная",
                    brakingCharacteristics.max.map { it.toCounterCharacteristic() })
            )
        }

        val stops = dto.stops.mapNotNull { stopDto ->
            sortedStations.find { station ->
                stopDto.stationId == station.id
            }?.let {
                TractiveStop(it.coordinate, stopDto.time)
            }
        }

        val tractionRates = dto.tractionRates.mapNotNull { rate ->
            sortedStations.find { it.id == rate.stationId }
                ?.let {
                    TractionRate(it.coordinate, rate.rate)
                }
        }

        val chosenCategory = trackCategoryRepository.findByIdOrNull(dto.categoryId)
            ?: singleTrack.categories[0]

        val category = singleTrack.categories.find { it.name == chosenCategory.name }
            ?: singleTrack.categories[0]

        val result = tractiveCount(
            forthProfile = singleTrack.profile,
            trackType = singleTrack.type,
            forthSpeedLimits = category.speedLimits.map { it.toSpeedLimit() },
            forthStartCoordinate = firstStation.coordinate,
            startSpeed = dto.startSpeed,
            startPosition = 0,
            tractivePositions = positions,
            forthFinishCoordinate = lastStation.coordinate,
            speedZone = speedZone,
            amperageSelfConsumption = locomotive.amperageSelfConsumption!!,
            locomotive = locomotive,
            recuperation = recuperation,
            adhesionCoefficient = dto.adhesionCoefficient,
            recuperationPositions = recuperationPositions,
            locomotiveCurrent = locomotive.current,
            reverseDirection = reverse,
            idleOptimisation = dto.idleOptimisation,
            voltageArray = voltages
                ?: listOf(RealVoltageDto(0.0, voltage.toDouble())).toOrderedList(),
            stops = stops,
            train = train,
            timeSlot = dto.timeSlot,
            tractionRateList = tractionRates,
            motorThermalCharacteristics = locomotive.motorThermalCharacteristics,
            initialOverheat = dto.initialOverheat
        )

        val averaging = averaging(result, dto.averagingPeriod, dto.timeSlot)
        val periods = getScheduledPeriods(averaging, dto.averagingPeriod, sortedStations, stops)

        return TractiveCalculate(
            id = dto.id,
            track = track,
            category = category,
            singleTrack = singleTrack,
            direction = !dto.reverse,
            active = false,
            weight = (train.weight + locomotive.weight).toInt(), // по соглашению в интегральном отчете масса поезда рассчитывается с одним локомотивом
            locomotive = locomotive,
            train = train,
            result = TractiveCalculateResult(dto.averagingPeriod, voltage, averaging, periods, "- / -"),
            stops = stops
        )
    }

    fun performTractiveComputation(req: CurrentSpreadingTractiveRequestDto): TractiveCalculate {
        val locomotive = locomotiveRepository.findByCurrent(req.locomotiveCurrent)

        val train = Train(
            name = "",
            brakeBlockType = req.brakeType,
            brakeForce = 1000.0,
            undercarGeneratorPower = 10.0,
            carsToTrain = mutableListOf(),
            resistanceToMotion = null,
            weight = 0.0,
            length = 0.0
        )

        val carsToTrain = CarsToTrain(
            id = CarsToTrainId(-1L, -1L),
            train = train,
            car = Car(
                name = "",
                numberOfAxles = NumberOfAxles.SIX_AXLES,
                weight = 20.0,
                length = 30.0,
                resistanceToMotion = ResistanceToMotion(
                    componentRail = Array(3) { 0.0 },
                    continuousRail = Array(3) { 0.0 }
                )
            ),
            count = req.carQty
        )
        (train.carsToTrain as MutableList).add(carsToTrain)
        train.resistanceToMotion = trainService.getTrainResistanceToMotion(train.carsToTrain)
        train.weight = train.carsToTrain.first().car!!.weight * req.carQty
        train.length = train.carsToTrain.first().car!!.length * req.carQty

        return performTractiveComputation(
            TractionCountRequestDto(
                id = null,
                locomotive = locomotive,
                train = train,
                trackId = 1,
                recuperation = false,
                idleOptimisation = true,
                reverse = req.direction == Right,
                categoryId = 1,
                trackNumber = 1,
                onlyLoop = true,
                tractionRates = listOf(),
                initialOverheat = 100.0
            )
        )
    }

    fun count(dto: TractionCountRequestDto): TractionCountResultDto {
        val tc = performTractiveComputation(dto)
        val calculate = tractiveCalculateRepository.save(tc)
        val graphic = tc.result.elements.map { it.toGraphicDto() }
        val singleTrack = tc.singleTrack
        val category = tc.category
        val sortedStations = tc.singleTrack.actualStations().sortedBy { it.coordinate }
        addSpeedLimitsOnGraphic(graphic, category.speedLimits)
        addProfileOnGraphic(graphic, singleTrack.profile)
        val stations = sortedStations.map {
            TrafficSchedulesStationDto(
                coordinate = it.coordinate,
                name = it.name
            )
        }
        return TractionCountResultDto(
            calculate.id!!,
            tc.result.report,
            graphic,
            stations,
            tc.locomotive.motorThermalCharacteristics.overheatTolerance
        )
    }

    fun count(dto: TractionCountRequestDto, voltages: OrderedList<RealVoltageDto>?): TractiveCalculate {
        return performTractiveComputation(dto, voltages)
    }

    private fun addProfileOnGraphic(graphic: List<TractionCountGraphicDto>, profile: List<ProfileElement>) {
        val profileForGraphic: List<TractionCountProfileForGraphicDto> = convertProfile(profile)
        val minHeight = profileForGraphic.minByOrNull { it.startHeight }!!.startHeight
        val gap = if (minHeight < 0) {
            -minHeight + 5
        } else {
            5.0
        }
        graphic.forEach {
            it.p = getProfileHeightOnCoordinate(profileForGraphic, it.c) + gap
            it.i = getProfileIOnCoordinate(profileForGraphic, it.c)
        }
    }

    private fun getProfileHeightOnCoordinate(profile: List<TractionCountProfileForGraphicDto>, c: Double): Double {
        profile.forEach {
            if (it.startCoordinate < c && it.endCoordinate + 0.01 > c) {
                return (c - it.startCoordinate) * it.i + it.startHeight
            }
        }
        return 0.0
    }

    private fun getProfileIOnCoordinate(profile: List<TractionCountProfileForGraphicDto>, c: Double): Double {
        profile.forEach {
            if (it.startCoordinate < c && it.endCoordinate + 0.01 > c) {
                return it.i
            }
        }
        return 0.0
    }

    private fun convertProfile(profile: List<ProfileElement>): List<TractionCountProfileForGraphicDto> {
        val result = mutableListOf<TractionCountProfileForGraphicDto>()
        var height = 0.0
        profile.forEach {
            result.add(
                TractionCountProfileForGraphicDto(
                    startCoordinate = it.startCoordinate,
                    endCoordinate = it.startCoordinate + it.length,
                    i = it.i,
                    startHeight = height
                )
            )
            height += it.length * it.i
        }
        return result
    }

    private fun addSpeedLimitsOnGraphic(graphic: List<TractionCountGraphicDto>, speedLimits: List<DbSpeedLimit>) {
        graphic.forEach {
            it.sl = getLimitOnCoordinate(speedLimits, it.c)
        }
    }

    private fun getLimitOnCoordinate(speedLimits: List<DbSpeedLimit>, c: Double): Int {
        speedLimits.forEach {
            if (it.startCoordinate <= c && it.endCoordinate >= c) {
                return it.limit
            }
        }
        return if (abs(speedLimits.first().startCoordinate - c) > abs(speedLimits.last().startCoordinate - c)) {
            speedLimits.last().limit
        } else {
            speedLimits.first().limit
        }
    }

    fun getTractiveCalculateGraphResult(id: Long): List<TractionCountGraphicDto> {
        val result = tractiveCalculateRepository.getById(id).result
        return result.elements.map { it.toGraphicDto() }
    }

    private fun validate(dto: TractionCountRequestDto, locomotive: Locomotive) {

        if (dto.averagingPeriod < dto.timeSlot) {
            throw IllegalStateException("Период усреднения должен быть меньше шага расчета")
        }

        if (abs((dto.averagingPeriod / dto.timeSlot).roundToInt() - (dto.averagingPeriod / dto.timeSlot)) > 0.0001) {
            throw IllegalStateException("Период усреднения должен быть кратен шагу расчета")
        }

        if (dto.recuperation) {
            if (locomotive.brakingCharacteristics == null ||
                locomotive.brakingCharacteristics.limit.isEmpty()
            ) {
                throw IllegalStateException("Режим рекуперации для локомотива ${locomotive.name} не предусмотрен")
            }
        }
        checkFieldInRange(dto.startSpeed, "Начальная скорость", 0.0, 574.0)
        if (dto.startSpeed > locomotive.maxSpeed) {
            throw IllegalStateException("Начальная скорость превышает конструкционную скорость локомотива ${locomotive.name}")
        }
        checkFieldInRange(dto.adhesionCoefficient, "Коэф. снижения сцепления", 0.5, 1.0)
        if (dto.speedZone != null) {
            checkFieldInRange(dto.speedZone.toDouble(), "Зона регулирования скорости", 0.0, 30.0)
        }
        if (dto.voltage != null) {
            checkFieldInRange(
                dto.voltage.toDouble(), "Напряжение контактной сети",
                locomotive.current.nominal / 1.6, locomotive.current.nominal * 1.35
            )
        }
    }

    private fun getScheduledPeriods(
        averaging: List<AverageElement>,
        period: Double,
        stations: List<Station>,
        stops: List<TractiveStop>
    ): List<ScheduledPeriod> {
        val result = mutableListOf<ScheduledPeriod>()
        val loopStations = stations.filter { it.loopStation }.toMutableList()
        if (loopStations.isEmpty() || stations.first().coordinate != loopStations.first().coordinate) {
            loopStations.add(0, stations.first())
        }
        if (loopStations.isEmpty() || stations.last().coordinate != loopStations.last().coordinate) {
            loopStations.add(stations.last())
        }
        var j = when (averaging.first().c > averaging.last().c) {
            true -> loopStations.lastIndex // реверс
            false -> 0
        }
        var lastTime = 0.0
        var startTime: Double
        var finishTime: Double
        var lastDistance = 999999.9
        for (i in averaging.indices) {
            if (abs(loopStations[j].coordinate - averaging[i].c) <= lastDistance && i < averaging.lastIndex) {
                lastDistance = abs(loopStations[j].coordinate - averaging[i].c)
            } else {
                startTime = i * period - lastTime
                val stop = stops.find { it.coordinate == loopStations[j].coordinate }
                finishTime = if (stop != null) {
                    startTime + stop.time
                } else {
                    startTime
                }
                result.add(
                    ScheduledPeriod(
                        loopStations[j].coordinate,
                        startTime.round(2),
                        finishTime.round(2),
                        i
                    )
                )
                lastTime += startTime
                lastDistance = 999999.9
                if (averaging[0].c > averaging[averaging.lastIndex].c) {
                    j--
                } else {
                    j++
                }
                if (j == loopStations.size || j == -1) {
                    break
                }
            }
        }
        return result
    }

    @Transactional(readOnly = true)
    fun getAll(searchText: String?, pageable: Pageable): PagedResult<TractionShortDto> {
        return searchText?.let {
            tractiveCalculateRepository.findWithFilter(searchText, pageable).map { it.toShortDto() }.toDto()
        }
            ?: tractiveCalculateRepository.findAllByActiveTrue(pageable).map { it.toShortDto() }.toDto()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(dto: TractionCountSaveDto): Long {
        var calculate = tractiveCalculateRepository.getById(dto.id)
        calculate.description = dto.description ?: ""
        calculate.active = true
        calculate = tractiveCalculateRepository.save(calculate)
        return calculate.id!!
    }

    @Transactional(readOnly = true)
    fun getAllTractionNames(): List<NamesDto> {
        return tractiveCalculateRepository.findAllByActiveTrue()
            .map {
                NamesDto(
                    it.id!!,
                    "${it.result.averagingPeriod}_${it.description}_${it.category.name}_${it.locomotive.name}_${it.weight}_${if (it.direction) "нечет" else "чет"}"
                )
            }
    }

    @Transactional(readOnly = true)
    fun getAllByTrackId(trackId: Long, pageable: Pageable): PagedResult<TractionShortDto> {
        return tractiveCalculateRepository.findAllByActiveTrueAndTrack_Id(trackId, pageable).map { it.toShortDto() }
            .toDto()
    }

    fun getAllByTrackIdAndNumber(
        trackId: Long,
        schemaId: Long,
        trackNumber: Int,
        direction: String?
    ): List<TractionShortDto> {
        val schema = schemaRepository.getById(schemaId)
        val current = if (schema.type === SchemaType.DC) {
            LocomotiveCurrent.DIRECT_CURRENT
        } else {
            LocomotiveCurrent.ALTERNATING_CURRENT
        }
        if (direction == null) {
            return tractiveCalculateRepository.findAllByActiveTrueAndTrack_Id(trackId)
                .filter { it.singleTrack.trackNumber == trackNumber && it.locomotive.current === current }
                .map { it.toShortDto() }
        } else if (direction.equals("odd", true)) {
            return tractiveCalculateRepository.findAllByActiveTrueAndTrack_Id(trackId)
                .filter { it.singleTrack.trackNumber == trackNumber && it.locomotive.current === current && it.direction }
                .map { it.toShortDto() }
        } else if (direction.equals("even", true)) {
            return tractiveCalculateRepository.findAllByActiveTrueAndTrack_Id(trackId)
                .filter { it.singleTrack.trackNumber == trackNumber && it.locomotive.current === current && !it.direction }
                .map { it.toShortDto() }
        }
        return tractiveCalculateRepository.findAllByActiveTrueAndTrack_Id(trackId)
            .filter { it.singleTrack.trackNumber == trackNumber && it.locomotive.current === current }
            .map { it.toShortDto() }
    }

    fun getAllByTrackIdAndSchemaId(trackId: Long, schemaId: Long): List<TractionCountDtoPair> {
        val track = trackRepository.getById(trackId)
        val schema = schemaRepository.getById(schemaId)
        val calculates = tractiveCalculateRepository.findAllByActiveTrueAndTrack_Id(trackId)
        val result = mutableListOf<TractionCountDtoPair>()
        for (i in 1..min(track.numberOfTracks, schema.trackCount)) {
            result.add(TractionCountDtoPair(
                odd = calculates.filter { it.singleTrack.trackNumber == i && it.direction }.map { it.toShortDto() },
                even = calculates.filter { it.singleTrack.trackNumber == i && !it.direction }.map { it.toShortDto() }
            ))
        }
        return result
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun deleteById(id: Long, trackId: Long?): PagedResult<TractionShortDto> {
        tractiveCalculateRepository.findByIdOrNull(id)?.let {
            it.active = false
            tractiveCalculateRepository.save(it)
        } ?: throw IllegalStateException("There is no entity with id = '$id'")
        return if (trackId == null) {
            tractiveCalculateRepository.findAllByActiveTrue(
                pageable = PageRequest.of(
                    0,
                    PAGEABLE_DEFAULT_SIZE,
                    Sort.by("changeTime")
                )
            )
                .map { it.toShortDto() }.toDto()
        } else {
            tractiveCalculateRepository
                .findAllByActiveTrueAndTrack_Id(
                    trackId,
                    PageRequest.of(0, PAGEABLE_DEFAULT_SIZE, Sort.by("changeTime"))
                )
                .map { it.toShortDto() }
                .toDto()
        }
    }

    fun getAllById(ids: List<Long>): List<TractionShortDto> =
        tractiveCalculateRepository.findAllById(ids).map { it.toShortDto() }

}

private fun ElectricalPosition.toCounterPosition() = Position(
    name = name,
    characteristics = characteristics.map { it.toCounterCharacteristic() }
)

private val logger = LoggerFactory.getLogger("CounterService")

private fun ElectricalCharacteristic.toCounterCharacteristic() = PositionCharacteristic(
    speed = if (speed != null) speed!! else {
        logger.debug(this.toString())
        throw IllegalStateException("Ошибка в характеристиках локомотива")
    },
    force = if (force != null) force!! else {
        logger.debug(this.toString())
        throw IllegalStateException("Ошибка в характеристиках локомотива")
    },
    motorAmperage = if (motorAmperage != null) motorAmperage!! else {
        logger.debug(this.toString())
        throw IllegalStateException("Ошибка в характеристиках локомотива")
    },
    activeAmperage = if (activeCurrentAmperage != null) activeCurrentAmperage!! else {
        logger.debug(this.toString())
        throw IllegalStateException("Ошибка в характеристиках локомотива")
    },
    fullAmperage = if (this is AlternateCharacteristic) commutateCurrentAmperage else null
)

fun AverageElement.toGraphicDto() = TractionCountGraphicDto(
    c = c,
    t = t,
    ma = abs(ma),
    s = s,
    a = abs(fullA ?: actA)
)
