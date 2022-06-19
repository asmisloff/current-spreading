package ru.vniizht.currentspreading.core.acnew

import ru.vniizht.currentspreading.core.dcnew.checkAndAmendAxisCoordinate
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.*
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.schedule.TrainPosition
import ru.vniizht.currentspreading.core.schedule.toOrderedList
import ru.vniizht.currentspreading.dto.*
import ru.vniizht.currentspreading.util.eq
import ru.vniizht.currentspreading.util.swap

class InstantCircuitAC(val network: List<NetworkResistanceRangeAC>) {

    val blocks = mutableListOf<BlockAC>()
    val transitionalCircuits = mutableListOf<TransitionalCircuitAC>()
    private val factory = BlockACFactory()

    companion object {

        private fun fromAcNetworks(network: List<ACNetworkDto>): InstantCircuitAC {
            val networkResistanceRanges = network
                .map { NetworkResistanceRangeAC(it.endSection, it.toMutualResistances()) }
                .toOrderedList(Comparator.comparingDouble { it.xMax })
                .merge()
            return InstantCircuitAC(networkResistanceRanges)
        }

        fun fromDto(schema: ElectricalSchemaDto): InstantCircuitAC {
            val factory = BlockACFactory()
            val mainSchemaTrackQty = schema.trackCount

            val defaultCoordinate = schema.mainSchema.objects.first().coordinate
            for (b in schema.branches) { // Если есть неподключенные ветви, вывести их ось на ось первого объекта
                val hostObjects = schema.mainSchema.objects
                    .filter {
                        check(it is ConvertableToBlockAc) {
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

            val overallNetwork = mutableListOf<ACNetworkDto>()
            val (ic, supplyQty) = when (schema) {
                is ACSchemaFullDto -> {
                    overallNetwork.addAll(schema.mainSchema.network)
                    for (branch in schema.branches) {
                        overallNetwork.addAll(branch.network)
                    }
                    Pair(fromAcNetworks(overallNetwork), null)
                }
                is AcdSchemaFullDto -> {
                    overallNetwork.addAll(schema.mainSchema.network)
                    for (branch in schema.branches) {
                        overallNetwork.addAll(branch.network)
                    }
                    Pair(fromAcNetworks(overallNetwork), mainSchemaTrackQty)
                }
                else -> throw IllegalArgumentException("Неверный тип схемы (${schema::class}). Допускаются: ACSchemaFullDto и ACDSchemaFullDto")
            }
            (schema.mainSchema.objects + schema.branches).forEach { o ->
                val trackQty = when (o) {
                    is BranchDto -> o.trackQty
                    else -> mainSchemaTrackQty
                }
                when (o) {
                    is ConvertableToBlockAc -> ic.blocks.add(factory.fromDto(o, trackQty, supplyQty))
                    else -> throw IllegalStateException("Сборка схемы с блоком ${o::class.java} в текущей реализации не поддерживается")
                }
            }

            ic.sortBlocksAndSwapIfNecessary()
            return ic
        }

    }

    /**
     * Добавить нагрузку в схему
     * @param pos нагрузки
     * @param trackQty количество путей в схеме
     * @param supplyQty количество питающих линий для схемы Acd или null для Ac
     */
    fun addTrainPosition(pos: TrainPosition, trackQty: Int, supplyQty: Int?) {
        blocks.add(
            factory.makePayload(
                trackNumber = pos.trackNumber,
                trackQty = trackQty,
                supplyQty = supplyQty,
                amperage = pos.acCsa(),
                coordinate = pos.coord,
                routeIndex = pos.routeIndex
            )
        )
    }

    fun removePayloads() {
        blocks.removeIf { it is BlockPayloadAC }
    }

    fun build() {
        sortBlocksAndSwapIfNecessary()
        transitionalCircuits.clear()
        transitionalCircuits.add(TransitionalCircuitAC(network))
        for ((iBlock, block) in blocks.withIndex()) {
            val tc = transitionalCircuits.last()
            when (block) {
                is IBlockSSAcDuplex -> {
                    when {
                        tc.blocks.isEmpty() -> block.ssA.mergeInto(tc)
                        else -> {
                            block.ssB.mergeInto(tc)
                            tc.wire()
                            tc.build()
                            if (iBlock != blocks.lastIndex) {
                                transitionalCircuits.add(TransitionalCircuitAC(network))
                                block.ssA.mergeInto(transitionalCircuits.last())
                            }
                        }
                    }
                }

                else -> block.mergeInto(tc)
            }
        }
    }

    /**
     * Сортировать блоки по возрастанию координаты, проверить корректность следования блоков и принять меры в случае необходимости.
     */
    private fun sortBlocksAndSwapIfNecessary() {
        /* TODO: 17.05.2022 Это надо переписать
            1. Можно положиться на устойчивость сортировки, и в этой связи:
                - не проверять положение веток
                - не проверять поезда на "выскакивание" влево
            2. Поездов, "выскочивших" вправо, может быть больше одного. Сейчас этот случай не обработан и вызовет ошибку.
        */
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
            val branchSwapCondition = left is BlockBranchAc && right is IBlockSSAcDuplex
                    && left.axisCoordinate eq right.axisCoordinate
                    && right.ssA.branchFeederInfoList.any { it.branchIndex == left.branchIndex }
            //Нагрузка слева от ЭЧЭ, на одной с ней координате и является первым блоком в схеме
            val payloadBeforeSchemaSwapCondition = left is BlockPayloadAC && right is IBlockSSAcDuplex
                    && left.axisCoordinate eq right.axisCoordinate && i == 0
            //Нагрузка справа от ЭЧЭ, на одной с ней координате и является последним блоком в схеме
            val payloadAfterSchemaSwapCondition = right is BlockPayloadAC && left is IBlockSSAcDuplex
                    && left.axisCoordinate eq right.axisCoordinate && i + 1 == blocks.lastIndex
            val totalSwapCondition =
                branchSwapCondition || payloadBeforeSchemaSwapCondition || payloadAfterSchemaSwapCondition
            if (totalSwapCondition) blocks.swap(i, i + 1)
        }

        check(blocks.first() is IBlockSSAcDuplex && blocks.last() is IBlockSSAcDuplex) { "Схема должна начинаться и заканчиваться подстанцией." }
    }

    fun solve(): Int {
        var cnt = 0
        while (true) {
            var stopIterations = true
            for (tc in transitionalCircuits) {
                val payloadCriteria = !tc.adjustPayloadsPhases(PAYLOAD_VOLTAGE_TOLERANCE)
                tc.ssLeft().duplex!!.exchangeShouldersAmperages()
                tc.ssRight().duplex!!.exchangeShouldersAmperages()
                tc.updateEmfVector()
                tc.solve()
                val leftAmpCriteria =
                    if (tc.ssLeft().prevFeedersAmp == null)
                        false
                    else {
                        val di = (tc.ssLeft().totalFeederAmp()!! - tc.ssLeft().prevFeedersAmp!!).abs()
                        di <= FEEDER_AMP_TOLERANCE
                    }
                val rightAmpCriteria =
                    if (tc.ssRight().prevFeedersAmp == null)
                        false
                    else {
                        val di = (tc.ssRight().totalFeederAmp()!! - tc.ssRight().prevFeedersAmp!!).abs()
                        di <= FEEDER_AMP_TOLERANCE
                    }
                stopIterations = stopIterations && payloadCriteria && leftAmpCriteria && rightAmpCriteria
            }
            ++cnt
            if (stopIterations) break
            if (cnt > 10) break
        }

        return cnt
    }

    fun updateBlocks(dtos: MutableList<BlockACDto>) {
        var i = 0
        factory.payloadCnt = 0
        for (dto in dtos) {
            if (dto is BlockPayloadACDto) {
                blocks.add(
                    factory.makePayload(
                        dto.trackNumber,
                        dto.trackQty,
                        dto.supplyQty,
                        dto.amperage.negate(),
                        dto.axisCoordinate
                    )
                )
            } else {
                blocks[i++].updateState(dto)
            }
        }
    }

    fun getSolution(solution: MutableList<InstantCircuitSolutionDataEntry>? = null): List<InstantCircuitSolutionDataEntry> {
        val sol = solution ?: mutableListOf()
        for (block in blocks) {
            sol.add(block.solution())
        }
        return sol
    }

    fun saveCompactSolution(chunk: CompactSolutionAcChunk) {
        saveCompactSsSolution(chunk.ssChunk)
        saveCompactPayloadSolution(chunk.payloadChunk)
        saveCompactAtSolution(chunk.atChunk)
        savePowerLoss(chunk)
    }

    fun solutionReport(): String {
        return blocks.joinToString("\n\n") { it.solutionReport() }
    }

    /** Для тестов */
    fun solveOnce() {
        blocks.filterIsInstance<IBlockSSAcDuplex>().forEach { it.exchangeShouldersAmperages() }
        for (tc in transitionalCircuits) {
            tc.updateEmfVector()
            tc.solve()
        }
        blocks.filterIsInstance<BlockSSAcDuplex>().forEach { it.trace() }
    }

    /** Для тестов */
    fun solveWithoutPayloadRotation() {
        val sss = blocks.filterIsInstance<BlockSSAcDuplex>()
        while (true) {
            var stopIterations = true
            sss.forEach { it.exchangeShouldersAmperages() }
            for (tc in transitionalCircuits) {
                tc.updateEmfVector()
                tc.solve()
                val leftAmpCriteria =
                    if (tc.ssLeft().prevFeedersAmp == null)
                        false
                    else {
                        val di = (tc.ssLeft().totalFeederAmp()!! - tc.ssLeft().prevFeedersAmp!!).abs()
                        di <= FEEDER_AMP_TOLERANCE
                    }
                val rightAmpCriteria =
                    if (tc.ssRight().prevFeedersAmp == null)
                        false
                    else {
                        val di = (tc.ssRight().totalFeederAmp()!! - tc.ssRight().prevFeedersAmp!!).abs()
                        di <= FEEDER_AMP_TOLERANCE
                    }
                stopIterations = stopIterations && leftAmpCriteria && rightAmpCriteria
            }
            blocks.filterIsInstance<BlockSSAcDuplex>().forEach { it.trace() }
            if (stopIterations) break
        }
    }

    private fun saveCompactSsSolution(chunk: SsAcSolutionChunk) {
        var cnt = 0
        for (b in blocks) {
            if (b is IBlockSSAcDuplex) {
                chunk[cnt++].add(b.compactSolution())
            }
        }
    }

    private fun saveCompactPayloadSolution(chunk: PayloadSolutionChunk) {
        for ((iZone, tc) in transitionalCircuits.withIndex()) {
            for (b in tc.blocks) {
                if (b is BlockPayloadAC) {
                    chunk[iZone].add(b.compactSolution())
                }
            }
        }
    }

    private fun saveCompactAtSolution(chunk: AtAcdSolutionChunk) {
        var cnt = 0
        for (b in blocks) {
            if (b is BlockSpaAcd || b is BlockAtpAcd) {
                chunk.addSolution(cnt++, b.compactSolution() as CompactAtAcdSolution)
            }
        }
    }

    private fun savePowerLoss(chunk: CompactSolutionAcChunk) {
        for (tc in transitionalCircuits) {
            tc.saveCnPowerLoss(chunk.powerLoss)
        }
    }

    /** Пространственные границы электрической схемы */
    fun spatialBoundaries(): Pair<Double, Double> {
        require(blocks.isNotEmpty())
        blocks.sortBy { it.axisCoordinate }
        return Pair(blocks.first().axisCoordinate, blocks.last().axisCoordinate)
    }

}

fun ConvertableToBlockAc.isConnectedToBranch(index: Int) = when (this) {
    is BranchPointDto -> connectedBranchIndex == index
    is ConnectorDto -> false
    is ACBranchDto -> false
    is ACSectionDto -> branchFiders.any { it.branchIndex == index }
    is ACStationDto -> branchFiders.any { it.branchIndex == index }
    is ACDSectionDto -> branchFeeders.any { it.branchIndex == index }
    is ACDSectionWithAtpDto -> branchFeeders.any { it.branchIndex == index }
    is ACDStationDto -> branchFeeders.any { it.branchIndex == index }
    is AcdAtpDto -> false
    is AcdBranchPointDto -> connectedBranchIndex == index
}

fun ACBranchDto.checkAndAmendAxisCoordinate(hostObjectCoordinate: Double?, defaultCoordinate: Double) {
    if (hostObjectCoordinate == null || this.coordinate != hostObjectCoordinate) {
        this.coordinate = defaultCoordinate
    }
}