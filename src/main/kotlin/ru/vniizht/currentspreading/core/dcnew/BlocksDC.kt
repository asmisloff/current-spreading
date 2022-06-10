package ru.vniizht.currentspreading.core.dcnew

import org.jgrapht.Graphs
import org.jgrapht.graph.DirectedWeightedMultigraph
import ru.vniizht.currentspreading.core.acnew.IBlockSSAcDuplex
import ru.vniizht.asuterkortes.counter.circuit.*
import ru.vniizht.asuterkortes.counter.dcnew.TransitionalCircuitDC
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.circuit.Circuit
import ru.vniizht.currentspreading.core.schedule.OrderedList
import ru.vniizht.currentspreading.dto.Connection
import ru.vniizht.currentspreading.dto.DCNetworkDto
import ru.vniizht.currentspreading.dto.DCStationDto

const val INF = 9e7
const val ZERO = 1e-7 // Использование 0.0 плохо влияет на устойчивость решений СЛАУ

/**
 * Базовый класс для блоков схемы МПЗ на постоянном токе
 */
abstract class BlockDC(override var axisCoordinate: Double, var blockLabel: String, override val branchIndex: Int = 0) :
    Block<Double, CircuitEdgeDC, CircuitNodeDC>(CircuitEdgeDC::class.java, CircuitNodeDC::class.java) {

    protected lateinit var tc: TransitionalCircuitDC

    override fun toString(): String {
        return "$blockLabel($axisCoordinate)"
    }

    abstract fun updateState(dto: BlockDCDto)

    abstract fun toDto(): BlockDCDto

    abstract fun solution(): InstantCircuitDCSolutionDataEntry

    override fun mergeInto(circuit: Circuit<Double, CircuitEdgeDC, CircuitNodeDC>) {
        super.mergeInto(circuit)
        tc = circuit as TransitionalCircuitDC
    }

}

/** Кардинальные параметры блока подстанции */
data class SSCardinalParameters(val emf: Double, val zSS: Double, val boosterIsPresent: Boolean)

/**
 * Блок трансформаторной подстанции
 */
class BlockSSDC(
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zSS: Double,
    var xCompXInv: Double,
    vOut: Double,
    blockLabel: String,
    branchIndex: Int = 0
) : BlockDC(axisCoordinate, blockLabel, branchIndex) {

    val gnd: CircuitNodeDC // Нулевой узел (между zssEdge и xCompXInvEdge)
    val cnOut: CircuitNodeDC // Выходная положительная клемма подстанции
    val railOut: CircuitNodeDC // Выходная рельсовая клемма подстанции
    val zssEdge: CircuitEdgeDC
    val xCompXInvEdge: CircuitEdgeDC
    val issEdge: CircuitEdgeDC
    val halfIssEdge: CircuitEdgeDC
    val zFLeft: MutableList<Double> = mutableListOf()
    val zFRight: MutableList<Double> = mutableListOf()
    val leftFeederEdges = mutableListOf<CircuitEdgeDC>()
    val rightFeederEdges = mutableListOf<CircuitEdgeDC>()
    var dto: DCStationDto? = null

    var branchFeederEdges: List<CircuitEdgeDC> = emptyList()
    var branchFeederInfoList: List<BranchFeederInfo<Double>> = emptyList()

    var vOut = vOut
        set(value) {
            field = value
            zssEdge.emf = value
        }

    var zSS = zSS
        set(value) {
            field = value
            zssEdge.value = value
        }

    init {
        railOut = addNode(suckingCoordinate, 0.amendConsideringBranchIndex(), "${blockLabel}:0", index = 0)
        gnd = addNode(axisCoordinate, -1, "${blockLabel}:GND", index = 1)
        cnOut = addNode(axisCoordinate, -1, "${blockLabel}:cnOut", index = 2)
        zssEdge = addEdge(1, 2, CircuitEdgeDC("${blockLabel}_Zss", zSS, emf = vOut))
        halfIssEdge = addEdge(1, 2, CircuitEdgeDC("${blockLabel}_1/2Iss", INF))
        issEdge = addEdge(0, 1, CircuitEdgeDC("${blockLabel}_Iss", INF))
        xCompXInvEdge = addEdge(0, 1, CircuitEdgeDC("${blockLabel}_Xkxi", xCompXInv))
    }

    fun addLeftFeeder(coordinates: DoubleArray, zF: DoubleArray) {
        // Валидация и корректировка точки присоединения фидера
        for ((i, c) in coordinates.withIndex()) {
            if (c == axisCoordinate) coordinates[i] =
                c - 1e-6 // Сместить на 1 мм влево. Во избежание неверных связей при разводке схемы.
        }
        addFeeder(coordinates, zF, "FL", leftFeederEdges)
        zFLeft.clear()
        zFLeft.addAll(zF.toList())
    }

    fun addRightFeeder(coordinates: DoubleArray, zF: DoubleArray) {
        // Валидация и корректировка точки присоединения фидера
        for ((i, c) in coordinates.withIndex()) {
            if (c == axisCoordinate) coordinates[i] =
                c + 1e-6 // Сместить на 1 мм вправо. Во избежание неверных связей при разводке схемы.
        }
        addFeeder(coordinates, zF, "FR", rightFeederEdges)
        zFRight.clear()
        zFRight.addAll(zF.toList())
    }

    fun addBranchFeeders(branchFeederInfoList: List<BranchFeederInfo<Double>>) {
        this.branchFeederInfoList = branchFeederInfoList
        val branchIndicesProcessed = mutableSetOf<Int>()
        branchFeederEdges = branchFeederInfoList
            .sortedBy { it.branchIndex }
            .map { info ->
                check(info.branchIndex > 0) { "Индекс ответвления должен быть больше 0. 0 - индекс основного хода." }
                check(info.branchIndex <= 200_000) {
                    "Индекс ответвления должен быть меньше 200_000. Задано значение ${info.branchIndex}"
                }

                if (branchIndicesProcessed.add(info.branchIndex)) {
                    val node = addNode(
                        coordinate = info.connectionPoint,
                        trackNumber = 0.amendConsideringBranchIndex(info.branchIndex),
                        label = "$blockLabel:ZRail_br${info.branchIndex}"
                    )
                    addEdge(
                        source = railOut,
                        target = node,
                        edge = CircuitEdgeDC("$blockLabel:ZRail_br${info.branchIndex}", info.feederResistance)
                    )
                }

                val node = addNode(
                    coordinate = info.connectionPoint,
                    trackNumber = info.trackNumber.amendConsideringBranchIndex(info.branchIndex),
                    label = "$blockLabel:Zf_br${info.branchIndex}"
                )
                addEdge(
                    source = cnOut,
                    target = node,
                    edge = CircuitEdgeDC("$blockLabel:Zf_br${info.branchIndex}", info.feederResistance)
                )
            }
    }

    private fun addFeeder(coordinates: DoubleArray, zF: DoubleArray, label: String, list: MutableList<CircuitEdgeDC>) {
        for (i in 1..coordinates.size) {
            val node = addNode(
                coordinate = coordinates[i - 1],
                trackNumber = i.amendConsideringBranchIndex(),
                label = "${blockLabel}_${label}:$i"
            )
            val edge = addEdge(
                2,
                node.index,
                CircuitEdgeDC("${blockLabel}_Z${label.lowercase()}_$i", zF[i - 1])
            )

            list.add(edge)
        }
    }

    override fun updateState(dto: BlockDCDto) {
        dto as? BlockSSDCDto ?: throw IllegalArgumentException(
            """
                |BlockSSDC::updateState -- аргумент не приводится к типу BlockSSDCDto
                |Фактический тип: ${dto::class}
            """.trimMargin()
        )
        this.description = dto.description
        this.zssEdge.value = if (dto.mainSwitchState) this.zSS else INF
        this.leftFeederEdges.forEachIndexed { i, it ->
            it.value = if (dto.leftSwitchesState[i]) zFLeft[i] else INF
        }
        this.rightFeederEdges.forEachIndexed { i, it ->
            it.value = if (dto.rightSwitchesState[i]) zFRight[i] else INF
        }
        for (i in dto.branchSwitchesState.indices) {
            val dtoState = dto.branchSwitchesState[i]
            val thisState = branchFeederInfoList[i]
            check(dtoState.branchIndex == thisState.branchIndex) {
                "Клиентское приложение передало данные в некорректном формате: у фидеров ответвлений не согласованы номера ветвей."
            }
            check(dtoState.trackNumber == thisState.trackNumber) {
                "Клиентское приложение передало данные в некорректном формате: у фидеров ответвлений не согласованы номера путей."
            }
            thisState.feederResistance = dtoState.feederResistance
            thisState.switchedOn = dtoState.switchedOn
            branchFeederEdges[i].value = if (thisState.switchedOn) thisState.feederResistance else INF
        }
    }

    fun forkLeft(): BlockSSDC {
        return copyMainBlock().also { ss ->
            val coordinates = this.leftFeederEdges.map { it.getTargetNode().coordinate }.toDoubleArray()
            for (c in coordinates) {
                check(c <= axisCoordinate) {
                    "Точка присоединения левого фидера не может находиться правее оси подстанции. " +
                            "Точка присоединения фидера: $c, подстанция: $description ($axisCoordinate)"
                }
            }
            ss.addLeftFeeder(
                coordinates,
                this.leftFeederEdges.map { it.value }.toDoubleArray()
            )
        }
    }

    fun forkRight(): BlockSSDC {
        val coordinates = this.rightFeederEdges.map { it.getTargetNode().coordinate }.toDoubleArray()
        for (c in coordinates) {
            check(c >= axisCoordinate) {
                "Точка присоединения правого фидера не может находиться левее оси подстанции. " +
                        "Точка присоединения фидера: $c, подстанция: $description ($axisCoordinate)"
            }
        }
        return copyMainBlock().also { ss ->
            ss.addRightFeeder(
                coordinates,
                this.rightFeederEdges.map { it.value }.toDoubleArray()
            )
            ss.addBranchFeeders(branchFeederInfoList)
        }
    }

    private fun copyMainBlock(): BlockSSDC =
        BlockSSDC(
            this.axisCoordinate,
            this.railOut.coordinate,
            this.zssEdge.value,
            this.xCompXInv,
            this.vOut,
            this.blockLabel
        ).also { it.description = this.description }

    override fun toDto(): BlockDCDto = BlockSSDCDto(
        axisCoordinate = axisCoordinate,
        blockLabel = this.blockLabel,
        vOut = vOut,
        description = this.description,
        leftFeederConnectionPoints = this.leftFeederEdges.map { it.getTargetNode().coordinate },
        leftFeederResistances = this.rightFeederEdges.map { it.value },
        rightFeederConnectionPoints = this.rightFeederEdges.map { it.getTargetNode().coordinate },
        rightFeederResistances = this.rightFeederEdges.map { it.value },
        suckingCoordinate = this.railOut.coordinate,
        xCompXInv = this.xCompXInv,
        zSS = this.zSS,
        mainSwitchState = this.zssEdge.value != INF,
        leftSwitchesState = this.leftFeederEdges.map { it.value != INF },
        rightSwitchesState = this.rightFeederEdges.map { it.value != INF },
        branchSwitchesState = branchFeederInfoList,
        branchIndex = branchIndex
    )

    override fun solution(): InstantCircuitDCSolutionDataEntry {
        return InstantCircuitDCSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel.replace("SS", "ЭЧЭ "),
            description = description,
            amperages = (leftFeederEdges + rightFeederEdges + branchFeederEdges + xCompXInvEdge)
                .mapTo(mutableListOf()) { it.amp ?: 0.0 },
            voltages = mutableListOf((cnOut.p ?: 0.0) - (railOut.p ?: 0.0))
        )
    }

}

/**
 * Блок нагрузки
 */
class BlockPayloadDC(
    val trackNumber: Int,
    val amperage: Double,
    axisCoordinate: Double,
    blockLabel: String,
    val routeIndex: Int = 0,
    branchIndex: Int = 0
) :
    BlockDC(axisCoordinate, blockLabel, branchIndex) {

    val iplEdge: CircuitEdgeDC
    val cnNode: CircuitNodeDC
    val gndNode: CircuitNodeDC

    init {
        gndNode = addNode(axisCoordinate, 0.amendConsideringBranchIndex(), "${blockLabel}_0", index = 0)
        cnNode = addNode(
            axisCoordinate,
            trackNumber.amendConsideringBranchIndex(),
            "${blockLabel}_${trackNumber.amendConsideringBranchIndex()}",
            index = 1
        )
        iplEdge = addEdge(1, 0, CircuitEdgeDC("${blockLabel}_Ipl", INF, csa = amperage))
    }

    override fun toString(): String {
        return super.toString() + "/track#$trackNumber"
    }

    override fun toDto() = BlockPayloadDCDto(
        axisCoordinate = axisCoordinate,
        blockLabel = this.blockLabel,
        amperage = amperage,
        trackNumber = trackNumber,
        description = this.description,
        branchIndex = branchIndex
    )

    override fun updateState(dto: BlockDCDto) = Unit

    override fun solution(): InstantCircuitDCSolutionDataEntry {
        return InstantCircuitDCSolutionDataEntry(
            coordinate = axisCoordinate,
            description = description,
            objectName = blockLabel.replace("PL", "Нагрузка "),
            amperages = mutableListOf(amperage),
            voltages = mutableListOf((cnNode.p ?: 0.0) - (gndNode.p ?: 0.0)),
            particularAttributes = mutableMapOf(
                "trackNumber" to "$trackNumber",
                "routeIndex" to "$routeIndex"
            )
        )
    }
}

/**
 * Блок ППС
 */
class BlockJumperDC(
    axisCoordinate: Double,
    blockLabel: String,
    val track1Number: Int,
    val track2Number: Int,
    branchIndex: Int = 0
) :
    BlockDC(axisCoordinate, blockLabel, branchIndex) {

    val jumperEdge: CircuitEdgeDC
    val gnd: CircuitNodeDC
    var switchState: Boolean = true

    init {
        addNode(axisCoordinate, track1Number.amendConsideringBranchIndex(), "${blockLabel}_$track1Number", index = 1)
        addNode(axisCoordinate, track2Number.amendConsideringBranchIndex(), "${blockLabel}_$track2Number", index = 2)
        gnd = addNode(axisCoordinate, 0.amendConsideringBranchIndex(), "${blockLabel}_GND", index = 0)
        jumperEdge = addEdge(1, 2, CircuitEdgeDC("${blockLabel}_jumper", ZERO))
    }

    override fun updateState(dto: BlockDCDto) {
        dto as? BlockJumperDCDto ?: throw IllegalArgumentException(
            """
                |BlockJumperDC::updateState -- аргумент не приводится к типу BlockJumperDCDto
                |Фактический тип: ${dto::class}
            """.trimMargin()
        )
        switchState = dto.switchState
        jumperEdge.value = if (switchState) ZERO else INF
    }

    override fun toDto(): BlockDCDto =
        BlockJumperDCDto(axisCoordinate, blockLabel, description, track1Number, track2Number, switchState, branchIndex)

    override fun solution(): InstantCircuitDCSolutionDataEntry {
        return InstantCircuitDCSolutionDataEntry(
            coordinate = axisCoordinate,
            description = description,
            objectName = blockLabel.replace("J", "ППС"),
            amperages = mutableListOf(jumperEdge.amp ?: 0.0),
            voltages = mutableListOf((jumperEdge.getSourceNode().p ?: 0.0) - (gnd.p ?: 0.0)),
            particularAttributes = mutableMapOf("tracks" to "${track1Number}-${track2Number}")
        )
    }
}

/**
 * Блок поста секционирования
 */
class BlockSPDC(
    axisCoordinate: Double,
    val coordinates: DoubleArray,
    private val zB: DoubleArray,
    blockLabel: String,
    branchIndex: Int = 0
) :
    BlockDC(axisCoordinate, blockLabel, branchIndex) {

    val mainSwitch: CircuitEdgeDC
    val gapNodes: List<CircuitNodeDC>
    val leftSwitches: List<CircuitEdgeDC>
    val rightSwitches: List<CircuitEdgeDC>

    val mainSwitchLeftPin: CircuitNodeDC
    val mainSwitchRightPin: CircuitNodeDC
    val gnd: CircuitNodeDC

    var branchFeederEdges: List<CircuitEdgeDC> = emptyList()
    var branchFeederInfoList: List<BranchFeederInfo<Double>> = emptyList()

    /**
     * Состояние воздушного промежутка между левой и правой половинами ПС.
     * true - закорот, false - разрыв
     */
    var medianSwitchState: Boolean
        set(value) = gapNodes.forEach { it.breaking = !value }
        get() = !gapNodes[0].breaking

    init {
        if (zB.size % 2 != 0) throw IllegalArgumentException("Нечетное количество параметров Z_b при добавлении ПС.")
        val trackQty = zB.size / 2

        fun nodeLabel(i: Int): String = when (i) {
            in 1..trackQty -> "${blockLabel}_FL_$i"
            in 11..trackQty + 10 -> "${blockLabel}_Gap_${i % 10}"
            in 21..trackQty + 20 -> "${blockLabel}_FR_${i % 10}"
            else -> throw RuntimeException()
        }

        mainSwitchLeftPin = addNode(axisCoordinate, -1, "${blockLabel}_comLeft", index = 100)
        mainSwitchRightPin = addNode(axisCoordinate, -1, "${blockLabel}_comRight", index = 101)
        gnd = addNode(axisCoordinate, 0.amendConsideringBranchIndex(), "${blockLabel}_gnd", index = 102)

        mainSwitch = addEdge(100, 101, CircuitEdgeDC("${blockLabel}_Switch", ZERO))

        // выходные узлы левого фидера
        (1..trackQty).forEach {
            val x = coordinates[it - 1]
            addNode(
                coordinate = if (x >= axisCoordinate) axisCoordinate - 1e-3 else x,
                trackNumber = it.amendConsideringBranchIndex(),
                label = nodeLabel(it),
                index = it
            )
        }

        // узлы воздушного промежутка
        gapNodes = (11..trackQty + 10).map {
            addNode(
                coordinate = axisCoordinate,
                trackNumber = (it % 10).amendConsideringBranchIndex(),
                label = nodeLabel(it),
                breaking = true,
                index = it // По умолчанию воздушный промежуток существует, не пробрасывать КС через эти узлы
            )
        }

        // выходные узлы правого фидера
        (21..trackQty + 20).forEach {
            val x = coordinates[(it % 10) - 1 + trackQty]
            addNode(
                coordinate = if (x <= axisCoordinate) axisCoordinate + 1e-3 else x,
                trackNumber = (it % 10).amendConsideringBranchIndex(),
                label = nodeLabel(it),
                index = it
            )
        }

        val leftSwitches = mutableListOf<CircuitEdgeDC>()
        (1..trackQty).forEach {
            leftSwitches.add(
                addEdge(100, it, CircuitEdgeDC("${blockLabel}_ZbL$it", zB[it - 1]))
            )
        }
        this.leftSwitches = leftSwitches

        val rightSwitches = mutableListOf<CircuitEdgeDC>()
        (21..trackQty + 20).forEach {
            rightSwitches.add(
                addEdge(101, it, CircuitEdgeDC("${blockLabel}_ZbR${it % 10}", zB[(it % 10) - 1 + trackQty]))
            )
        }
        this.rightSwitches = rightSwitches
    }

    fun addBranchFeeders(branchFeederInfoList: List<BranchFeederInfo<Double>>) {
        this.branchFeederInfoList = branchFeederInfoList
        val branchIndicesProcesses = mutableSetOf<Int>()
        branchFeederEdges = branchFeederInfoList
            .sortedBy { it.branchIndex }
            .map { info ->
                check(info.branchIndex > 0) { "Индекс ответвления должен быть больше 0. 0 - индекс основного хода." }
                check(info.branchIndex <= 200_000) {
                    "Индекс ответвления должен быть меньше 200_000. Задано значение ${info.branchIndex}"
                }

                if (branchIndicesProcesses.add(info.branchIndex)) {
                    val node = addNode(
                        coordinate = info.connectionPoint,
                        trackNumber = 0.amendConsideringBranchIndex(info.branchIndex),
                        label = "$blockLabel:Rail_br${info.branchIndex}"
                    )
                    addEdge(
                        source = gnd,
                        target = node,
                        edge = CircuitEdgeDC("$blockLabel:Rail_br${info.branchIndex}", info.feederResistance)
                    )
                }

                val node = addNode(
                    coordinate = info.connectionPoint,
                    trackNumber = info.trackNumber.amendConsideringBranchIndex(info.branchIndex),
                    label = "$blockLabel:Fb${info.branchIndex}"
                )
                addEdge(
                    source = mainSwitchRightPin,
                    target = node,
                    edge = CircuitEdgeDC("$blockLabel:Zb_br${info.branchIndex}", info.feederResistance)
                )
            }
    }

    override fun updateState(dto: BlockDCDto) {
        dto as? BlockSPDCDto ?: throw IllegalArgumentException(
            """
                |BlockSPDC::updateState -- аргумент не приводится к типу BlockSPDCDto
                |Фактический тип: ${dto::class}
            """.trimMargin()
        )

        mainSwitch.value = if (dto.mainSwitchState) ZERO else INF
        medianSwitchState = dto.medianSwitchState
        gapNodes.forEach { it.breaking = !medianSwitchState }

        val trackQty = dto.leftSwitchesState.size
        for (i in 0 until trackQty) {
            leftSwitches[i].value = if (dto.leftSwitchesState[i]) zB[i] else INF
            rightSwitches[i].value = if (dto.rightSwitchesState[i]) zB[i + trackQty] else INF
        }

        for (i in dto.branchSwitchesState.indices) {
            val dtoState = dto.branchSwitchesState[i]
            val thisState = branchFeederInfoList[i]
            check(dtoState.branchIndex == thisState.branchIndex) {
                "Клиентское приложение передало данные в некорректном формате: у фидеров ответвлений не согласованы номера ветвей."
            }
            check(dtoState.trackNumber == thisState.trackNumber) {
                "Клиентское приложение передало данные в некорректном формате: у фидеров ответвлений не согласованы номера путей."
            }
            thisState.feederResistance = dtoState.feederResistance
            thisState.switchedOn = dtoState.switchedOn
            branchFeederEdges[i].value = if (thisState.switchedOn) thisState.feederResistance else INF
        }
    }

    override fun toDto(): BlockSPDCDto = BlockSPDCDto(
        axisCoordinate = this.axisCoordinate,
        blockLabel = this.blockLabel,
        mainSwitchState = mainSwitch.value < INF,
        medianSwitchState = medianSwitchState,
        leftSwitchesState = leftSwitches.map { it.value < INF },
        rightSwitchesState = rightSwitches.map { it.value < INF },
        description = this.description,
        rightFeederResistances = this.zB.slice((zB.size / 2)..zB.lastIndex),
        rightFeederConnectionPoints = this.coordinates.slice((coordinates.size / 2)..coordinates.lastIndex),
        leftFeederResistances = this.zB.slice(0 until (zB.size / 2)),
        leftFeederConnectionPoints = this.coordinates.slice(0 until (zB.size / 2)),
        branchSwitchesState = branchFeederInfoList,
        branchIndex = branchIndex
    )

    override fun solution(): InstantCircuitDCSolutionDataEntry {
        val amps = mutableListOf<Double>()
        val voltages = mutableListOf<Double>()
        if (medianSwitchState && leftSwitches.all { it.value > 9e6 } && rightSwitches.all { it.value > 9e6 } /*сеть спрямлена*/) {
            val cnEdges = gapNodes.map { node ->
                tc.graph.edgesOf(node)
                    .toList()
                    .sortedBy { edge -> edge.name }
                    .find { it.getSourceNode() == node }
            }
            amps.addAll(cnEdges.map { it?.amp ?: 0.0 })
            voltages.addAll(gapNodes.sortedBy { it.trackNumber }.map { (it.p ?: 0.0) - (gnd.p ?: 0.0) })
        } else {
            amps.addAll(leftSwitches.map { it.amp ?: 0.0 })
            amps.addAll(rightSwitches.map { it.amp ?: 0.0 })
            voltages.add((mainSwitchLeftPin.p ?: 0.0) - (gnd.p ?: 0.0))
            voltages.add((mainSwitchRightPin.p ?: 0.0) - (gnd.p ?: 0.0))
        }
        return InstantCircuitDCSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel.replace("SP", "ПС "),
            description = description,
            amperages = amps,
            voltages = voltages
        )
    }
}

/**
 * Блок-контейнер для блоков схемы ответвления.
 * @param axisCoordinate точка присоединения ответвления к схеме основного хода
 * @param branchIndex индекс ответвления в схеме
 * @param blocks блоки устройств, входящие в схему ответвления. Должны быть отсортированы по возрастанию осевой координаты.
 */
class BlockBranchDc(
    axisCoordinate: Double,
    branchIndex: Int,
    val blocks: OrderedList<BlockDC>,
    val network: List<MutableList<DCNetworkDto>>
) :
    BlockDC(axisCoordinate, "Branch #$branchIndex", branchIndex) {

    init {
        check(branchIndex > 0) { "Индекс ветви схемы ($blockLabel) должен быть больше 1" }
        var ssCnt = 0
        for ((i, block) in blocks.withIndex()) {
            check(block.branchIndex == branchIndex) {
                "Индекс ветви (${block.branchIndex}) блока ${block.blockLabel} не совпадает индексом ветви, в которую он должен быть добавлен ($branchIndex)."
            }
            if (block is IBlockSSAcDuplex) {
                check(++ssCnt <= 1) {
                    "В схеме ответвления (№$branchIndex) задано больше одной ЭЧЭ"
                }
                check(i == blocks.lastIndex) {
                    "В схеме ответвления (№$branchIndex) задана ЭЧЭ, но она не является завершающим блоком."
                }
            }
        }
    }

    override fun mergeInto(circuit: Circuit<Double, CircuitEdgeDC, CircuitNodeDC>) {
        for ((i, block) in blocks.withIndex()) {
            val graphToMerge = when (block) {
                is BlockSSDC -> prepareBlockAndGetGraph(i, block, circuit as TransitionalCircuitDC)
                else -> block.graph
            }
            Graphs.addGraph(this.graph, graphToMerge)
        }
        super.mergeInto(circuit)
    }

    private fun prepareBlockAndGetGraph(
        index: Int,
        ss: BlockSSDC,
        circuit: TransitionalCircuitDC
    ): DirectedWeightedMultigraph<CircuitNodeDC, CircuitEdgeDC> {
        val hostBranchNodes = circuit.graph.vertexSet().filter {
            it.trackNumber.extractBranchIndex() == this.branchIndex
        }
        val axisCoordinate = ss.axisCoordinate
        val leftPoint = hostBranchNodes.minOfOrNull { it.coordinate } ?: axisCoordinate
        val rightPoint = hostBranchNodes.maxOfOrNull { it.coordinate } ?: axisCoordinate
        when {
            index == 0 && blocks.size > 1 -> {
                check(leftPoint >= axisCoordinate) {
                    "Ошибка составления схемы ветви $blockLabel. Одна или несколько точек присоединения расположена за границами схемы."
                }
                ss.leftFeederEdges.disable()
            }
            index == 0 && blocks.size == 1 -> {
                when (axisCoordinate) {
                    in Double.MIN_VALUE..leftPoint -> ss.leftFeederEdges.disable()
                    in rightPoint..Double.MAX_VALUE -> ss.rightFeederEdges.disable()
                    else -> throw java.lang.IllegalStateException(
                        "Ошибка составления схемы ветви $blockLabel. Точки присоединения расположены по разные стороны от ЭЧЭ."
                    )
                }
            }
            else -> { // ЭЧЭ последняя, схема слева от нее
                check(rightPoint <= axisCoordinate) {
                    "Ошибка составления схемы ветви $blockLabel. Одна или несколько точек присоединения расположена за границами схемы."
                }
                ss.rightFeederEdges.disable()
            }
        }
        return ss.graph
    }

    /**
     *  Исключить фидера из трассировки КС
     *  @receiver Список ветвей графа, соответствующих фидерам
     *  */
    private fun List<CircuitEdgeDC>.disable() = this.forEach {
        it.getTargetNode().breaking = true
    }

    override fun updateState(dto: BlockDCDto) {
        check(dto is BlockBranchDcDto)
        for (i in blocks.indices) {
            val block = blocks[i]
            val state = dto.blocks[i]
            block.updateState(state)
        }
    }

    override fun toDto(): BlockBranchDcDto {
        return BlockBranchDcDto(
            axisCoordinate = axisCoordinate,
            blockLabel = blockLabel,
            description = description,
            blocks = blocks.map { it.toDto() },
            network = network,
            branchIndex = branchIndex
        )
    }

    override fun solution(): InstantCircuitDCSolutionDataEntry {
        return InstantCircuitBranchDCSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            branchSchemaSolutions = blocks.map { it.solution() }
        )
    }
}

class BlockSplitterDc(
    axisCoordinate: Double,
    wiringLayout: List<Connection<Int>>,
    blockLabel: String,
    val connectedBranchIndex: Int,
    branchIndex: Int = 0,
) : BlockDC(axisCoordinate, blockLabel, branchIndex) {

    val wiring = mutableMapOf<Connection<Int>, CircuitEdgeDC>()

    val wiringState: List<SplitterWiringState<Int>>
        get() = wiring.map { (conn, edge) -> SplitterWiringState(conn, edge.value < INF) }

    init {
        require(connectedBranchIndex > 0) { "Индекс ответвления, присоединяемого к блоку $blockLabel должен быть больше 1" }
        require(wiringLayout.isNotEmpty()) { "Карта соединений элемента $blockLabel пуста" }

        val railToRailConnection = Connection(0, 0, axisCoordinate, wiringLayout.first().secondConnectionPoint)
        for (conn in wiringLayout + railToRailConnection) {
            if (!wiring.containsKey(conn)) {
                val primaryTrackNumber = conn.firstTrackNumber
                val branchTrackNumber = conn.secondTrackNumber.amendConsideringBranchIndex(connectedBranchIndex)
                val sourceNode = graph.vertexSet()
                    .find { it.trackNumber == primaryTrackNumber }
                    ?: addNode(
                        coordinate = conn.firstConnectionPoint ?: axisCoordinate,
                        trackNumber = primaryTrackNumber,
                        label = "$blockLabel:$primaryTrackNumber"
                    )
                val targetNode = graph.vertexSet()
                    .find { it.trackNumber == branchTrackNumber }
                    ?: addNode(
                        coordinate = conn.secondConnectionPoint ?: 0.0,
                        trackNumber = branchTrackNumber,
                        label = "$blockLabel:$branchTrackNumber"
                    )
                val edge = addEdge(
                    source = sourceNode,
                    target = targetNode,
                    edge = CircuitEdgeDC("$blockLabel:$primaryTrackNumber-$branchTrackNumber", ZERO)
                )

                if (conn.firstTrackNumber != 0 /* Перемычку между рельсами в карту соединений включать не нужно */) {
                    wiring[conn] = edge
                }
            }
        }
    }

    override fun updateState(dto: BlockDCDto) {
        require(dto is BlockSplitterDcDto) {
            "Несоответствие типов: ожидался BlockSplitterDcDto, фактические получен ${dto::class.simpleName}"
        }
        require(dto.state.size == wiring.size) {
            "Карта соединения, переданная клиентским приложением не согласована с картой соединений блока $blockLabel"
        }
        for (ws in dto.state) {
            val edge = wiring[ws.connection] ?: throw IllegalStateException(
                "Карта соединения, переданная клиентским приложением не согласована с картой соединений блока $blockLabel"
            )
            edge.value = if (ws.switchedOn) ZERO else INF
        }
    }

    override fun toDto(): BlockDCDto {
        return BlockSplitterDcDto(
            axisCoordinate = axisCoordinate,
            blockLabel = blockLabel,
            description = description,
            connectedBranchIndex = connectedBranchIndex,
            state = wiringState,
            branchIndex = branchIndex
        )
    }

    override fun solution(): InstantCircuitDCSolutionDataEntry {
        val amperages = mutableListOf<SplitterWiringDcSolution>()
        val voltages = mutableListOf<SplitterWiringDcSolution>()

        for ((conn, edge) in wiring) {
            amperages.add(SplitterWiringDcSolution(conn.getTrackPair(), edge.amp ?: 0.0))
            voltages.add(SplitterWiringDcSolution(conn.getTrackPair(), edge.getSourceNode().p ?: 0.0))
        }

        return InstantCircuitSplitterDCSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            wiringAmperages = amperages,
            wiringVoltages = voltages
        )
    }
}

class BlockShortCircuitPointDc(
    axisCoordinate: Double,
    blockLabel: String,
    var resistance: Double,
    var trackNumber: Int,
    branchIndex: Int = 0
) : BlockDC(axisCoordinate, blockLabel, branchIndex) {

    val cnNode = addNode(
        coordinate = axisCoordinate,
        trackNumber = trackNumber.amendConsideringBranchIndex(),
        label = "${blockLabel}_cn"
    )

    private val railNode = addNode(
        coordinate = axisCoordinate,
        trackNumber = 0.amendConsideringBranchIndex(),
        label = "${blockLabel}_rail"
    )

    val arc = addEdge(cnNode, railNode, CircuitEdgeDC("${blockLabel}_arc", resistance))

    override fun updateState(dto: BlockDCDto) {
        require(dto is BlockShortCircuitPointDcDto) {
            "Несоответствие типов: ожидался BlockShortCircuitPointDcDto, фактические получен ${dto::class.simpleName}"
        }
        axisCoordinate = dto.axisCoordinate
        blockLabel = dto.blockLabel
        description = dto.description
        resistance = dto.resistance
        trackNumber = dto.trackNumber
    }

    override fun toDto(): BlockDCDto {
        return BlockShortCircuitPointDcDto(
            axisCoordinate, blockLabel, description, resistance, trackNumber, branchIndex
        )
    }

    fun fromBlockDto(dto: BlockShortCircuitPointDcDto) = with(dto) {
        BlockShortCircuitPointDc(
            axisCoordinate, blockLabel, resistance, trackNumber, branchIndex
        )
    }

    override fun solution(): InstantCircuitDCSolutionDataEntry {
        return InstantCircuitDCSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            amperages = mutableListOf(arc.amp ?: 0.0),
            voltages = mutableListOf((cnNode.p ?: 0.0) - (railNode.p ?: 0.0)),
            particularAttributes = mutableMapOf("trackNumber" to "$trackNumber")
        )
    }
}