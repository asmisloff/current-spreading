package ru.vniizht.currentspreading.core.dcnew

import mu.KotlinLogging
import ru.vniizht.currentspreading.core.circuit.BranchFeederInfo
import ru.vniizht.currentspreading.core.circuit.amendConsideringBranchIndex
import ru.vniizht.asuterkortes.counter.dcnew.BlockDCFactory
import ru.vniizht.currentspreading.dao.TransformerType
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.dto.*
import ru.vniizht.currentspreading.util.check
import ru.vniizht.currentspreading.util.eq
import ru.vniizht.currentspreading.util.swap
import kotlin.math.abs
import kotlin.math.pow

/**
 * Пороговое значение невязки фидерных токов при итерационном расчете
 */
private const val EPS = 0.1
private var logger = KotlinLogging.logger {}

enum class ICSolutionAttempt { FIRST, SECOND }

/* Заданное стабилизированное напряжение на сборных шинах распред. устройства.
*  В текущей реализации константа */
const val uStable = 3300.0

/**
 * Мгновенная схема на постоянном токе
 */
class InstantCircuitDC(val dto: DCSchemaFullDto? = null) {

    val blocks = mutableListOf<BlockDC>()
    val transitionalCircuits: MutableList<TransitionalCircuitDC> = mutableListOf()
    val network = mutableMapOf<Int, MutableList<NetworkResistanceRangeDC>>()
    private val factory = BlockDCFactory()

    private var attempt = ICSolutionAttempt.FIRST

    init {
        if (dto != null) {
            parseDto()
            (0..dto.trackCount).forEach { network[it] = mutableListOf() }
            parseNetwork()
        }
        blocks.sortBy { it.axisCoordinate }
    }

    fun solve() {
        if (attempt == ICSolutionAttempt.FIRST)
            transitionalCircuits.forEach { it.buildMatrices() }
        else
            transitionalCircuits.forEach { it.updateSSCardinalParameters() }

        while (!solveIteration()) {
            exchangeBranchCurrents()
        }

        if (attempt == ICSolutionAttempt.FIRST) {
            val recomputationIsNecessary = checkResultsAndUpdateSSCardinalParameters()
            if (recomputationIsNecessary) {
                attempt = ICSolutionAttempt.SECOND
                solve()
            }
        }

//        logger.info(approach.name)
        transitionalCircuits.forEach {
            it.saveAmperagesInGraphEdges()
            it.computeNodePotentials()
        }
    }

    /**
     * Сортировать блоки по возрастанию координаты, проверить корректность следования блоков.
     */
    private fun sortBlocksAndSwapIfNecessary() {
        check(blocks.isNotEmpty()) { "В схеме нет блоков" }
        blocks.sortBy { it.axisCoordinate }

        /**
         * После сортировки блоки, расположенные на одной координате, могут стоять неправильно.
         * 1. Поезда могут "выскочить" за границы схемы.
         * 2. Ветка, предназначенная для соединения с ЭЧЭ, может оказаться слева от нее в списке блоков. Тогда она
         *    попадет не в ту МПЗ.
         * Выявить такие ситуации и поменять блоки местами.
         */
        for ((i, pair) in blocks.zipWithNext().withIndex()) {
            val (left, right) = pair
            //Ветка слева от ЭЧЭ, на одной координате с ЭЧЭ, и ЭЧЭ имеет хотя бы один фидер, идущий в эту ветку.
            val branchSwapCondition = left is BlockBranchDc && right is BlockSSDC
                    && left.axisCoordinate eq right.axisCoordinate
                    && right.branchFeederInfoList.any { it.branchIndex == left.branchIndex }
            //Нагрузка слева от ЭЧЭ, на одной с ней координате и является первым блоком в схеме
            val payloadBeforeSchemaSwapCondition = left is BlockPayloadDC && right is BlockSSDC
                    && left.axisCoordinate eq right.axisCoordinate && i == 0
            //Нагрузка справа от ЭЧЭ, на одной с ней координате и является последним блоком в схеме
            val payloadAfterSchemaSwapCondition = right is BlockPayloadDC && left is BlockSSDC
                    && left.axisCoordinate eq right.axisCoordinate && i + 1 == blocks.lastIndex
            val totalSwapCondition =
                branchSwapCondition || payloadBeforeSchemaSwapCondition || payloadAfterSchemaSwapCondition
            if (totalSwapCondition) blocks.swap(i, i + 1)
        }

        check(blocks.first() is BlockSSDC && blocks.last() is BlockSSDC) {
            "Невозможно построить схему замещения, так как некоторые объекты выходят за границы заданных МПЗ. " +
                    "Возможно, присутствует ветвь, не присоединенная к основному ходу."
        }
    }

    private fun collectFeederAmperages(): List<Double> {
        val tcSolutions = transitionalCircuits.map {
            val (left, right) = it.getFeederAmperages()
            Pair(left.sum(), right.sum())
        }
        val firstStationAmps = tcSolutions.first().first
        val lastStationAmps = tcSolutions.last().second
        val restStationsAmps = tcSolutions
            .zipWithNext { leftZone, rightZone ->
                val left = leftZone.second // левые фидера: левая МПЗ, правая подстанция
                val right = rightZone.first // правые фидера: правая МПЗ, левая подстанция
                left + right
            }
        return listOf(firstStationAmps) + restStationsAmps + listOf(lastStationAmps)
    }

    private fun checkResultsAndUpdateSSCardinalParameters(): Boolean {
        val ssDtos = dto!!.mainSchema.objects.filterIsInstance<DCStationDto>().sortedBy { it.coordinate }
        val ssBlocks = blocks.filterIsInstance<BlockSSDC>()
        val feederAmps = collectFeederAmperages()
        var recomputationIsNecessary = false
        ssDtos.forEachIndexed { i, dto ->
            val cardinalParameters = ssCardinalParameters(dto, ICSolutionAttempt.SECOND)
            if (cardinalParameters.boosterIsPresent) {
                val idm = idm(dto)!!
//                logger.info("idm = $idm")
                if (feederAmps[i] > idm) {
                    recomputationIsNecessary = true
                    ssBlocks[i].vOut = cardinalParameters.emf
                    ssBlocks[i].zSS = cardinalParameters.zSS
                    //todo: убрать после перехода на BlockSSDuplexDC
                    when (i) { // Изменить кард. парам. в схемах МПЗ
                        0 -> {
                            val tc = transitionalCircuits[0]
                            (tc.blocks.first() as BlockSSDC).vOut = cardinalParameters.emf
                            (tc.blocks.first() as BlockSSDC).zSS = cardinalParameters.zSS
                        }

                        ssDtos.lastIndex -> {
                            val tc = transitionalCircuits.last()
                            (tc.blocks.last() as BlockSSDC).vOut = cardinalParameters.emf
                            (tc.blocks.last() as BlockSSDC).zSS = cardinalParameters.zSS
                        }

                        else -> {
                            val left = transitionalCircuits[i - 1].blocks.last() as BlockSSDC
                            val right = transitionalCircuits[i].blocks.first() as BlockSSDC
                            for (ss in listOf(left, right)) {
                                ss.vOut = cardinalParameters.emf
                                ss.zSS = cardinalParameters.zSS
                            }
                        }
                    }

                }
            }
        }

        return recomputationIsNecessary
    }

    private fun exchangeBranchCurrents() {
        for ((left, right) in transitionalCircuits.zipWithNext()) {
            val leftZoneIssEdge = left.ss2IssEdge
            val leftZoneHalfIssEdge = left.ss2HalfIssEdge
            val rightZoneIssEdge = right.ss1IssEdge
            val rightZoneHalfIssEdge = right.ss1HalfIssEdge

            left.I!![leftZoneIssEdge!!.index, 0] = right.ss1RightFeederAmperage
            left.I!![leftZoneHalfIssEdge!!.index, 0] = right.ss1RightFeederAmperage

            right.I!![rightZoneIssEdge!!.index, 0] = left.ss2LeftFeederAmperage
            right.I!![rightZoneHalfIssEdge!!.index, 0] = left.ss2LeftFeederAmperage
        }
    }

    private fun solveIteration(): Boolean {
        transitionalCircuits.forEach(TransitionalCircuitDC::solve)
        for (tc in transitionalCircuits) {
            if (abs(tc.ss1DeltaFA) > EPS || abs(tc.ss2DeltaFA) > EPS) return false
        }
        return true
    }

    fun build() {
        if (blocks.isEmpty()) {
            parseDto()
        } else {
            sortBlocksAndSwapIfNecessary()
        }
        transitionalCircuits.clear()
        var blockIndex = 0
        while (blockIndex < blocks.lastIndex) {
            blockIndex = buildTransitionalCircuit(blockIndex)
        }
    }

    private fun parseDto() {
        validateDto(dto!!)
        val objects = (dto.mainSchema.objects + dto.branches).sortedBy { it.coordinate }
        for (o in objects) {
            when (o) {
                is ConvertableToBlockDc -> blocks.add(factory.fromDto(o, dto.trackCount))
                else -> throw IllegalStateException("Сборка схемы с блоком ${o::class.java} в текущей реализации не поддерживается")
            }
        }
    }

    private fun buildTransitionalCircuit(startIndex: Int): Int {
        transitionalCircuits.add(TransitionalCircuitDC())
        val tc = transitionalCircuits.last()
        var index = startIndex
        var ssCnt = 0

        while (ssCnt != 2) {
            when (val b = blocks[index]) {
                is BlockSSDC -> {
                    ++ssCnt
                    (if (ssCnt == 1) b.forkRight() else b.forkLeft())
                        .mergeInto(tc)
                }

                is BlockSPDC -> b.mergeInto(tc)
                is BlockPayloadDC -> b.mergeInto(tc)
                is BlockJumperDC -> b.mergeInto(tc)
                is BlockBranchDc -> b.mergeInto(tc)
                is BlockSplitterDc -> b.mergeInto(tc)
                is BlockShortCircuitPointDc -> b.mergeInto(tc)

                else -> throw NotImplementedError("Сборка схемы с объектом типа ${b::class.java} не поддерживается.")
            } // when

            ++index

        } // while

        tc.wire(this.network)
//        tc.toDOT(true)
        tc.buildMatrices()
        return index - 1
    }

    private fun validateDto(dto: DCSchemaFullDto) {
        val mainSchemaObjects = dto.mainSchema.objects
        check(mainSchemaObjects.size >= 2 || mainSchemaObjects.filterIsInstance<DCStationDto>().size >= 2) {
            "Ошибка составления схемы. Схема должна состоять как минимум из двух подстанций."
        }
        check(mainSchemaObjects.first() is DCStationDto || mainSchemaObjects.last() is DCStationDto) {
            "Ошибка составления схемы. Схема должна начинаться и заканчиваться подстанцией."
        }

        val defaultCoordinate = dto.mainSchema.objects.first().coordinate
        for (b in dto.branches) {
            val hostObjects = dto.mainSchema.objects
                .filter {
                    check(it is ConvertableToBlockDc) {
                        "Объект ${it.name} (${it.coordinate}) не предназначен для использования в схемах постоянного тока."
                    }
                    it.isConnectedToBranch(b.branchIndex)
                }
            when (hostObjects.size) {
                0 -> b.checkAndAmendAxisCoordinate(null, defaultCoordinate)
                1 -> b.checkAndAmendAxisCoordinate(hostObjects.first().coordinate, defaultCoordinate)
                else -> throw IllegalStateException(
                    "Ветвь №${b.branchIndex} подключена к нескольким устройствам основного хода. Ветвь может иметь не более одного подключения."
                )
            }
        }
    }

    private fun parseNetwork() {
        val overallNetwork = mutableListOf<List<DCNetworkDto>>()
        overallNetwork.addAll(dto!!.mainSchema.network)
        for (branch in dto.branches) {
            overallNetwork.addAll(branch.network)
        }
        val networksByBranchIndex = overallNetwork.groupBy { dcNetworkList ->
            dcNetworkList
                .check("Для одного или нескольких путей не задана контактная сеть") { it.isNotEmpty() }
                .first().branchIndex
        }
        for ((branchIndex, branchNetworks) in networksByBranchIndex) {
            for ((trackIndex, networks) in branchNetworks.withIndex()) {
                val trackNumber = (trackIndex + 1).amendConsideringBranchIndex(branchIndex)
                val cnRanges = network.getOrPut(trackNumber) { mutableListOf() }
                val railRanges = network.getOrPut(0.amendConsideringBranchIndex(branchIndex)) { mutableListOf() }
                for (dto in networks) {
                    cnRanges.add(
                        NetworkResistanceRangeDC(
                            dto.endSection, dto.network.wiresResistance/* + (dto.network.railsResistance ?: 0.0)*/
                        )
                    )
                    railRanges
                        .find { it.xMax eq dto.endSection }
                        ?: railRanges.add(
                            NetworkResistanceRangeDC(dto.endSection, dto.network.railsResistance ?: 0.0)
                        )
                }
            }
        }
        network.forEach { (_, ranges) -> ranges.sortBy { it.xMax } }
    }

    fun wire() {
        for (tc in this.transitionalCircuits) {
            tc.wire(this.network)
        }
    }

    fun removePayloads() {
        blocks.removeIf { it is BlockPayloadDC }
        factory.payloadCnt = 0
    }

    fun addPayload(trackNumber: Int, amperage: Double, coordinate: Double, routeIndex: Int = 0) {
        blocks.add(factory.makePayload(trackNumber, amperage, coordinate, routeIndex))
    }

    fun updateBlocks(dtos: List<BlockDCDto>) {
        var i = 0
        factory.payloadCnt = 0
        for (dto in dtos) {
            when {
                dto.blockLabel.contains("PL") -> {
                    dto as BlockPayloadDCDto
                    blocks.add(factory.makePayload(dto.trackNumber, -dto.amperage, dto.axisCoordinate))
                }
                dto is BlockShortCircuitPointDcDto -> {
                    blocks.add(factory.makeShortCircuitPoint(dto))
                }
                else -> {
                    blocks[i++].updateState(dto)
                }
            }
        }
    }

    fun getReport(solutionDataList: MutableList<InstantCircuitDCSolutionDataEntry>? = null): List<InstantCircuitDCSolutionDataEntry> {
        val solData = solutionDataList ?: mutableListOf()

        for (tc in transitionalCircuits) {
            solData.addAll(tc.blocks.map { (it as BlockDC).solution() })
        }

        /* Объединить смежные подстанции
        * todo: убрать после замены подстанции на Duplex*/
        var i = 0
        while (i < solData.size - 1) {
            val curr = solData[i]
            val next = solData[i + 1]
            if (curr.objectName.contains("ЭЧЭ")) {
                if (next.objectName.contains("ЭЧЭ") && curr.coordinate == next.coordinate) { // смежные подстанции
                    curr.amperages.removeLast()
                    curr.amperages.addAll(next.amperages)
                    solData.removeAt(i + 1)
                } else {
                    val tmp = MutableList((blocks.first() as BlockSSDC).leftFeederEdges.size) { 0.0 }
                    tmp.addAll(curr.amperages)
                    curr.amperages.clear()
                    curr.amperages.addAll(tmp)
                }
            }
            ++i
        }
        val suckingAmp = solData.last().amperages.removeLast()
        solData.last().amperages.addAll(List(solData.last().amperages.size) { 0.0 })
        solData.last().amperages.add(suckingAmp)
        solData.sortBy { it.coordinate }

        return solData
    }

}

fun List<DCFiderDto>.toBranchFeederInfoList(hostBlockLabel: String): List<BranchFeederInfo<Double>> {
    var trackCnt = 0
    var currentBranchIndex = 0
    return this.map { f ->
        if (f.branchIndex != currentBranchIndex) {
            currentBranchIndex = f.branchIndex
            trackCnt = 0
        }
        val r = when {
            f.length eq 0 -> ZERO
            f.type == null -> throw IllegalStateException(
                "Не заданы электрические параметры фидера ответвления для устройства $hostBlockLabel"
            )
            else -> f.type!!.wiresResistance * f.length
        }
        BranchFeederInfo(
            branchIndex = f.branchIndex,
            trackNumber = ++trackCnt,
            feederResistance = r,
            connectionPoint = f.coordinate
        )
    }
}

/**
 * Пара значений (координата, удельное сопротивление) линии.
 * @param xMax
 * координата на участке пути
 * @param r
 * удельное сопротивление
 *
 * r(x) = xMax если x <= xMax, иначе 0
 */
data class NetworkResistanceRangeDC(val xMax: Double, val r: Double)

fun lambdaP(ssDto: DCStationDto): Double? {
    val ud0 = ssDto.stationParameters!!.vduUhh
    val udd0 = ssDto.stationParameters!!.vduVoltage
    return when {
        ud0 == null || udd0 == null || ud0 == 0 || udd0 == 0 -> null
        else -> (ud0 + udd0).toDouble() / udd0.toDouble()
    }
}

fun ud0(ssDto: DCStationDto): Double {
    val stt = ssDto.stationParameters!!.stt // DTO преобразующего трансформатора
        ?: throw RuntimeException("Не задан преобразующий трансформатор")
    return when {
        stt.name.contains('*') -> 3600.0
        else -> 3700.0
    }
}

/**
 * Расчет Z_тп при работающем ВДУ
 */
fun zSSBooster(ssDto: DCStationDto): Double? {
    val skz = ssDto.stationParameters!!.skz!!
    val lambdaP = lambdaP(ssDto) ?: return null

    /* понижающий трансформатор */
    val spt = ssDto.stationParameters!!.spt
    val ptTerm =
        if (spt == null)
            (lambdaP.pow(2.0) / skz)
        else {
            val uKpt = spt.voltageShortCircuit
            val sptNom = spt.power
            val npt = ssDto.stationParameters!!.sptCount ?: 1
            (1.0 / skz + (10.0 * uKpt) / (npt * sptNom)) * lambdaP.pow(2.0)
        }

    /* преобразующий трансформатор */
    val stt = ssDto.stationParameters!!.stt
        ?: throw RuntimeException("Не задан преобразующий трансформатор") // DTO преобразующего трансформатора
    val uKtt = stt.voltageShortCircuit
    val ntt = ssDto.stationParameters!!.sttCount ?: 1
    val sttNom = stt.power
    val ttTerm = (10.0 * uKtt) / (ntt * sttNom)

    /* ВДУ */
    val uKktd = 5.0
    val stdNom = 125.0
    val ntd = 1
    val boosterTerm = (0.01 * uKktd) * (lambdaP - 1).pow(2.0) / (ntd * stdNom)

    val kR = if (stt.name.endsWith("*")) 3.4 else 6.6 // коэффициенты Кс, расчет, как в Кортэсе

    return kR * (ptTerm + ttTerm + boosterTerm)
}


/**
 * Расчет Z_тп в отсутствие ВДУ
 */
fun zSSBasic(ssDto: DCStationDto): Double {
    /* Базовый вариант: Z_тп рассчитано клиентским приложением или явно введено пользователем */
    val directRo = ssDto.stationParameters?.directRo
    if (directRo != null) return directRo

    /* Клиент не прислал Z_тп - нужно его рассчитать */
    val skz = ssDto.stationParameters!!.skz!!

    val spt = ssDto.stationParameters!!.spt
    val ukSpt = spt?.voltageShortCircuit ?: 0.0
    val sNomSpt = spt?.power ?: 0
    val sptQty = ssDto.stationParameters!!.sptCount ?: 0

    val sptRes = ssDto.stationParameters!!.sptReserve // DTO резервного понижающего трансформатора
    val ukSptRes = sptRes?.voltageShortCircuit ?: 0.0
    val sNomSptRes = sptRes?.power ?: 0
    val sptResQty = ssDto.stationParameters!!.sptReserveCount ?: 0

    val stt = ssDto.stationParameters!!.stt  // DTO преобразующего трансформатора
        ?: throw RuntimeException("Не задан преобразующий трансформатор")
    val ukStt = stt.voltageShortCircuit
    val sNomStt = stt.power
    val sttQty = ssDto.stationParameters!!.sttCount ?: 0

    val sttRes = ssDto.stationParameters!!.sttReserve // DTO резервного понижающего трансформатора
    val ukSttRes = sttRes?.voltageShortCircuit ?: 0.0
    val sNomSttRes = sttRes?.power ?: 0
    val sttResQty = ssDto.stationParameters!!.sttReserveCount ?: 0

    val kcStt = when (val tType = TransformerType.infer(stt.name)) { // коэффициенты Кс, расчет, как в Кортэсе
        TransformerType.TWELVE_PHASE -> 3.4
        TransformerType.DC, TransformerType.THREE_PHASE -> 6.6
        else -> throw IllegalStateException("Неверный тип трансформатора: ${tType.strRepr} (${stt.name})")
    }

    val ttTerm =
        10 * ((ukStt * sttQty + ukSttRes * sttResQty) / (sNomStt * sttQty + sNomSttRes * sttResQty)) / (sttQty + sttResQty)

    val ptTerm =
        if (spt != null || sptRes != null)
            10 * ((ukSpt * sptQty + ukSptRes * sptResQty) / (sNomSpt * sptQty + sNomSptRes * sptResQty)) / (sptQty + sptResQty)
        else
            0.0

    return kcStt * (1.0 / skz + ttTerm + ptTerm)
}

fun ssCardinalParameters(ssDto: DCStationDto, attempt: ICSolutionAttempt): SSCardinalParameters {
    val vBoosterShortCircuit = ssDto.stationParameters?.vduUhh
    val vBooster = ssDto.stationParameters?.vduVoltage
    return when {
        (vBoosterShortCircuit == null && vBooster == null) || (vBoosterShortCircuit == 0 && vBooster == 0) -> // ВДУ отсутствует
            SSCardinalParameters(
                emf = ssDto.stationParameters?.directUhh?.toDouble() ?: 3600.0,
                zSS = zSSBasic(ssDto),
                boosterIsPresent = false
            )

        vBooster == null || vBoosterShortCircuit == null || vBooster == 0 || vBoosterShortCircuit == 0 -> // ВДУ есть, но заданы не все параметры
            throw IllegalStateException("Для подстанции ${ssDto.name} задан только один из параметров ВДУ. Необходимо задать оба.")

        else -> // ВДУ есть, параметры заданы корректно
            when (attempt) {
                ICSolutionAttempt.FIRST -> SSCardinalParameters(3300.0, 0.01, true)
                ICSolutionAttempt.SECOND -> SSCardinalParameters(
                    ud0(ssDto) * lambdaP(ssDto)!!,
                    zSSBooster(ssDto)!!,
                    true
                )
            }

    }
}

fun idm(dto: DCStationDto): Double? {
    val lambdaP = lambdaP(dto)
    val zSS = zSSBooster(dto)
    return when (lambdaP) {
        null -> null
        else -> (ud0(dto) * lambdaP - uStable) / zSS!!
    }
}

fun ConvertableToBlockDc.isConnectedToBranch(index: Int) = when (this) {
    is BranchPointDto -> connectedBranchIndex == index
    is ConnectorDto -> false
    is DCBranchDto -> false
    is DCSectionDto -> branchFiders.any { it.branchIndex == index }
    is DCStationDto -> branchFiders.any { it.branchIndex == index }
}

fun ConvertableToBlockDc.connectionPointsForBranch(index: Int): List<Double> = when (this) {
    is BranchPointDto -> wiringLayout.map { it.secondConnectionPoint ?: 0.0 }
    is ConnectorDto -> emptyList()
    is DCBranchDto -> emptyList()
    is DCSectionDto -> branchFiders.filter { it.branchIndex == index }.map { it.coordinate }
    is DCStationDto -> branchFiders.filter { it.branchIndex == index }.map { it.coordinate }
}

fun BranchDto.checkAndAmendAxisCoordinate(hostObjectCoordinate: Double?, defaultCoordinate: Double) {
    if (hostObjectCoordinate == null || this.coordinate != hostObjectCoordinate) {
        this.coordinate = defaultCoordinate
    }
}