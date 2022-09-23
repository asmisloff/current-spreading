package ru.vniizht.currentspreading.core.acnew

import org.apache.commons.math3.complex.Complex
import org.ejml.data.Complex_F32
import org.jgrapht.Graphs
import org.jgrapht.graph.DirectedWeightedMultigraph
import ru.vniizht.asuterkortes.counter.circuit.CircuitEdgeAC
import ru.vniizht.asuterkortes.counter.circuit.CircuitNodeAC
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.CompactPayloadSolution
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.CompactSolution
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.CompactSsAcSolution
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.circuit.Block
import ru.vniizht.currentspreading.core.circuit.BranchFeederInfo
import ru.vniizht.currentspreading.core.circuit.Circuit
import ru.vniizht.currentspreading.core.circuit.amendConsideringBranchIndex
import ru.vniizht.currentspreading.core.schedule.OrderedList
import ru.vniizht.currentspreading.core.throughout.capacity.integral.indices.exp60degF
import ru.vniizht.currentspreading.dto.ACNetworkDto
import ru.vniizht.currentspreading.dto.Connection
import ru.vniizht.currentspreading.util.eq
import ru.vniizht.currentspreading.util.leftHalf
import ru.vniizht.currentspreading.util.rightHalf
import ru.vniizht.currentspreading.util.toFixed
import kotlin.math.PI

/**
 * Базовый класс для блоков схемы МПЗ на переменном токе
 */
abstract class BlockAC(
    override var axisCoordinate: Double,
    open var blockLabel: String,
    override val branchIndex: Int = 0
) : Block<Complex, CircuitEdgeAC, CircuitNodeAC>(CircuitEdgeAC::class.java, CircuitNodeAC::class.java) {

    protected lateinit var tc: TransitionalCircuitAC

    abstract var zeroNode: CircuitNodeAC

    override fun toString(): String {
        return "$blockLabel($axisCoordinate)"
    }

    abstract fun updateState(dto: BlockACDto)

    abstract fun toDto(): BlockACDto

    override fun mergeInto(circuit: Circuit<Complex, CircuitEdgeAC, CircuitNodeAC>) {
        super.mergeInto(circuit)
        tc = circuit as TransitionalCircuitAC
        when (tc.zeroNode) {
            null -> tc.zeroNode = this.zeroNode
            else -> this.zeroNode = tc.zeroNode!!
        }
    }

    abstract fun solution(): InstantCircuitSolutionDataEntry

    open fun compactSolution(): CompactSolution {
        throw NotImplementedError("Not implemented for ${this::class}")
    }

    abstract fun solutionReport(): String

    protected fun List<BranchFeederInfo<Complex>>.makeEdges(
        blockLabel: String,
        sourceNodeProvider: (branchFeederInfo: BranchFeederInfo<Complex>) -> CircuitNodeAC
    ) = this.mapTo(mutableListOf()) { info ->
        check(info.branchIndex > 0) { "Индекс ответвления должен быть больше 0. 0 - индекс основного хода." }
        check(info.branchIndex <= 200_000) {
            "Индекс ответвления должен быть меньше 200_000. Задано значение ${info.branchIndex}"
        }
        val node = addNode(
            coordinate = info.connectionPoint,
            trackNumber = info.trackNumber.amendConsideringBranchIndex(info.branchIndex),
            label = "$blockLabel:FR_br${info.branchIndex}",
            breaking = false
        )
        addEdge(
            sourceNodeProvider(info),
            node,
            CircuitEdgeAC("$blockLabel:Zbr_br${info.branchIndex}", info.feederResistance)
        )
    }

}

/**
 * Блок трансформаторной подстанции
 */
abstract class BlockSSAC(
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zSS: Complex,
    xCompXInv: Complex,
    xCompX: Complex,
    xCompY: Complex,
    vOut: Complex,
    blockLabel: String,
    val phaseOrder: PhaseOrder,
    val duplex: IBlockSSAcDuplex? = null,
    suckingFeederResistance: Complex = ZERO,
    branchIndex: Int = 0
) : BlockAC(axisCoordinate, blockLabel, branchIndex) {

    override var zeroNode = addNode(suckingCoordinate, 0, "RAIL", index = 0)
    val cnOut = addNode(axisCoordinate, -1, "${blockLabel}:cnOut", index = 3)
    val suckerNode = addNode(axisCoordinate, -1, "${blockLabel}:suckInp", index = 5)

    abstract val zssEdge: CircuitEdgeAC
    abstract val issEdge: CircuitEdgeAC
    abstract val halfIssEdge: CircuitEdgeAC

    val xCompXInvEdge: CircuitEdgeAC
    val xCompXEdge: CircuitEdgeAC
    val suckingFeederEdge: CircuitEdgeAC
    var xCompYEdge: CircuitEdgeAC? = null

    init {
        addNode(axisCoordinate, -1, "${blockLabel}:emfSource", index = 1)
        addNode(axisCoordinate, -1, "${blockLabel}:emfTarget", index = 2)
        xCompXInvEdge = addEdge(1, 5, CircuitEdgeAC("${blockLabel}_Xkxi", xCompXInv))
        xCompXEdge = addEdge(2, 3, CircuitEdgeAC("${blockLabel}_Xkx", xCompX))
        if (!xCompY.isInf()) xCompYEdge = addEdge(3, 5, CircuitEdgeAC("${blockLabel}_Xky", xCompY))
        suckingFeederEdge = addEdge(5, 0, CircuitEdgeAC("${blockLabel}_FSuck", suckingFeederResistance))
    }

    open val feederNodes = mutableListOf<CircuitNodeAC>()

    val feederEdges = mutableListOf<CircuitEdgeAC>()

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

    var prevFeedersAmp: Complex? = null

    open fun addFeeders(coordinates: List<Double>, zF: List<Complex>) {
        val trackQty = coordinates.size

        for (i in 1..trackQty) {
            val node = addNode(
                coordinate = coordinates[i - 1],
                trackNumber = i.amendConsideringBranchIndex(),
                label = "${blockLabel}_Zf:$i"
            )
            val edge = addEdge(
                3,
                node.index,
                CircuitEdgeAC("${blockLabel}_Zf_$i", zF[i - 1])
            )
            feederEdges.add(edge)
            feederNodes.add(node)
        }
    }

    open fun totalFeederAmp() = feederEdges.sumOfOrNull { it.amp }

    abstract override fun updateState(dto: BlockACDto)

    override fun solution() = InstantCircuitACSolutionDataEntry(
        coordinate = axisCoordinate,
        objectName = blockLabel,
        description = description,
        particularAttributes = mutableMapOf(),
        amperages = feederEdges.mapTo(mutableListOf()) { it.amp ?: ZERO },
        voltages = mutableListOf(cnOut.p ?: ZERO)
    )

    override fun toDto(): BlockACDto {
        TODO("Not implemented")
    }

}

open class BlockSSAcA(
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zSS: Complex,
    xCompXInv: Complex,
    xCompX: Complex,
    xCompY: Complex,
    vOut: Complex,
    blockLabel: String,
    phaseOrder: PhaseOrder,
    feederCoordinates: List<Double>,
    feederResistances: List<Complex>,
    duplex: IBlockSSAcDuplex? = null,
    branchIndex: Int = 0,
    var branchFeederInfoList: List<BranchFeederInfo<Complex>> = emptyList()
) : BlockSSAC(
    axisCoordinate,
    suckingCoordinate,
    zSS,
    xCompXInv,
    xCompX,
    xCompY,
    vOut,
    "$blockLabel:A",
    phaseOrder,
    duplex,
    branchIndex = branchIndex
) {

    final override val zssEdge: CircuitEdgeAC
    final override val issEdge: CircuitEdgeAC
    final override val halfIssEdge: CircuitEdgeAC

    init {
        zssEdge = addEdge(1, 2, CircuitEdgeAC("${blockLabel}_Zss", zSS, emf = vOut))
        halfIssEdge = addEdge(1, 2, CircuitEdgeAC("${blockLabel}_1/2Iss", INF))
        issEdge = addEdge(5, 1, CircuitEdgeAC("${blockLabel}_Iss", INF))
        super.addFeeders(feederCoordinates, feederResistances)
    }

    override fun totalFeederAmp(): Complex? {
        return (feederEdges + branchFeederEdges).sumOfOrNull { it.amp }
    }

    val branchFeederEdges = branchFeederInfoList.makeEdges(blockLabel) { cnOut }

    override fun updateState(dto: BlockACDto) {
        check(dto is BlockSSACDto) { "Неверный тип аргумента: ${dto::class.java}. Ожидался: BlockSSACDto" }
        check(dto.branchFeederSwitchesState.size == branchFeederEdges.size) {
            """
                |Несоответствие в количестве фидеров ответвления.
                |Передано ${dto.branchFeederSwitchesState.size} флагов состояния,
                |фактически на $blockLabel задано ${branchFeederEdges.size} фидеров ответвления
            """.trimMargin().replace("\n", " ")
        }

        if (!dto.mainSwitchState) {
            zssEdge.emf = Complex.ZERO
        } else {
            zssEdge.emf = vOut
        }

        for ((i, st) in dto.rightCnSwitchesState.withIndex()) {
            if (!st) feederEdges[i].value = INF
            /*
            TODO: 11.01.2022
             Учесть сопротивления фидеров из dto. Сейчас мгн. схема в клиентском приложении не имеет соотв. элементов управления,
             и сопротивления вытягиваются из базы.
            */
        }

        dto.branchFeederSwitchesState.asSequence()
            .forEachIndexed { i, _ ->
                val dtoState = dto.branchFeederSwitchesState[i]
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

    override fun mergeInto(circuit: Circuit<Complex, CircuitEdgeAC, CircuitNodeAC>) {
        for (c in feederNodes.map { it.coordinate }) {
            check(c >= axisCoordinate) {
                "Точка присоединения правого фидера не может находиться левее оси подстанции. " +
                        "Точка присоединения фидера: $c, подстанция: ${duplex?.description} ($axisCoordinate)"
            }
        }
        super.mergeInto(circuit)
    }

    override fun solutionReport(): String {
        return """
            |    Правое плечо: ${(feederEdges + branchFeederEdges).map { it.amp?.expRepr(1) ?: 0.0 }.joinToString(";")};
            |        сумма: ${totalFeederAmp()?.expRepr(1) ?: ZERO}; напр.: ${cnOut.p?.expRepr(1) ?: ZERO}
        """.trimMargin()
    }

}

open class BlockSSAcB(
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zSS: Complex,
    xCompXInv: Complex,
    xCompX: Complex,
    xCompY: Complex,
    vOut: Complex,
    blockLabel: String,
    phaseOrder: PhaseOrder,
    feederCoordinates: List<Double>,
    feederResistances: List<Complex>,
    duplex: IBlockSSAcDuplex? = null,
    branchIndex: Int = 0
) : BlockSSAC(
    axisCoordinate,
    suckingCoordinate,
    zSS,
    xCompXInv,
    xCompX,
    xCompY,
    vOut,
    "$blockLabel:B",
    phaseOrder,
    duplex,
    branchIndex = branchIndex
) {

    final override val zssEdge: CircuitEdgeAC
    final override val issEdge: CircuitEdgeAC
    final override val halfIssEdge: CircuitEdgeAC

    init {
        zssEdge = addEdge(2, 1, CircuitEdgeAC("${blockLabel}_Zss", zSS, emf = vOut))
        halfIssEdge = addEdge(2, 1, CircuitEdgeAC("${blockLabel}_1/2Iss", INF))
        issEdge = addEdge(1, 5, CircuitEdgeAC("${blockLabel}_Iss", INF))
        super.addFeeders(feederCoordinates, feederResistances)
    }

    override fun updateState(dto: BlockACDto) {
        dto as? BlockSSACDto
            ?: throw IllegalArgumentException("Неверный тип аргумента: ${dto::class.java}. Ожидался: BlockSSACDto")

        if (!dto.mainSwitchState) {
            zssEdge.emf = Complex.ZERO
        } else {
            zssEdge.emf = vOut
        }

        for ((i, st) in dto.leftCnSwitchesState.withIndex()) {
            if (!st) feederEdges[i].value = INF
        }
    }

    override fun mergeInto(circuit: Circuit<Complex, CircuitEdgeAC, CircuitNodeAC>) {
        for (c in feederNodes.map { it.coordinate }) {
            check(c <= axisCoordinate) {
                "Точка присоединения левого фидера не может находиться правее оси подстанции. " +
                        "Точка присоединения фидера: $c, подстанция: ${duplex?.description} ($axisCoordinate)"
            }
        }
        super.mergeInto(circuit)
    }

    override fun solutionReport(): String {
        return """
            |    Левое плечо: ${feederEdges.map { it.amp?.expRepr(1) ?: 0.0 }.joinToString(";")};
            |        сумма: ${totalFeederAmp()?.expRepr(1) ?: 0.0}; напр. ${cnOut.p?.expRepr(1) ?: 0.0}
        """.trimMargin()
    }

}

interface IBlockSSAcDuplex {
    val ssA: BlockSSAcA
    val ssB: BlockSSAcB
    val description: String

    fun exchangeShouldersAmperages() {
        val ssAAmp = ssA.totalFeederAmp()
        val ssBAmp = ssB.totalFeederAmp()

        ssA.issEdge.csa = ssBAmp
            ?.rotate(if (ssA.phaseOrder == PhaseOrder.LEADING) PI / 3.0 else -PI / 3.0)
            ?: ZERO
        ssA.halfIssEdge.csa = ssA.issEdge.csa.times(0.5)

        ssB.issEdge.csa = ssAAmp
            ?.rotate(if (ssB.phaseOrder == PhaseOrder.LEADING) PI / 3.0 else -PI / 3.0)
            ?.negate()
            ?: ZERO
        ssB.halfIssEdge.csa = ssB.issEdge.csa.times(0.5)
    }

    fun trace() {
        println("${ssB.blockLabel}(${ssB.phaseOrder.name}): Iпп = ${ssB.issEdge.csa.expRepr()}, 1/2Iпп = ${ssB.halfIssEdge.csa.expRepr()}, Zтп = ${ssB.zssEdge.amp?.expRepr()}")
        println("${ssA.blockLabel}(${ssA.phaseOrder.name}: Iпп = ${ssA.issEdge.csa.expRepr()}, 1/2Iпп = ${ssA.halfIssEdge.csa.expRepr()}, Zтп = ${ssA.zssEdge.amp?.expRepr()}")
    }

    fun compactSolution(): CompactSsAcSolution

}

class BlockSSAcDuplex(
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zSS: Complex,
    xCompXInv: Complex,
    xCompXLeft: Complex,
    xCompXRight: Complex,
    xCompYLeft: Complex,
    xCompYRight: Complex,
    vOut: Complex,
    blockLabel: String,
    leftShoulderPhaseOrder: PhaseOrder,
    feederCoordinates: List<Double>,
    feederResistances: List<Complex>,
    branchIndex: Int = 0,
    branchFeederInfoList: List<BranchFeederInfo<Complex>> = emptyList()
) : BlockAC(axisCoordinate, blockLabel, branchIndex), IBlockSSAcDuplex {

    init {
        require(feederCoordinates.size % 2 == 0) { "Нечетное кол. точек подключения фидеров на подстанции $blockLabel}" }
        require(feederResistances.size % 2 == 0) { "Нечетное кол. сопротивлений фидеров на подстанции $blockLabel}" }
        require(feederResistances.size == feederCoordinates.size) { "Кол. точек подключения фидеров на подстанции $blockLabel} и кол. их сопротивлений не совпадают." }
    }

    override val ssA = BlockSSAcA(
        axisCoordinate,
        suckingCoordinate,
        zSS,
        xCompXInv,
        xCompXRight,
        xCompYRight,
        vOut,
        blockLabel,
        !leftShoulderPhaseOrder,
        feederCoordinates = feederCoordinates.rightHalf(),
        feederResistances = feederResistances.rightHalf(),
        duplex = this,
        branchIndex = branchIndex,
        branchFeederInfoList = branchFeederInfoList
    )
    override val ssB = BlockSSAcB(
        axisCoordinate,
        suckingCoordinate,
        zSS,
        xCompXInv,
        xCompXLeft,
        xCompYLeft,
        vOut.negate(),
        blockLabel,
        leftShoulderPhaseOrder,
        feederCoordinates = feederCoordinates.leftHalf(),
        feederResistances = feederResistances.leftHalf(),
        duplex = this,
        branchIndex = branchIndex
    )

    override var zeroNode = ssA.zeroNode

    override fun updateState(dto: BlockACDto) {
        ssA.updateState(dto)
        ssB.updateState(dto)
    }

    override fun toDto() = BlockSSACDto(
        axisCoordinate = axisCoordinate,
        blockLabel = blockLabel,
        description = description,
        vOut = ssA.vOut,
        zSS = ssA.zSS,
        mainSwitchState = ssA.zssEdge.emf != ZERO,
        leftCnFeederResistances = ssB.feederEdges.map { it.value },
        rightCnFeederResistances = ssA.feederEdges.map { it.value },
        leftCnFeederConnectionPoints = ssB.feederNodes.map { it.coordinate },
        rightCnFeederConnectionPoints = ssA.feederNodes.map { it.coordinate },
        leftCnSwitchesState = ssB.feederEdges.map { !it.value.isInf() },
        rightCnSwitchesState = ssA.feederEdges.map { !it.value.isInf() },
        branchFeederSwitchesState = ssA.branchFeederInfoList
    )

    override fun solution(): InstantCircuitACSolutionDataEntry {
        val amperages = ssB.feederEdges.mapTo(mutableListOf()) { it.amp ?: ZERO }
        amperages.addAll((ssA.feederEdges + ssA.branchFeederEdges).mapTo(mutableListOf()) { it.amp ?: ZERO })

        return InstantCircuitACSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel.replace("SS", "ЭЧЭ-"),
            description = description,
            particularAttributes = mutableMapOf(),
            amperages = amperages,
            voltages = mutableListOf(ssB.cnOut.p ?: ZERO, ssA.cnOut.p ?: ZERO)
        )
    }

    override fun compactSolution(): CompactSsAcSolution {
        val leftActiveFeederAmperages = FloatArray(ssB.feederEdges.size)
        val leftReactiveFeederAmperages = FloatArray(ssB.feederEdges.size)
        val rightActiveFeederAmperages = FloatArray(ssA.feederEdges.size)
        val rightReactiveFeederAmperages = FloatArray(ssA.feederEdges.size)
        var suckerActiveAmperage = 0f
        var suckerReactiveAmperage = 0f
        val buffer = Complex_F32()

        for (i in ssB.feederEdges.indices) {
            leftActiveFeederAmperages[i] = ssB.feederEdges[i].amp?.real?.toFloat() ?: 0f
            leftReactiveFeederAmperages[i] = ssB.feederEdges[i].amp?.imaginary?.toFloat() ?: 0f
            rightActiveFeederAmperages[i] = ssA.feederEdges[i].amp?.real?.toFloat() ?: 0f
            rightReactiveFeederAmperages[i] = ssA.feederEdges[i].amp?.imaginary?.toFloat() ?: 0f

            var leadActiveAmps: FloatArray
            var leadReactiveAmps: FloatArray
            var retActiveAmps: FloatArray
            var retReactiveAmps: FloatArray
            when (ssB.phaseOrder) {
                PhaseOrder.LEADING -> {
                    leadActiveAmps = leftActiveFeederAmperages
                    leadReactiveAmps = leftReactiveFeederAmperages
                    retActiveAmps = rightActiveFeederAmperages
                    retReactiveAmps = rightReactiveFeederAmperages
                }
                PhaseOrder.RETARDING -> {
                    leadActiveAmps = rightActiveFeederAmperages
                    leadReactiveAmps = rightReactiveFeederAmperages
                    retActiveAmps = leftActiveFeederAmperages
                    retReactiveAmps = leftReactiveFeederAmperages
                }
            }

            buffer.assign(
                retActiveAmps.sum(),
                retReactiveAmps.sum()
            )
            buffer.timesAssign(exp60degF)
            suckerActiveAmperage = (leadActiveAmps.sum().toDouble() + buffer.real).toFloat()
            suckerReactiveAmperage = (leadReactiveAmps.sum().toDouble() + buffer.imaginary).toFloat()
        }

        return CompactSsAcSolution(
            leftActiveCnFeederAmperages = leftActiveFeederAmperages,
            leftReactiveCnFeederAmperages = leftReactiveFeederAmperages,
            leftActiveCnVoltage = ssB.cnOut.p?.real?.toFloat() ?: 0f,
            leftReactiveCnVoltage = ssB.cnOut.p?.imaginary?.toFloat() ?: 0f,

            rightActiveCnFeederAmperages = rightActiveFeederAmperages,
            rightReactiveCnFeederAmperages = rightReactiveFeederAmperages,
            rightActiveCnVoltage = ssA.cnOut.p?.real?.toFloat() ?: 0f,
            rightReactiveCnVoltage = ssA.cnOut.p?.imaginary?.toFloat() ?: 0f,

            suckerActiveAmperage = suckerActiveAmperage,
            suckerReactiveAmperage = suckerReactiveAmperage,

            totalBranchCnActiveAmperage = ssA.branchFeederEdges.sumOf { it.amp?.real ?: 0.0 }.toFloat(),
            totalBranchCnReactiveAmperage = ssA.branchFeederEdges.sumOf { it.amp?.imaginary ?: 0.0 }.toFloat()
        )
    }

    override fun solutionReport(): String {
        return """
            |$blockLabel ($description, $axisCoordinate км)
            |${ssB.solutionReport()}
            |${ssA.solutionReport()}
        """.trimMargin()
    }

}

enum class PhaseOrder(val stringRepr: String) {
    LEADING("LEFT_FIRST"), RETARDING("RIGHT_FIRST");

    operator fun not() = when (this) {
        LEADING -> RETARDING
        else -> LEADING
    }

    companion object {

        fun leftShoulderOrderFromStringRepr(s: String) = when (s) {
            "LEFT_FIRST" -> LEADING
            "RIGHT_FIRST" -> RETARDING
            else -> throw IllegalArgumentException("Неизвестное значение порядка фаз на ТП: $s")
        }

    }
}

/**
 * Блок нагрузки
 */
open class BlockPayloadAC(
    trackNumber: Int,
    val trackQty: Int,
    val amperage: Complex,
    axisCoordinate: Double,
    blockLabel: String,
    val routeIndex: Int = 0,
    branchIndex: Int = 0
) : BlockAC(axisCoordinate, blockLabel, branchIndex) {

    val trackNumber = trackNumber.amendConsideringBranchIndex()

    final override var zeroNode = addNode(axisCoordinate, 0, "${blockLabel}_0", index = 0)
    val cnNode = addNode(axisCoordinate, this.trackNumber, "${blockLabel}_cn${this.trackNumber}", index = 1)

    val iplEdge = addEdge(cnNode, zeroNode, CircuitEdgeAC("${blockLabel}_Ipl", INF, csa = amperage))

    var pPrev: Complex? = null

    override fun toString(): String {
        return super.toString() + "/track#$trackNumber"
    }

    override fun toDto() = BlockPayloadACDto(
        axisCoordinate = axisCoordinate,
        blockLabel = this.blockLabel,
        amperage = amperage,
        trackNumber = trackNumber,
        trackQty = trackQty,
        description = this.description,
        branchIndex = branchIndex
    )

    override fun solution(): InstantCircuitSolutionDataEntry {
        return InstantCircuitACSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            particularAttributes = mutableMapOf(
                "trackNumber" to trackNumber.toString(),
                "routeIndex" to routeIndex.toString()
            ),
            amperages = mutableListOf(iplEdge.amp ?: ZERO),
            voltages = mutableListOf(cnNode.p ?: ZERO)
        )
    }

    override fun solutionReport(): String {
        val b = StringBuilder()
        b.append("${blockLabel} (${trackNumber}-й путь, ${axisCoordinate} км)\n")
        val i = (iplEdge.amp ?: ZERO).expRepr()
        val u = (cnNode.p ?: ZERO).abs().toFixed(0)
        b.append("    I = $i, U = $u")

        return b.toString()
    }

    override fun updateState(dto: BlockACDto) = Unit

    override fun compactSolution(): CompactPayloadSolution {
        assert(trackNumber <= 6) { "Расчет режимов СТЭ не должен учитывать ответвления" }
        val voltage = when {
            iplEdge.csa.real > 0 -> -cnNode.p!!.abs().toFloat()
            else -> cnNode.p!!.abs().toFloat()
        }
        return CompactPayloadSolution(
            axisCoordinate = axisCoordinate.toFloat(),
            voltage = voltage,
            trackNumber = trackNumber.toByte(),
            routeIndex = routeIndex.toShort()
        )
    }

}

/**
 * Блок ППС
 */
open class BlockJumperAC(
    axisCoordinate: Double,
    blockLabel: String,
    track1Number: Int,
    track2Number: Int,
    val trackQty: Int,
    branchIndex: Int = 0
) : BlockAC(axisCoordinate, blockLabel, branchIndex) {

    val track1Number = track1Number.amendConsideringBranchIndex()
    val track2Number = track2Number.amendConsideringBranchIndex()

    override var zeroNode = addNode(axisCoordinate, 0, "RAIL", index = 0)
    protected val cn1Node = addNode(
        axisCoordinate,
        this.track1Number,
        "${blockLabel}:CN_${this.track1Number}",
        index = 1
    )

    protected val jumperEdge = addEdge(
        cn1Node,
        addNode(axisCoordinate, this.track2Number, "${blockLabel}:CN_${this.track2Number}", index = 2),
        CircuitEdgeAC("${blockLabel}_jumper", ZERO)
    )

    var switchState: Boolean = true

    override fun updateState(dto: BlockACDto) {
        dto as? BlockJumperACDto ?: throw IllegalArgumentException(
            "BlockJumperDC::updateState -- аргумент не приводится к типу BlockJumperACDto. Фактический тип: ${dto::class}"
        )
        switchState = dto.switchState
        jumperEdge.value = if (switchState) ZERO else INF
    }

    override fun toDto(): BlockACDto =
        BlockJumperACDto(
            axisCoordinate = axisCoordinate,
            blockLabel = blockLabel,
            description = description,
            track1Number = track1Number,
            track2Number = track2Number,
            switchState = switchState,
            trackQty = trackQty,
            branchIndex = branchIndex
        )

    override fun solution(): InstantCircuitSolutionDataEntry = InstantCircuitACSolutionDataEntry(
        coordinate = axisCoordinate,
        objectName = blockLabel,
        description = description,
        particularAttributes = mutableMapOf("tracks" to "$track1Number - $track2Number"),
        amperages = mutableListOf(jumperEdge.amp ?: ZERO),
        voltages = mutableListOf(cn1Node.p ?: ZERO)
    )

    override fun solutionReport(): String {
        return "$blockLabel (пути $track1Number и $track2Number). Ток: ${jumperEdge.amp?.expRepr()}, напр.: ${
            (cn1Node.p ?: ZERO).abs().toFixed(0)
        }"
    }

}

/**
 * Блок поста секционирования
 */
open class BlockSpAc(
    axisCoordinate: Double,
    val coordinates: List<Double>,
    protected val zB: List<Complex>,
    blockLabel: String,
    branchIndex: Int = 0,
    var branchFeederInfoList: List<BranchFeederInfo<Complex>> = emptyList()
) : BlockAC(axisCoordinate, blockLabel, branchIndex) {

    val trackQty: Int

    val leftFeederNodes: List<CircuitNodeAC>
    val rightFeederNodes: List<CircuitNodeAC>
    val gapNodes: List<CircuitNodeAC>

    val leftSwitches: List<CircuitEdgeAC>
    val rightSwitches: List<CircuitEdgeAC>

    val mainSwitch: CircuitEdgeAC
    val mainSwitchLeftPin: CircuitNodeAC
    val mainSwitchRightPin: CircuitNodeAC

    override var zeroNode = addNode(axisCoordinate, 0, "RAIL", index = 102)

    init {
        if (zB.size % 2 != 0) throw IllegalArgumentException("Нечетное количество параметров Z_b при добавлении ПС.")
        trackQty = zB.size / 2

        fun nodeLabel(i: Int): String = when (i) {
            in 1..trackQty -> "${blockLabel}_FL_$i"
            in 11..trackQty + 10 -> "${blockLabel}_Gap_${i % 10}"
            in 21..trackQty + 20 -> "${blockLabel}_FR_${i % 10}"
            else -> throw RuntimeException()
        }

        mainSwitchLeftPin = addNode(axisCoordinate, -1, "${blockLabel}_comLeft", index = 100)
        mainSwitchRightPin = addNode(axisCoordinate, -1, "${blockLabel}_comRight", index = 101)

        mainSwitch = addEdge(100, 101, CircuitEdgeAC("${blockLabel}_Switch", ZERO))

        // выходные узлы левого фидера
        leftFeederNodes = (1..trackQty).map {
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
        rightFeederNodes = (21..trackQty + 20).map {
            val x = coordinates[(it % 10) - 1 + trackQty]
            addNode(
                coordinate = if (x <= axisCoordinate) axisCoordinate + 1e-3 else x,
                trackNumber = (it % 10).amendConsideringBranchIndex(),
                label = nodeLabel(it),
                index = it
            )
        }

        val leftSwitches = mutableListOf<CircuitEdgeAC>()
        (1..trackQty).forEach {
            leftSwitches.add(
                addEdge(100, it, CircuitEdgeAC("${blockLabel}_ZbL$it", zB[it - 1]))
            )
        }
        this.leftSwitches = leftSwitches

        val rightSwitches = mutableListOf<CircuitEdgeAC>()
        (21..trackQty + 20).forEach {
            rightSwitches.add(
                addEdge(101, it, CircuitEdgeAC("${blockLabel}_ZbR${it % 10}", zB[(it % 10) - 1 + trackQty]))
            )
        }
        this.rightSwitches = rightSwitches
    }

    val branchFeederEdges = branchFeederInfoList.makeEdges(blockLabel) { mainSwitchRightPin }

    /**
     * Состояние воздушного промежутка между левой и правой половинами ПС.
     * true - закорот, false - разрыв
     */
    open var medianSwitchState: Boolean
        set(value) = gapNodes.forEach { it.breaking = !value }
        get() = !gapNodes[0].breaking

    override fun updateState(dto: BlockACDto) {
        dto as? BlockSpAcDto ?: throw IllegalArgumentException(
            """
                |BlockSPAC::updateState -- аргумент не приводится к типу BlockSPACDto
                |Фактический тип: ${dto::class}
            """.trimMargin()
        )

        require(dto.leftCnSwitchesState.size == trackQty)
        require(dto.rightCnSwitchesState.size == trackQty)
        require(dto.leftCnFeederResistances.size == trackQty)
        require(dto.rightCnFeederResistances.size == trackQty)
        require(dto.leftCnFeederConnectionPoints.size == trackQty)
        require(dto.rightCnFeederConnectionPoints.size == trackQty)
        require(dto.branchFeederSwitchesState.size == branchFeederEdges.size)

        mainSwitch.value =
            if (dto.mainSwitchState) ZERO else INF
        medianSwitchState = dto.medianSwitchState

        // TODO: 13.01.2022 сопротивления фидеров из Dto

        for (i in 0 until trackQty) {
            leftSwitches[i].value = if (dto.leftCnSwitchesState[i]) zB[i] else INF
            rightSwitches[i].value =
                if (dto.rightCnSwitchesState[i]) zB[i + trackQty] else INF
        }

        dto.branchFeederSwitchesState.asSequence()
            .forEachIndexed { i, _ ->
                val dtoState = dto.branchFeederSwitchesState[i]
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

    override fun toDto(): BlockSpAcDto = BlockSpAcDto(
        axisCoordinate = this.axisCoordinate,
        blockLabel = this.blockLabel,
        mainSwitchState = !mainSwitch.value.isInf(),
        medianSwitchState = medianSwitchState,
        leftCnSwitchesState = leftSwitches.map { !it.value.isInf() },
        rightCnSwitchesState = rightSwitches.map { !it.value.isInf() },
        description = this.description,
        rightCnFeederResistances = this.zB.slice((zB.size / 2)..zB.lastIndex),
        rightCnFeederConnectionPoints = this.coordinates.slice((coordinates.size / 2)..coordinates.lastIndex),
        leftCnFeederResistances = this.zB.slice(0 until (zB.size / 2)),
        leftCnFeederConnectionPoints = this.coordinates.slice(0 until (zB.size / 2)),
        branchFeederSwitchesState = branchFeederInfoList,
        branchIndex = branchIndex
    )

    override fun solution(): InstantCircuitSolutionDataEntry {
        val amps = mutableListOf<Complex>()
        val voltages = mutableListOf<Complex>()
        if (medianSwitchState && leftSwitches.all { it.value.isInf() } && rightSwitches.all { it.value.isInf() } /*сеть спрямлена*/) {
            val cnEdges = gapNodes.map { node ->
                tc.graph.edgesOf(node)
                    .toList()
                    .sortedBy { edge -> edge.name }
                    .find { it.getSourceNode() == node }
            }
            amps.addAll(cnEdges.map { it?.amp ?: ZERO })
            voltages.addAll(gapNodes.sortedBy { it.trackNumber }.map { it.p ?: ZERO })
        } else {
            amps.addAll(leftSwitches.map { it.amp ?: ZERO })
            amps.addAll(rightSwitches.map { it.amp ?: ZERO })
            voltages.add(mainSwitchLeftPin.p ?: ZERO)
            voltages.add(mainSwitchRightPin.p ?: ZERO)
        }

        return InstantCircuitACSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            amperages = amps,
            voltages = voltages
        )
    }

    override fun solutionReport(): String {
        return """
            |${blockLabel} ($description, $axisCoordinate км)
            |    Левое плечо: ${leftSwitches.map { "${it.amp?.expRepr()}" }}
            |    Правое плечо: ${rightSwitches.map { "${it.amp?.expRepr()}" }}
            |    Напряжение: ${(mainSwitchLeftPin.p ?: ZERO).abs().toFixed(0)}
        """.trimMargin()
    }
}

/**
 * Ветвь схемы
 */
class BlockBranchAc(
    axisCoordinate: Double,
    branchIndex: Int,
    val blocks: OrderedList<BlockAC>,
    val network: List<ACNetworkDto>,
    blockLabel: String
) :
    BlockAC(axisCoordinate, blockLabel, branchIndex) {

    override var zeroNode = addNode(
        coordinate = axisCoordinate,
        trackNumber = 0,
        label = "RAIL"
    )

    init {
        check(branchIndex > 0) { "Индекс ветви схемы ($blockLabel) должен быть больше 1" }
        var ssCnt = 0
        for ((i, block) in blocks.withIndex()) {
            check(block.branchIndex == branchIndex) {
                "Индекс ветви (${block.branchIndex}) блока ${block.blockLabel} не совпадает индексом ветви, в которую он должен быть добавлен ($branchIndex)."
            }
            if (block is IBlockSSAcDuplex) {
                check(++ssCnt <= 1) {
                    "Ошибка составления схемы ветви $blockLabel. Нельзя установить больше одной ЭЧЭ."
                }
                check(i == blocks.lastIndex || i == 0) {
                    "Ошибка составления схемы ветви $blockLabel. ЭЧЭ может быть только первым или последним объектом."
                }
            }
        }
    }

    override fun mergeInto(circuit: Circuit<Complex, CircuitEdgeAC, CircuitNodeAC>) {
        for ((i, block) in blocks.withIndex()) {
            val graphToMerge = when (block) {
                is IBlockSSAcDuplex -> getAppropriateHalf(i, block, circuit as TransitionalCircuitAC)
                else -> block.graph
            }
            Graphs.addGraph(this.graph, graphToMerge)
            block.zeroNode = this.zeroNode
        }
        super.mergeInto(circuit)
    }

    private fun getAppropriateHalf(
        index: Int,
        ss: IBlockSSAcDuplex,
        circuit: TransitionalCircuitAC
    ): DirectedWeightedMultigraph<CircuitNodeAC, CircuitEdgeAC> {
        val hostBranchNodes = circuit.graph.vertexSet().filter {
            it.trackNumber.extractBranchIndex() == this.branchIndex
        }
        val axisCoordinate = ss.ssA.axisCoordinate
        val leftPoint = hostBranchNodes.minOfOrNull { it.coordinate } ?: axisCoordinate
        val rightPoint = hostBranchNodes.maxOfOrNull { it.coordinate } ?: axisCoordinate
        return when {
            index == 0 && blocks.size > 1 -> {
                check(leftPoint >= axisCoordinate) {
                    "Ошибка составления схемы ветви $blockLabel. Одна или несколько точек присоединения расположена за границами схемы."
                }
                ss.ssA.graph
            }
            index == 0 && blocks.size == 1 -> {
                when (axisCoordinate) {
                    in Double.NEGATIVE_INFINITY..leftPoint -> ss.ssA.graph
                    in rightPoint..Double.POSITIVE_INFINITY -> ss.ssB.graph
                    else -> throw IllegalStateException(
                        "Ошибка составления схемы ветви $blockLabel. Точки присоединения расположены по разные стороны от ЭЧЭ."
                    )
                }
            }
            else -> {
                check(rightPoint <= axisCoordinate) {
                    "Ошибка составления схемы ветви $blockLabel. Одна или несколько точек присоединения расположена за границами схемы."
                }
                ss.ssB.graph
            }
        }
    }

    override fun updateState(dto: BlockACDto) {
        require(dto is BlockBranchAcDto) {
            "Несоответствие типов: ожидался BlockBranchAcDto, фактические получен ${dto::class.simpleName}"
        }
        blocks.forEachIndexed { i, block ->
            block.updateState(dto.blocks[i])
        }
    }

    override fun toDto(): BlockBranchAcDto {
        return BlockBranchAcDto(
            axisCoordinate = axisCoordinate,
            blockLabel = blockLabel,
            description = description,
            branchIndex = branchIndex,
            blocks = blocks.map { it.toDto() },
            network = network
        )
    }

    override fun solution(): InstantCircuitSolutionDataEntry {
        return InstantCircuitBranchACSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            branchSchemaSolutions = blocks.map { it.solution() }
        )
    }

    override fun solutionReport(): String {
        val report = StringBuilder()
        report.append("Ответвление №$branchIndex\n")
        for (block in blocks) {
            report.append(block.solutionReport() + '\n')
        }
        report.setLength(report.length - 1) // удалить последний '\n'
        return report.toString()
    }
}

/**
 * Блок ОТВ
 */
class BlockSplitterAc(
    axisCoordinate: Double,
    blockLabel: String,
    val connectedBranchIndex: Int,
    wiringLayout: List<Connection<Int>>,
    branchIndex: Int = 0,
) : BlockAC(axisCoordinate, blockLabel, branchIndex) {

    val wiring = mutableMapOf<Connection<Int>, CircuitEdgeAC>()

    val wiringState: List<SplitterWiringState<Int>>
        get() = wiring.map { (conn, edge) -> SplitterWiringState(conn, edge.value.isNotInf()) }

    init {
        require(connectedBranchIndex > 0) { "Индекс ответвления, присоединяемого к блоку $blockLabel должен быть больше 1" }
        require(wiringLayout.isNotEmpty()) { "Карта соединений элемента $blockLabel пуста" }

        for (connection in wiringLayout) {
            if (!wiring.containsKey(connection)) {
                val trackPair = Pair(connection.firstTrackNumber, connection.secondTrackNumber)
                val primaryTrackNumber = trackPair.first.amendConsideringBranchIndex()
                val branchTrackNumber = trackPair.second.amendConsideringBranchIndex(connectedBranchIndex)
                val sourceNode = graph.vertexSet()
                    .find {
                        it.trackNumber == primaryTrackNumber && it.coordinate eq (connection.firstConnectionPoint
                            ?: axisCoordinate)
                    }
                    ?: addNode(
                        coordinate = connection.firstConnectionPoint ?: axisCoordinate,
                        trackNumber = primaryTrackNumber,
                        label = "$blockLabel:$primaryTrackNumber"
                    )
                val targetNode = graph.vertexSet()
                    .find {
                        it.trackNumber == branchTrackNumber && it.coordinate eq (connection.secondConnectionPoint
                            ?: 0.0)
                    }
                    ?: addNode(
                        coordinate = connection.secondConnectionPoint ?: 0.0,
                        trackNumber = branchTrackNumber,
                        label = "$blockLabel:$branchTrackNumber"
                    )
                val edge = addEdge(
                    source = sourceNode,
                    target = targetNode,
                    edge = CircuitEdgeAC("$blockLabel:$primaryTrackNumber-$branchTrackNumber", ZERO)
                )
                wiring[connection] = edge
            }
        }
    }

    override var zeroNode = CircuitNodeAC(axisCoordinate, 0, "Zero", false)

    override fun updateState(dto: BlockACDto) {
        require(dto is BlockSplitterAcDto) {
            "Несоответствие типов: ожидался BlockSplitterDto, фактические получен ${dto::class.simpleName}"
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

    override fun toDto(): BlockSplitterAcDto {
        return BlockSplitterAcDto(
            axisCoordinate = axisCoordinate,
            blockLabel = blockLabel,
            description = description,
            connectedBranchIndex = connectedBranchIndex,
            state = wiringState,
            branchIndex = branchIndex
        )
    }

    override fun solution(): InstantCircuitSolutionDataEntry {
        val amperages = mutableListOf<SplitterWiringAcSolution>()
        val voltages = mutableListOf<SplitterWiringAcSolution>()

        for ((conn, edge) in wiring) {
            amperages.add(SplitterWiringAcSolution(conn.getTrackPair(), edge.amp ?: ZERO))
            voltages.add(SplitterWiringAcSolution(conn.getTrackPair(), edge.getSourceNode().p ?: ZERO))
        }

        return InstantCircuitSplitterACSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            wiringAmperages = amperages,
            wiringVoltages = voltages
        )
    }

    override fun solutionReport(): String {
        val report = StringBuilder()
        report.append("$blockLabel ($description, $axisCoordinate км)\n")
        report.append("    Токи: ")
        for ((conn, edge) in wiring) {
            val trackPair = conn.getTrackPair()
            report.append("${trackPair.first}-${trackPair.second}: ${(edge.amp ?: ZERO).expRepr()}, ")
        }
        report.setLength(report.length - 2) // удалить последние символы ", "
        report.append("\n    Напряжения: ")
        for ((conn, edge) in wiring) {
            val trackPair = conn.getTrackPair()
            report.append("${trackPair.first}-${trackPair.second}: ${(edge.getSourceNode().p ?: ZERO).expRepr()}, ")
        }
        report.setLength(report.length - 2) // удалить последние символы ", "

        return report.toString()
    }

}
