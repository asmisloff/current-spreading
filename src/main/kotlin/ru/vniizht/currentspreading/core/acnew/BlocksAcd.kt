package ru.vniizht.currentspreading.core.acnew

import org.apache.commons.math3.complex.Complex
import org.ejml.data.Complex_F32
import ru.vniizht.asuterkortes.counter.circuit.CircuitEdgeAC
import ru.vniizht.asuterkortes.counter.circuit.CircuitNodeAC
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.CompactAtAcdSolution
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.CompactSsAcSolution
import ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices.CompactSsAcdSolution
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.circuit.BranchFeederInfo
import ru.vniizht.currentspreading.core.circuit.Circuit
import ru.vniizht.currentspreading.core.throughout.capacity.integral.indices.exp60degF
import ru.vniizht.currentspreading.core.throughout.capacity.integral.indices.expMinus60degF
import ru.vniizht.currentspreading.dao.TransformerType
import ru.vniizht.currentspreading.util.leftHalf
import ru.vniizht.currentspreading.util.rightHalf
import ru.vniizht.currentspreading.util.toFixed
import kotlin.math.roundToInt

/**
 * Базовый интерфейс одного плеча подстанции
 */
private interface IBlockSSHalfAcd {
    // todo: поддержать возможность изменения точек присоединения и сопротивлений фидеров

    val zssEdge: CircuitEdgeAC
    val vOut: Complex
    val trackQty: Int

    val feederEdges: MutableList<CircuitEdgeAC>
    val supplyFeederEdges: MutableList<CircuitEdgeAC>

    val supplyOut: CircuitNodeAC

    val cnFeederCoords: List<Double>
    val cnFeederResistances: List<Complex>

    val supplyFeederCoords: List<Double>
    val supplyFeederResistances: List<Complex>

    val feederNodes: MutableList<CircuitNodeAC>
    val supplyFeederNodes: MutableList<CircuitNodeAC>

    /** Включение/выключение ЭДС */
    var emfSwitchState: Boolean
        get() = zssEdge.emf != ZERO
        set(value) {
            zssEdge.emf = when (value) {
                true -> vOut
                false -> ZERO
            }
        }

    /** Вкл/выкл фидеров КС */
    var cnSwitchesState: List<Boolean>
        get() = feederEdges.map { !it.value.isInf() }
        set(value) {
            for ((i, v) in value.withIndex()) {
                feederEdges[i].value = when (v) {
                    true -> cnFeederResistances[i]
                    false -> INF
                }
            }
        }

    /** Вкл/выкл фидеров ПП */
    var supplySwitchesState: List<Boolean>
        get() = supplyFeederEdges.map { !it.value.isInf() }
        set(value) {
            for ((i, v) in value.withIndex()) {
                supplyFeederEdges[i].value = when (v) {
                    true -> supplyFeederResistances[i]
                    false -> INF
                }
            }
        }

}

/**
 * Добавить фидера к блоку подстанции 2х25
 */
private fun BlockSSAC.addFeedersHelper(
    coordinates: List<Double>,
    resistances: List<Complex>,
    trackQty: Int,
    supplyNodes: MutableList<CircuitNodeAC>,
    supplyEdges: MutableList<CircuitEdgeAC>,
    sourceNode: CircuitNodeAC
) {
    for (i in trackQty until coordinates.size) { // питающие фидера
        val node = addNode(
            coordinate = coordinates[i],
            trackNumber = (1000 + (i - trackQty + 1)).amendConsideringBranchIndex(),
            label = "${blockLabel}_ZSf:${i - trackQty + 1}"
        )
        supplyNodes.add(node)
        val edge = addEdge(
            source = sourceNode,
            target = node,
            edge = CircuitEdgeAC("${blockLabel}_ZSf_${i - trackQty + 1}", resistances[i])
        )
        supplyEdges.add(edge)
    }
}

/**
 * Правое плечо (сторона А в схеме МПЗ) тяговой подстанции 2х25
 */
class BlockSSAcdA(
    blockLabel: String,
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zSS: Complex,
    xCompXInv: Complex,
    xCompX: Complex,
    val zTk: Complex,
    val zAt: Complex,
    val xCompSupply: Complex,
    xCompY: Complex,
    vOut: Complex,
    override val cnFeederCoords: List<Double>,
    override val cnFeederResistances: List<Complex>,
    override val supplyFeederCoords: List<Double>,
    override val supplyFeederResistances: List<Complex>,
    phaseOrder: PhaseOrder,
    val transformerType: TransformerType,
    duplex: IBlockSSAcDuplex? = null,
    branchIndex: Int = 0,
    branchFeederInfoList: List<BranchFeederInfo<Complex>> = emptyList()
) : BlockSSAcA(
    axisCoordinate = axisCoordinate,
    suckingCoordinate = suckingCoordinate,
    zSS = zSS,
    xCompXInv = xCompXInv,
    xCompX = xCompX + zTk,
    xCompY = xCompY,
    vOut = vOut,
    blockLabel = blockLabel,
    phaseOrder = phaseOrder,
    feederCoordinates = cnFeederCoords,
    feederResistances = cnFeederResistances,
    duplex = duplex,
    branchIndex = branchIndex,
    branchFeederInfoList = emptyList()
), IBlockSSHalfAcd {

    private val zAtEdge: CircuitEdgeAC
    override val trackQty = cnFeederCoords.size
    val supplyQty = supplyFeederCoords.size

    override val supplyOut: CircuitNodeAC
    override val supplyFeederEdges = mutableListOf<CircuitEdgeAC>()
    override val supplyFeederNodes = mutableListOf<CircuitNodeAC>()

    init {
        require(trackQty > 0) { "Списки координат и сопротивлений фидеров КС пусты: $blockLabel." }
        require(trackQty == cnFeederResistances.size) { "Списки координат и сопротивления фидеров КС должны быть одной длины: $blockLabel." }
        require(supplyQty == supplyFeederResistances.size) { "Списки координат и сопротивления фидеров питающей линии должны быть одной длины: $blockLabel." }

        supplyOut = addNode(coordinate = axisCoordinate, trackNumber = -1, label = "${blockLabel}:supplyOut")
        zAtEdge = CircuitEdgeAC("${blockLabel}_Zat", zAt + xCompSupply)
        addEdge(2, supplyOut.index, zAtEdge)

        this.addFeedersHelper(
            cnFeederCoords + supplyFeederCoords,
            cnFeederResistances + supplyFeederResistances,
            trackQty,
            supplyFeederNodes,
            supplyFeederEdges,
            supplyOut
        )

        this.branchFeederInfoList = branchFeederInfoList // через конструктор нельзя, иначе конструктор суперкласса создаст лишние ветви по этому списку
        branchFeederEdges.addAll(
            branchFeederInfoList.makeEdges(blockLabel) {
                when {
                    it.trackNumber.isCnTrackNumber() -> cnOut
                    it.trackNumber.isSupplyTrackNumber() -> supplyOut
                    else -> throw IllegalStateException("Не удалось идентифицировать индекс пути: ${it.trackNumber}")
                }
            }
        )
    }

    override fun totalFeederAmp() =
        (super.totalFeederAmp() ?: ZERO) + (supplyFeederEdges.sumOfOrNull { it.amp } ?: ZERO)

    override fun updateState(dto: BlockACDto) {
        super.updateState(dto) // фидера КС

        dto as? BlockSSAcdDto
            ?: throw IllegalArgumentException("Неверный тип аргумента: ${dto::class.java}. Ожидался: BlockSSAcdDto")
        checkNotNull(dto.rightSupplySwitchesState)
        check(dto.rightSupplySwitchesState.size == supplyFeederEdges.size)

        for ((i, st) in dto.rightSupplySwitchesState.withIndex()) { // фидера питающий проводов
            if (!st) supplyFeederEdges[i].value = INF
            /*
            TODO: 11.01.2022
             Учесть сопротивления питающих фидеров из dto. Сейчас мгн. схема в клиентском приложении не имеет соотв. элементов управления,
             и сопротивления вытягиваются из базы.
            */
        }
    }

    override fun mergeInto(circuit: Circuit<Complex, CircuitEdgeAC, CircuitNodeAC>) {
        for (c in feederNodes.map { it.coordinate }) {
            check(c >= axisCoordinate) {
                "Точка присоединения правого фидера КС не может находиться левее оси подстанции. " +
                        "Точка присоединения фидера: $c, подстанция: ${duplex?.description} ($axisCoordinate)"
            }
        }
        for (c in supplyFeederNodes.map { it.coordinate }) {
            check(c >= axisCoordinate) {
                "Точка присоединения правого фидера ПП не может находиться левее оси подстанции. " +
                        "Точка присоединения фидера: $c, подстанция: ${duplex?.description} ($axisCoordinate)"
            }
        }
        super.mergeInto(circuit)
    }

    @Deprecated("Теперь через конструктор")
    override fun addFeeders(coordinates: List<Double>, zF: List<Complex>) {
        throw NotImplementedError()
    }

    override fun solutionReport(): String {
        val b = StringBuilder()

        var iCn = (feederEdges.sumOfOrNull { it.amp } ?: ZERO).expRepr()
        val uCn = (cnOut.p ?: ZERO).abs().toFixed(0)
        var iSp = (supplyFeederEdges.sumOfOrNull { it.amp } ?: ZERO).expRepr()
        val uSp = (supplyOut.p ?: ZERO).abs().toFixed(0)
        b.append("В27-прв: Iкс = $iCn, Uк = $uCn, Iпп = $iSp, Uп = $uSp\n")

        for (tn in 1..trackQty) {
            iCn = (feederEdges[tn - 1].amp ?: ZERO).expRepr()
            iSp = (supplyFeederEdges[tn - 1].amp ?: ZERO).expRepr()
            b.append("\t\t$tn. Iкс = $iCn, Iпп = $iSp\n")
        }

        return b.dropLast(1).toString()
    }

}

/**
 * Левое плечо (сторона Б в схеме МПЗ) тяговой подстанции 2х25
 */
class BlockSSAcdB(
    blockLabel: String,
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zSS: Complex,
    xCompXInv: Complex,
    xCompX: Complex,
    val zTk: Complex,
    val zAt: Complex,
    val xCompSupply: Complex,
    xCompY: Complex,
    vOut: Complex,
    override val cnFeederCoords: List<Double>,
    override val cnFeederResistances: List<Complex>,
    override val supplyFeederCoords: List<Double>,
    override val supplyFeederResistances: List<Complex>,
    phaseOrder: PhaseOrder,
    val transformerType: TransformerType,
    duplex: IBlockSSAcDuplex? = null,
    branchIndex: Int = 0
) : BlockSSAcB(
    axisCoordinate = axisCoordinate,
    suckingCoordinate = suckingCoordinate,
    zSS = zSS,
    xCompXInv = xCompXInv,
    xCompX = xCompX + zTk,
    xCompY = xCompY,
    vOut = vOut,
    blockLabel = blockLabel,
    phaseOrder = phaseOrder,
    feederCoordinates = cnFeederCoords,
    feederResistances = cnFeederResistances,
    duplex = duplex,
    branchIndex = branchIndex
), IBlockSSHalfAcd {

    private val zAtEdge: CircuitEdgeAC
    override val trackQty = cnFeederCoords.size
    val supplyQty = supplyFeederCoords.size

    override val supplyOut: CircuitNodeAC
    override val supplyFeederEdges = mutableListOf<CircuitEdgeAC>()
    override val supplyFeederNodes = mutableListOf<CircuitNodeAC>()

    init {
        require(trackQty > 0) { "Списки координат и сопротивлений фидеров КС пусты: $blockLabel." }
        require(trackQty == cnFeederResistances.size) { "Списки координат и сопротивления фидеров КС должны быть одной длины: $blockLabel." }
        require(supplyQty == supplyFeederResistances.size) { "Списки координат и сопротивления фидеров питающей линии должны быть одной длины: $blockLabel." }

        supplyOut = addNode(coordinate = axisCoordinate, trackNumber = -1, label = "${blockLabel}:supplyOut")
        zAtEdge = CircuitEdgeAC("${blockLabel}_Zat", zAt + xCompSupply)
        addEdge(2, supplyOut.index, zAtEdge)

        this.addFeedersHelper(
            cnFeederCoords + supplyFeederCoords,
            cnFeederResistances + supplyFeederResistances,
            trackQty,
            supplyFeederNodes,
            supplyFeederEdges,
            supplyOut
        )
    }

    override fun totalFeederAmp() =
        (super.totalFeederAmp() ?: ZERO) + (supplyFeederEdges.sumOfOrNull { it.amp } ?: ZERO)

    override fun updateState(dto: BlockACDto) {
        super.updateState(dto) // фидера КС

        dto as? BlockSSAcdDto
            ?: throw IllegalArgumentException("Неверный тип аргумента: ${dto::class.java}. Ожидался: BlockSSAcdDto")
        checkNotNull(dto.leftSupplySwitchesState)
        check(dto.leftSupplySwitchesState.size == supplyFeederEdges.size)

        for ((i, st) in dto.leftSupplySwitchesState.withIndex()) { // фидера питающий проводов
            if (!st) supplyFeederEdges[i].value = INF
            /*
            TODO: 11.01.2022
             Учесть сопротивления питающих фидеров из dto. Сейчас мгн. схема в клиентском приложении не имеет соотв. элементов управления,
             и сопротивления вытягиваются из базы.
            */
        }
    }

    override fun mergeInto(circuit: Circuit<Complex, CircuitEdgeAC, CircuitNodeAC>) {
        for (c in feederNodes.map { it.coordinate }) {
            check(c <= axisCoordinate) {
                "Точка присоединения левого фидера КС не может находиться правее оси подстанции. " +
                        "Точка присоединения фидера: $c, подстанция: ${duplex?.description} ($axisCoordinate)"
            }
        }
        for (c in supplyFeederNodes.map { it.coordinate }) {
            check(c <= axisCoordinate) {
                "Точка присоединения левого фидера ПП не может находиться правее оси подстанции. " +
                        "Точка присоединения фидера: $c, подстанция: ${duplex?.description} ($axisCoordinate)"
            }
        }
        super.mergeInto(circuit)
    }

    @Deprecated("Теперь через конструктор")
    override fun addFeeders(coordinates: List<Double>, zF: List<Complex>) {
        throw NotImplementedError()
    }

    override fun solutionReport(): String {
        val b = StringBuilder()

        var iCn = (feederEdges.sumOfOrNull { it.amp } ?: ZERO).expRepr()
        val uCn = (cnOut.p ?: ZERO).abs().toFixed(0)
        var iSp = (supplyFeederEdges.sumOfOrNull { it.amp } ?: ZERO).expRepr()
        val uSp = (supplyOut.p ?: ZERO).abs().toFixed(0)
        b.append("В27-лев: Iкс = $iCn, Uк = $uCn, Iпп = $iSp, Uп = $uSp\n")

        for (tn in 1..trackQty) {
            iCn = (feederEdges[tn - 1].amp ?: ZERO).expRepr()
            iSp = (supplyFeederEdges[tn - 1].amp ?: ZERO).expRepr()
            b.append("\t\t$tn. Iкс = $iCn, Iпп = $iSp\n")
        }

        return b.dropLast(1).toString()
    }

}

/**
 * Тяговая подстанция 2х25 с двумя плечами
 * */
class BlockSSAcdDuplex(
    axisCoordinate: Double,
    suckingCoordinate: Double,
    zssLeft: Complex,
    zssRight: Complex,
    xCompXInv: Complex,
    xCompXLeft: Complex,
    xCompXRight: Complex,
    zTkLeft: Complex,
    zTkRight: Complex,
    zAtLeft: Complex,
    zAtRight: Complex,
    xCompYLeft: Complex,
    xCompYRight: Complex,
    xCompSupplyLeft: Complex,
    xCompSupplyRight: Complex,
    vOut: Complex,
    blockLabel: String,
    leftCnFeederCoords: List<Double>,
    leftCnFeederResistances: List<Complex>,
    leftSupplyFeederCoords: List<Double>,
    leftSupplyFeederResistances: List<Complex>,
    rightCnFeederCoords: List<Double>,
    rightCnFeederResistances: List<Complex>,
    rightSupplyFeederCoords: List<Double>,
    rightSupplyFeederResistances: List<Complex>,
    leftShoulderPhaseOrder: PhaseOrder,
    branchIndex: Int = 0,
    branchFeederInfoList: List<BranchFeederInfo<Complex>> = emptyList()
) : BlockAC(axisCoordinate, blockLabel, branchIndex), IBlockSSAcDuplex {

    override val ssA = BlockSSAcdA(
        blockLabel = blockLabel,
        axisCoordinate = axisCoordinate,
        suckingCoordinate = suckingCoordinate,
        zSS = zssRight,
        xCompXInv = xCompXInv,
        xCompX = xCompXRight,
        zTk = zTkRight,
        zAt = zAtRight,
        xCompSupply = xCompSupplyRight,
        xCompY = xCompYRight,
        vOut = vOut,
        cnFeederCoords = rightCnFeederCoords,
        cnFeederResistances = rightCnFeederResistances,
        supplyFeederCoords = rightSupplyFeederCoords,
        supplyFeederResistances = rightSupplyFeederResistances,
        phaseOrder = !leftShoulderPhaseOrder,
        transformerType = TransformerType.infer(zAtRight, zTkRight),
        duplex = this,
        branchIndex = branchIndex,
        branchFeederInfoList = branchFeederInfoList
    )

    override val ssB = BlockSSAcdB(
        blockLabel = blockLabel,
        axisCoordinate = axisCoordinate,
        suckingCoordinate = suckingCoordinate,
        zSS = zssLeft,
        xCompXInv = xCompXInv,
        xCompX = xCompXLeft,
        zTk = zTkLeft,
        zAt = zAtLeft,
        xCompSupply = xCompSupplyLeft,
        xCompY = xCompYLeft,
        vOut = vOut.negate(),
        cnFeederCoords = leftCnFeederCoords,
        cnFeederResistances = leftCnFeederResistances,
        supplyFeederCoords = leftSupplyFeederCoords,
        supplyFeederResistances = leftSupplyFeederResistances,
        phaseOrder = leftShoulderPhaseOrder,
        transformerType = TransformerType.infer(zAtLeft, zTkLeft),
        duplex = this,
        branchIndex = branchIndex
    )

    override var zeroNode = ssA.zeroNode

    private val trackQty = ssA.feederEdges.size
    var mainSwitchState
        get() = ssA.emfSwitchState
        set(value) {
            ssA.emfSwitchState = value
            ssB.emfSwitchState = value
        }
    var cnSwitchesState
        get() = ssB.cnSwitchesState + ssA.cnSwitchesState
        set(value) {
            ssB.cnSwitchesState = value.leftHalf()
            ssA.cnSwitchesState = value.rightHalf()
        }
    var supplySwitchesState
        get() = ssB.supplySwitchesState + ssA.supplySwitchesState
        set(value) {
            ssB.supplySwitchesState = value.leftHalf()
            ssA.supplySwitchesState = value.rightHalf()
        }

    override fun exchangeShouldersAmperages() {
        if (ssA.transformerType == ssB.transformerType) {
            super.exchangeShouldersAmperages()
        }
    }

    override fun compactSolution(): CompactSsAcSolution {
        val leftActiveCnFeederAmperages = FloatArray(ssB.feederEdges.size)
        val leftReactiveCnFeederAmperages = FloatArray(ssB.feederEdges.size)
        val rightActiveCnFeederAmperages = FloatArray(ssA.feederEdges.size)
        val rightReactiveCnFeederAmperages = FloatArray(ssA.feederEdges.size)
        val leftActiveSpFeederAmperages = FloatArray(ssB.feederEdges.size)
        val leftReactiveSpFeederAmperages = FloatArray(ssB.feederEdges.size)
        val rightActiveSpFeederAmperages = FloatArray(ssA.feederEdges.size)
        val rightReactiveSpFeederAmperages = FloatArray(ssA.feederEdges.size)
        var suckerActiveAmperage = 0f
        var suckerReactiveAmperage = 0f

        for (i in ssB.feederEdges.indices) {
            leftActiveCnFeederAmperages[i] = ssB.feederEdges[i].amp?.real?.toFloat() ?: 0f
            leftReactiveCnFeederAmperages[i] = ssB.feederEdges[i].amp?.imaginary?.toFloat() ?: 0f
            rightActiveCnFeederAmperages[i] = ssA.feederEdges[i].amp?.real?.toFloat() ?: 0f
            rightReactiveCnFeederAmperages[i] = ssA.feederEdges[i].amp?.imaginary?.toFloat() ?: 0f

            leftActiveSpFeederAmperages[i] = ssB.supplyFeederEdges[i].amp?.real?.toFloat() ?: 0f
            leftReactiveSpFeederAmperages[i] = ssB.supplyFeederEdges[i].amp?.imaginary?.toFloat() ?: 0f
            rightActiveSpFeederAmperages[i] = ssA.supplyFeederEdges[i].amp?.real?.toFloat() ?: 0f
            rightReactiveSpFeederAmperages[i] = ssA.supplyFeederEdges[i].amp?.imaginary?.toFloat() ?: 0f

        }

//        val sA = ssA.xCompXInvEdge.amp ?: ZERO
//        val sB = ssB.xCompXInvEdge.amp ?: ZERO

        val leftBuffer = Complex_F32()
        leftBuffer.assign(
//            sA.real.toFloat(), sA.imaginary.toFloat()
            leftActiveCnFeederAmperages.sum() - leftActiveSpFeederAmperages.sum(),
            leftReactiveCnFeederAmperages.sum() - leftReactiveSpFeederAmperages.sum()
        )
        leftBuffer.timesAssign(if (ssB.phaseOrder == PhaseOrder.LEADING) exp60degF else expMinus60degF)
        val rightBuffer = Complex_F32()
        rightBuffer.assign(
//            sB.real.toFloat(), sB.imaginary.toFloat()
            rightActiveCnFeederAmperages.sum() - rightActiveSpFeederAmperages.sum(),
            rightReactiveCnFeederAmperages.sum() - rightReactiveSpFeederAmperages.sum()
        )
        rightBuffer.timesAssign(if (ssA.phaseOrder == PhaseOrder.RETARDING) exp60degF else expMinus60degF)
        when (ssA.transformerType == ssB.transformerType) {
            true -> {
                suckerActiveAmperage = leftBuffer.real + rightBuffer.real
                suckerReactiveAmperage = leftBuffer.imaginary + rightBuffer.imaginary
            }

            false -> {
                val maxMagnBuffer = maxOf(leftBuffer, rightBuffer) { a, b ->
                    (a.magnitude2 - b.magnitude2).roundToInt()
                }
                suckerActiveAmperage = maxMagnBuffer.real
                suckerReactiveAmperage = maxMagnBuffer.imaginary
            }
        }

        val cnBranchFeederEdges = ssA.branchFeederEdges.filter { it.getTargetNode().trackNumber.isCnTrackNumber() }
        val spBranchFeederEdges = ssA.branchFeederEdges.filter { it.getTargetNode().trackNumber.isSupplyTrackNumber() }

        return CompactSsAcdSolution(
            leftActiveCnFeederAmperages = leftActiveCnFeederAmperages,
            leftReactiveCnFeederAmperages = leftReactiveCnFeederAmperages,
            leftActiveCnVoltage = ssB.cnOut.p?.real?.toFloat() ?: 0f,
            leftReactiveCnVoltage = ssB.cnOut.p?.imaginary?.toFloat() ?: 0f,

            leftActiveSpFeederAmperages = leftActiveSpFeederAmperages,
            leftReactiveSpFeederAmperages = leftReactiveSpFeederAmperages,
            leftActiveSpVoltage = ssB.supplyOut.p?.real?.toFloat() ?: 0f,
            leftReactiveSpVoltage = ssB.supplyOut.p?.imaginary?.toFloat() ?: 0f,

            rightActiveCnFeederAmperages = rightActiveCnFeederAmperages,
            rightReactiveCnFeederAmperages = rightReactiveCnFeederAmperages,
            rightActiveCnVoltage = ssA.cnOut.p?.real?.toFloat() ?: 0f,
            rightReactiveCnVoltage = ssA.cnOut.p?.imaginary?.toFloat() ?: 0f,

            rightActiveSpFeederAmperages = rightActiveSpFeederAmperages,
            rightReactiveSpFeederAmperages = rightReactiveSpFeederAmperages,
            rightActiveSpVoltage = ssA.supplyOut.p?.real?.toFloat() ?: 0f,
            rightReactiveSpVoltage = ssA.supplyOut.p?.imaginary?.toFloat() ?: 0f,

            suckerActiveAmperage = suckerActiveAmperage,
            suckerReactiveAmperage = suckerReactiveAmperage,

            totalBranchCnActiveAmperage = cnBranchFeederEdges.sumOf { it.amp?.real ?: 0.0 }.toFloat(),
            totalBranchCnReactiveAmperage = cnBranchFeederEdges.sumOf { it.amp?.imaginary ?: 0.0 }.toFloat(),
            totalBranchSpActiveAmperage = spBranchFeederEdges.sumOf { it.amp?.real ?: 0.0 }.toFloat(),
            totalBranchSpReactiveAmperage = spBranchFeederEdges.sumOf { it.amp?.imaginary ?: 0.0 }.toFloat()
        )
    }

    override fun updateState(dto: BlockACDto) {
        ssA.updateState(dto)
        ssB.updateState(dto)
    }

    override fun toDto() = BlockSSAcdDto(
        axisCoordinate = axisCoordinate,
        blockLabel = blockLabel,
        description = description,
        vOut = ssA.vOut,
        zSS = ssA.zSS,
        mainSwitchState = mainSwitchState,
        leftCnFeederResistances = ssB.feederEdges.map { it.value },
        rightCnFeederResistances = ssA.feederEdges.map { it.value },
        leftCnFeederConnectionPoints = ssB.feederNodes.map { it.coordinate },
        rightCnFeederConnectionPoints = ssA.feederNodes.map { it.coordinate },
        leftCnSwitchesState = ssB.cnSwitchesState,
        rightCnSwitchesState = ssA.cnSwitchesState,
        leftSupplyFeederResistances = ssB.supplyFeederEdges.map { it.value },
        rightSupplyFeederResistances = ssA.supplyFeederEdges.map { it.value },
        leftSupplyFeederConnectionPoints = ssB.supplyFeederNodes.map { it.coordinate },
        rightSupplyFeederConnectionPoints = ssA.supplyFeederNodes.map { it.coordinate },
        leftSupplySwitchesState = ssB.supplySwitchesState,
        rightSupplySwitchesState = ssA.supplySwitchesState,
        branchFeederSwitchesState = ssA.branchFeederInfoList,
        branchIndex = branchIndex
    )

    override fun solution(): InstantCircuitAcdSolutionDataEntry {
        val (cnBranchFeederEdges, spBranchFeederEdges) = ssA.branchFeederEdges.partition {
            it.getTargetNode().trackNumber.isCnTrackNumber()
        }
        return InstantCircuitAcdSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            trackQty = trackQty,
            cnAmperages = ssB.feederEdges.map { it.amp ?: ZERO } + (ssA.feederEdges + cnBranchFeederEdges).map {
                it.amp ?: ZERO
            } + listOf(ssB.suckingFeederEdge.amp ?: ZERO, ssA.suckingFeederEdge.amp ?: ZERO),
            cnVoltages = listOf(ssB.cnOut.p ?: ZERO, ssA.cnOut.p ?: ZERO),
            supplyAmperages = ssB.supplyFeederEdges.map {
                it.amp ?: ZERO
            } + (ssA.supplyFeederEdges + spBranchFeederEdges).map {
                it.amp ?: ZERO
            },
            supplyVoltages = listOf(ssB.supplyOut.p ?: ZERO, ssA.supplyOut.p ?: ZERO)
        )
    }

    override fun solutionReport(): String {
        return """
            |$blockLabel ($description, $axisCoordinate км)
            |   ${ssB.solutionReport()}
            |   ${ssA.solutionReport()}
            |
        """.trimMargin()
    }

}

/**
 * Пост секционирования для схемы 2х25
 */
class BlockSpAcd(
    blockLabel: String,
    axisCoordinate: Double,
    leftCnFeedersCoords: List<Double>,
    leftCnFeederResistances: List<Complex>,
    rightCnFeedersCoords: List<Double>,
    rightCnFeederResistances: List<Complex>,
    val leftSpFeedersCoords: List<Double> = leftCnFeedersCoords,
    val leftSpFeederResistances: List<Complex> = leftCnFeederResistances,
    val rightSpFeedersCoords: List<Double> = rightCnFeedersCoords,
    val rightSpFeederResistances: List<Complex> = rightCnFeederResistances,
    val supplyQty: Int,
    branchIndex: Int = 0,
    branchFeederInfoList: List<BranchFeederInfo<Complex>> = emptyList()
) : BlockSpAc(
    axisCoordinate = axisCoordinate,
    coordinates = leftCnFeedersCoords + rightCnFeedersCoords,
    zB = leftCnFeederResistances + rightCnFeederResistances,
    blockLabel = blockLabel,
    branchIndex = branchIndex,
    branchFeederInfoList = emptyList()
) {

    val leftSpSwitches: List<CircuitEdgeAC>
    val rightSpSwitches: List<CircuitEdgeAC>
    private val spCommonNodes: List<CircuitNodeAC>
    private val spMainSwitch: CircuitEdgeAC

    init {
        spCommonNodes = listOf(
            addNode(coordinate = axisCoordinate, trackNumber = -1, label = "${blockLabel}_spComL"),
            addNode(coordinate = axisCoordinate, trackNumber = -1, label = "${blockLabel}_spComR")
        )
        spMainSwitch = addEdge(spCommonNodes[0], spCommonNodes[1], CircuitEdgeAC("${blockLabel}_spMainSwitch", ZERO))
        val leftSpNodes = leftSpFeedersCoords.mapIndexed { i, x ->
            addNode(
                coordinate = if (x >= axisCoordinate) x - 1e-3 else x,
                trackNumber = (1001 + i).amendConsideringBranchIndex(),
                label = "${blockLabel}_spL_${i + 1}"
            )
        }
        val rightSpNodes = rightSpFeedersCoords.mapIndexed { i, x ->
            addNode(
                coordinate = if (x <= axisCoordinate) x + 1e-3 else x,
                trackNumber = (1001 + i).amendConsideringBranchIndex(),
                label = "${blockLabel}_spR_${i + 1}"
            )
        }
        leftSpNodes.forEachIndexed { i, node -> // узлы возд. промежутка на питающей линии
            addNode(
                coordinate = axisCoordinate,
                trackNumber = (1001 + i).amendConsideringBranchIndex(),
                label = "${blockLabel}_Gap_${1001 + i}",
                breaking = true
            )
        }
        leftSpSwitches = leftSpFeederResistances.mapIndexed { i, r ->
            addEdge(spCommonNodes[0], leftSpNodes[i], CircuitEdgeAC("${blockLabel}_zSpL_${i + 1}", r))
        }
        rightSpSwitches = rightSpFeederResistances.mapIndexed { i, r ->
            addEdge(spCommonNodes[1], rightSpNodes[i], CircuitEdgeAC("${blockLabel}_zSpR_${i + 1}", r))
        }

        this.branchFeederInfoList = branchFeederInfoList // через конструктор нельзя, иначе конструктор суперкласса создаст лишние ветви по этому списку
        branchFeederEdges.addAll(
            branchFeederInfoList.makeEdges(blockLabel) {
                when {
                    it.trackNumber.isCnTrackNumber() -> mainSwitchRightPin
                    it.trackNumber.isSupplyTrackNumber() -> spCommonNodes[1]
                    else -> throw IllegalStateException("Не удалось идентифицировать индекс пути: ${it.trackNumber}")
                }
            }
        )
    }

    override fun updateState(dto: BlockACDto) {
        dto as? BlockSpAcDto ?: throw IllegalArgumentException(
            """
                |BlockSPAC::updateState -- аргумент не приводится к типу BlockSPACDto
                |Фактический тип: ${dto::class}
            """.trimMargin()
        )
        super.updateState(dto)
        for (i in 0 until trackQty) {
            leftSpSwitches[i].value = if (dto.leftCnSwitchesState[i]) leftSpFeederResistances[i] else INF
            rightSpSwitches[i].value = if (dto.rightCnSwitchesState[i]) rightSpFeederResistances[i] else INF
        }
    }

    override fun toDto(): BlockSpAcdDto {
        return BlockSpAcdDto(
            axisCoordinate = this.axisCoordinate,
            blockLabel = this.blockLabel,
            mainSwitchState = mainSwitch.value.isNotInf(),
            medianSwitchState = medianSwitchState,
            leftCnSwitchesState = leftSwitches.map { !it.value.isInf() },
            rightCnSwitchesState = rightSwitches.map { !it.value.isInf() },
            description = this.description,
            rightCnFeederResistances = this.zB.slice((zB.size / 2)..zB.lastIndex),
            rightCnFeederConnectionPoints = this.coordinates.slice((coordinates.size / 2)..coordinates.lastIndex),
            leftCnFeederResistances = this.zB.slice(0 until (zB.size / 2)),
            leftCnFeederConnectionPoints = this.coordinates.slice(0 until (zB.size / 2)),
            rightSupplyFeederResistances = leftSpFeederResistances,
            rightSupplyFeederConnectionPoints = leftSpFeedersCoords,
            leftSupplyFeederResistances = rightSpFeederResistances,
            leftSupplyFeederConnectionPoints = rightSpFeedersCoords,
            branchFeederSwitchesState = branchFeederInfoList,
            branchIndex = branchIndex
        )
    }

    override fun solution(): InstantCircuitAcdSolutionDataEntry {
        val cnAmperages = mutableListOf<Complex>()
        val cnVoltages = mutableListOf<Complex>()
        val spAmperages = mutableListOf<Complex>()
        val spVoltages = mutableListOf<Complex>()

        cnAmperages.addAll(leftSwitches.map { it.amp ?: ZERO })
        cnAmperages.addAll(rightSwitches.map { it.amp ?: ZERO })
        cnVoltages.add(mainSwitchLeftPin.p ?: ZERO)
        cnVoltages.add(mainSwitchRightPin.p ?: ZERO)

        spAmperages.addAll(leftSpSwitches.map { it.amp ?: ZERO })
        spAmperages.addAll(rightSpSwitches.map { it.amp ?: ZERO })
        spVoltages.addAll(spCommonNodes.map { it.p ?: ZERO })

        val (cnBranchFeederEdges, spBranchFeederEdges) = branchFeederEdges.partition {
            it.getTargetNode().trackNumber.isCnTrackNumber()
        }

        return InstantCircuitAcdSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            trackQty = trackQty,
            cnAmperages = cnAmperages + cnBranchFeederEdges.map { it.amp ?: ZERO },
            cnVoltages = cnVoltages,
            supplyAmperages = spAmperages + spBranchFeederEdges.map { it.amp ?: ZERO },
            supplyVoltages = spVoltages
        )
    }
}

/** Блок ПСА - пост секционирования с автотрансформаторами */
class BlockSpaAcd(
    blockLabel: String,
    axisCoordinate: Double,
    leftCnFeedersCoords: List<Double>,
    leftCnFeederResistances: List<Complex>,
    rightCnFeedersCoords: List<Double>,
    rightCnFeederResistances: List<Complex>,
    leftAtResistance: Complex,
    rightAtResistance: Complex,
    leftSupplyFeederCoords: List<Double>,
    val leftSupplyFeederResistances: List<Complex>,
    rightSupplyFeederCoords: List<Double>,
    val rightSupplyFeederResistances: List<Complex>,
    branchIndex: Int = 0,
    branchFeederInfoList: List<BranchFeederInfo<Complex>> = emptyList()
) : BlockSpAc(
    axisCoordinate,
    leftCnFeedersCoords + rightCnFeedersCoords,
    leftCnFeederResistances + rightCnFeederResistances,
    blockLabel,
    branchIndex = branchIndex,
    branchFeederInfoList = mutableListOf()
) {

    private val supplyFeedersQty = leftSupplyFeederCoords.size

    private val leftAtEdge: CircuitEdgeAC
    private val rightAtEdge: CircuitEdgeAC

    private val leftSupplySwitches = mutableListOf<CircuitEdgeAC>()
    private val rightSupplySwitches = mutableListOf<CircuitEdgeAC>()

    private val leftSupplyOutNodes = mutableListOf<CircuitNodeAC>()
    private val supplyGapNodes = mutableListOf<CircuitNodeAC>()
    private val rightSupplyOutNodes = mutableListOf<CircuitNodeAC>()

    private val supplySwitchLeftPin = addNode(axisCoordinate, -2, "\"${blockLabel}_spComLeft\"", index = 200)
    private val supplySwitchRightPin = addNode(axisCoordinate, -2, "\"${blockLabel}_spComRight\"", index = 201)
    private val supplySwitch = addEdge(200, 201, CircuitEdgeAC("$blockLabel:spSwitch", ZERO))

    init {
        require(supplyFeedersQty == rightSupplyFeederCoords.size) { "Количества питающих проводов слева и справа от ПСА должны быть равны: $blockLabel." }
        require(supplyFeedersQty == leftSupplyFeederResistances.size) { "Списки координат и сопротивлений фидеров питающей линии должны быть одной длины: $blockLabel, слева." }
        require(supplyFeedersQty == rightSupplyFeederResistances.size) { "Списки координат и сопротивлений фидеров питающей линии должны быть одной длины: $blockLabel, справа." }
        require(leftSupplyFeederCoords.all { it <= axisCoordinate }) { "Некорректно заданы точки подключения фидеров питающей линии: $blockLabel" }
        require(rightSupplyFeederCoords.all { it >= axisCoordinate }) { "Некорректно заданы точки подключения фидеров питающей линии: $blockLabel" }

        leftAtEdge = addEdge(200, 100, CircuitEdgeAC("${blockLabel}:ATLeft", leftAtResistance))
        rightAtEdge = addEdge(201, 101, CircuitEdgeAC("${blockLabel}:ATRight", rightAtResistance))

        for ((i, x) in leftSupplyFeederCoords.withIndex()) { // узлы на питающей линии и фидера слева
            val xx = if (x == axisCoordinate) x - 1e-3 else x
            val supplyNodeIndex = 200 + 10 * (i + 1) // 210, 220
            val tn = (1001 + i).amendConsideringBranchIndex() // 1001, 1002

            leftSupplyOutNodes.add(addNode(xx, tn, "${blockLabel}:supplyOutL_${i + 1}", index = supplyNodeIndex))
            leftSupplySwitches.add(
                addEdge(200, supplyNodeIndex, CircuitEdgeAC("supplySwitchL_${i + 1}", leftSupplyFeederResistances[i]))
            )
        }

        for (i in 0 until supplyFeedersQty) { // Узлы возд. промежутка
            val gapNodeIndex = 1000 + 100 * (i + 1) /* 1100, 1200 */
            val tn = (1001 + i).amendConsideringBranchIndex() // 1001, 1002
            supplyGapNodes.add(
                addNode(axisCoordinate, tn, "${blockLabel}:SupplyGap_${i + 1}", true, gapNodeIndex)
            )
        }

        for ((i, x) in rightSupplyFeederCoords.withIndex()) { // узлы на питающей линии и фидера справа
            val xx = if (x == axisCoordinate) x + 1e-3 else x
            val supplyNodeIndex = 201 + 10 * (i + 1) // 211, 221

            val tn = (1001 + i).amendConsideringBranchIndex() // 1001, 1002
            rightSupplyOutNodes.add(
                addNode(xx, tn, "${blockLabel}:supplyOutR_${i + 1}", index = supplyNodeIndex)
            )

            val e = CircuitEdgeAC("supplySwitchR_${i + 1}", rightSupplyFeederResistances[i])
            rightSupplySwitches.add(e)
            addEdge(201, supplyNodeIndex, e)
        }

        this.branchFeederInfoList = branchFeederInfoList // через конструктор нельзя, иначе конструктор суперкласса создаст лишние ветви по этому списку
        branchFeederEdges.addAll(
            branchFeederInfoList.makeEdges(blockLabel) {
                when {
                    it.trackNumber.isCnTrackNumber() -> mainSwitchRightPin
                    it.trackNumber.isSupplyTrackNumber() -> supplySwitchRightPin
                    else -> throw IllegalStateException("Не удалось идентифицировать номер пути: ${it.trackNumber}")
                }
            }
        )
    }

    override var medianSwitchState: Boolean
        get() = super.medianSwitchState
        set(value) {
            gapNodes.forEach { it.breaking = !value }
            supplyGapNodes.forEach { it.breaking = !value }
        }

    var leftSupplySwitchesState: List<Boolean>
        get() = leftSupplySwitches.map { !it.value.isInf() }
        set(value) {
            require(value.size == 2)
            for ((i, e) in leftSupplySwitches.withIndex()) {
                e.value = if (value[i]) leftSupplyFeederResistances[i] else INF
            }
        }

    var rightSupplySwitchesState: List<Boolean>
        get() = rightSupplySwitches.map { !it.value.isInf() }
        set(value) {
            require(value.size == 2)
            for ((i, e) in rightSupplySwitches.withIndex()) {
                e.value = if (value[i]) rightSupplyFeederResistances[i] else INF
            }
        }

    var leftCnSwitchesState: List<Boolean>
        get() = leftSwitches.map { it.value.isNotInf() }
        set(value) {
            require(value.size == 2)
            for ((i, e) in leftSwitches.withIndex()) {
                e.value = if (value[i]) zB.leftHalf()[i] else INF
            }
        }

    var rightCnSwitchesState: List<Boolean>
        get() = rightSwitches.map { it.value.isNotInf() }
        set(value) {
            require(value.size == 2)
            for ((i, e) in rightSwitches.withIndex()) {
                e.value = if (value[i]) zB.rightHalf()[i] else INF
            }
        }

    var busSwitchState: Boolean
        get() = mainSwitch.value.isNotInf()
        set(value) {
            mainSwitch.value = if (value) ZERO else INF
            supplySwitch.value = if (value) ZERO else INF
        }

    override fun updateState(dto: BlockACDto) {
        super.updateState(dto)
        dto as? BlockSpaAcdDto ?: throw IllegalArgumentException(
            "BlockSpaAcd::updateState -- аргумент не приводится к типу BlockSpaAcdDto. Фактический тип: ${dto::class}."
        )

        require(dto.leftSupplyFeederResistances.size == supplyFeedersQty)
        require(dto.rightSupplyFeederResistances.size == supplyFeedersQty)
        require(dto.leftSupplySwitchesState.size == supplyFeedersQty)
        require(dto.rightSupplySwitchesState.size == supplyFeedersQty)

        // TODO: 13.01.2022 сопротивления и координаты фидеров из Dto

        mainSwitch.value = if (dto.mainSwitchState) ZERO else INF
        supplySwitch.value = if (dto.mainSwitchState) ZERO else INF

        for (supplyIndex in 0 until supplyFeedersQty) {
            leftSupplySwitches[supplyIndex].value = when (dto.leftSupplySwitchesState[supplyIndex]) {
                true -> leftSupplyFeederResistances[supplyIndex]
                else -> INF
            }

            rightSupplySwitches[supplyIndex].value = when (dto.rightSupplySwitchesState[supplyIndex]) {
                true -> rightSupplyFeederResistances[supplyIndex]
                else -> INF
            }
        }

        medianSwitchState = dto.medianSwitchState
    }

    override fun toDto(): BlockSpaAcdDto {
        return BlockSpaAcdDto(
            axisCoordinate = axisCoordinate,
            blockLabel = blockLabel,
            description = description,
            mainSwitchState = mainSwitch.value.isNotInf(),
            medianSwitchState = medianSwitchState,
            leftCnSwitchesState = leftCnSwitchesState,
            rightCnSwitchesState = rightCnSwitchesState,
            leftCnFeederResistances = leftSwitches.map { it.value },
            rightCnFeederResistances = rightSwitches.map { it.value },
            leftCnFeederConnectionPoints = leftFeederNodes.map { it.coordinate },
            rightCnFeederConnectionPoints = rightFeederNodes.map { it.coordinate },

            leftSupplyFeederResistances = leftSupplyFeederResistances,
            rightSupplyFeederResistances = rightSupplyFeederResistances,
            leftSupplySwitchesState = leftSupplySwitchesState,
            rightSupplySwitchesState = rightSupplySwitchesState,
            leftSupplyFeederConnectionPoints = leftSupplyOutNodes.map { it.coordinate },
            rightSupplyFeederConnectionPoints = rightSupplyOutNodes.map { it.coordinate },
            branchFeederSwitchesState = branchFeederInfoList,
            branchIndex = branchIndex
        )
    }

    override fun compactSolution(): CompactAtAcdSolution {
        return CompactAtAcdSolution(
            atActiveAmperages = floatArrayOf(
                leftAtEdge.amp?.real?.toFloat() ?: 0f, rightAtEdge.amp?.real?.toFloat() ?: 0f
            ),
            atReactiveAmperages = floatArrayOf(
                leftAtEdge.amp?.imaginary?.toFloat() ?: 0f, rightAtEdge.amp?.imaginary?.toFloat() ?: 0f
            ),
        )
    }

    override fun solution(): InstantCircuitAcdSolutionDataEntry {
        val cnAmperages = mutableListOf<Complex>()
        val cnVoltages = mutableListOf<Complex>()
        val supplyAmperages = mutableListOf<Complex>()
        val supplyVoltages = mutableListOf<Complex>()

        cnAmperages.add(leftAtEdge.amp ?: ZERO)
        cnAmperages.add(rightAtEdge.amp ?: ZERO)
        cnAmperages.addAll(leftSwitches.map { it.amp ?: ZERO })
        cnAmperages.addAll(rightSwitches.map { it.amp ?: ZERO })
        cnVoltages.add(mainSwitchLeftPin.p ?: ZERO)
        cnVoltages.add(mainSwitchRightPin.p ?: ZERO)

        supplyAmperages.addAll(leftSupplySwitches.map { it.amp ?: ZERO })
        supplyAmperages.addAll(rightSupplySwitches.map { it.amp ?: ZERO })
        supplyVoltages.add(supplySwitchLeftPin.p ?: ZERO)
        supplyVoltages.add(supplySwitchRightPin.p ?: ZERO)

        val (cnBranchFeederEdges, spBranchFeederEdges) = branchFeederEdges.partition {
            it.getTargetNode().trackNumber.isCnTrackNumber()
        }

        return InstantCircuitAcdSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            trackQty = trackQty,
            cnAmperages = cnAmperages + cnBranchFeederEdges.map { it.amp ?: ZERO },
            cnVoltages = cnVoltages,
            supplyAmperages = supplyAmperages + spBranchFeederEdges.map { it.amp ?: ZERO },
            supplyVoltages = supplyVoltages
        )
    }

    override fun solutionReport(): String {
        val b = StringBuilder()
        b.append("$blockLabel ($description, $axisCoordinate км)\n")

        var iCn = (leftAtEdge.amp ?: ZERO).expRepr()
        var uCn = (mainSwitchLeftPin.p ?: ZERO).abs().toFixed(0)
        var uSp: String? = (supplySwitchLeftPin.p ?: ZERO).abs().toFixed(0)
        b.append("\t\tA1. Iкс = $iCn, Uк = $uCn, Uп = $uSp\n")
        iCn = (rightAtEdge.amp ?: ZERO).expRepr()
        uCn = (mainSwitchRightPin.p ?: ZERO).abs().toFixed(0)
        uSp = (supplySwitchRightPin.p ?: ZERO).abs().toFixed(0)
        b.append("\t\tA2. Iкс = $iCn, Uк = $uCn, Uп = $uSp\n")

        var iSp: String?
        b.append("\tЛевое плечо:\n")
        for (i in 0 until trackQty) {
            iCn = (leftSwitches[i].amp ?: ZERO).expRepr()
            uCn = (leftFeederNodes[i].p ?: ZERO).abs().toFixed(0)
            iSp = if (i < supplyFeedersQty) (leftSupplySwitches[i].amp ?: ZERO).expRepr() else null
            uSp = if (i < supplyFeedersQty) (leftSupplyOutNodes[i].p ?: ZERO).abs().toFixed(0) else null
            b.append("\t\t${i + 1}. Iкс = $iCn, Uк = $uCn, Iпп = $iSp, Uп = $uSp\n")
        }

        b.append("\tПравое плечо:\n")
        for (i in 0 until trackQty) {
            iCn = (rightSwitches[i].amp ?: ZERO).expRepr()
            uCn = (rightFeederNodes[i].p ?: ZERO).abs().toFixed(0)
            iSp = if (i < supplyFeedersQty) (rightSupplySwitches[i].amp ?: ZERO).expRepr() else null
            uSp = if (i < supplyFeedersQty) (rightSupplyOutNodes[i].p ?: ZERO).abs().toFixed(0) else null
            b.append("\t\t${i + 1}. Iкс = $iCn, Uк = $uCn, Iпп = $iSp, Uп = $uSp\n")
        }

        return b.toString()
    }

}

/** Блок автотрансформатора */
class BlockAtpAcd(
    blockLabel: String,
    axisCoordinate: Double,
    var atResistances: List<Complex>,
    val trackQty: Int,
    branchIndex: Int = 0
) : BlockAC(axisCoordinate, blockLabel, branchIndex) {

    override var zeroNode = CircuitNodeAC(axisCoordinate, 0, "RAIL", false)

    private val supplyQty = atResistances.size

    init {
        require(trackQty >= supplyQty)
    }

    private var atSwitchesState = List(supplyQty) { true }
        set(value) {
            require(value.size == supplyQty)
            field = value
        }

    private val cnNodes = List(supplyQty) { i ->
        val tn = (i + 1).amendConsideringBranchIndex()
        val node = addNode(axisCoordinate, tn, "${blockLabel}:cn_$tn", index = i)
        node
    }

    private val supplyNodes = List(supplyQty) { i ->
        val tn = (i + 1001).amendConsideringBranchIndex()
        val node = addNode(axisCoordinate, tn, "${blockLabel}:supply_${i + 1}", index = i + 100)
        node
    }

    private val atEdges = List(supplyQty) { i ->
        val tn = i + 1
        addEdge(supplyNodes[i], cnNodes[i], CircuitEdgeAC("${blockLabel}:At_$tn", atResistances[i]))
    }

    override fun updateState(dto: BlockACDto) {
        require(dto is BlockAtpAcdDto) { "Ожидался BlockAtAcdDto, фактически передан ${dto::class}" }
        require(dto.atResistances.size == atResistances.size)
        require(dto.atSwitchesState.size == atSwitchesState.size)

        description = dto.description
        atResistances = dto.atResistances
        atSwitchesState = dto.atSwitchesState

        for ((i, st) in atSwitchesState.withIndex()) {
            atEdges[i].value = if (st) atResistances[i] else INF
        }
    }

    override fun toDto() = BlockAtpAcdDto(
        axisCoordinate, blockLabel, description, atResistances, atSwitchesState, trackQty, supplyQty, branchIndex
    )

    override fun compactSolution(): CompactAtAcdSolution {
        val atActiveAmperages = FloatArray(supplyQty)
        val atReactiveAmperages = FloatArray(supplyQty)
        for (i in 0 until supplyQty) {
            atActiveAmperages[i] = atEdges[i].amp?.real?.toFloat() ?: 0f
            atReactiveAmperages[i] = atEdges[i].amp?.imaginary?.toFloat() ?: 0f
        }
        return CompactAtAcdSolution(atActiveAmperages, atReactiveAmperages)
    }

    override fun solution() = InstantCircuitAcdSolutionDataEntry(
        coordinate = axisCoordinate,
        objectName = blockLabel,
        description = description,
        trackQty = supplyQty,
        cnAmperages = atEdges.map { it.amp ?: ZERO },
        cnVoltages = cnNodes.map { it.p ?: ZERO },
        supplyAmperages = atEdges.map { it.amp ?: ZERO },
        supplyVoltages = supplyNodes.map { it.p ?: ZERO },
    )

    override fun solutionReport(): String {
        val b = StringBuilder()
        b.append("$blockLabel ($description, $axisCoordinate км)\n")

        for (tn in 1..supplyQty) {
            val iCn = (atEdges[tn - 1].amp ?: ZERO).expRepr()
            val uCn = (cnNodes[tn - 1].p ?: ZERO).abs().toFixed(0)
            val uSp = (supplyNodes[tn - 1].p ?: ZERO).abs().toFixed(0)
            b.append("\t\tA$tn.\tIкс = $iCn\tUк = $uCn\tUп = $uSp\n")
        }

        return b.toString()
    }

}

/**
 * Блок нагрузки для схемы 2х25
 */
class BlockPayloadAcd(
    blockLabel: String,
    axisCoordinate: Double,
    trackNumber: Int,
    trackQty: Int,
    amperage: Complex,
    routeIndex: Int = 0,
    branchIndex: Int = 0
) : BlockPayloadAC(
    trackNumber, trackQty, amperage, axisCoordinate, blockLabel, routeIndex, branchIndex = branchIndex
) {

    override fun solution(): InstantCircuitAcdSolutionDataEntry {
        return InstantCircuitAcdSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            trackQty = trackQty,
            cnAmperages = listOf(iplEdge.amp ?: ZERO),
            cnVoltages = listOf(cnNode.p ?: ZERO),
            supplyVoltages = emptyList(),
            supplyAmperages = emptyList(),
            routeIndex = routeIndex
        )
    }

}


/**
 * Блок ППС для схемы 2х25
 */
class BlockJumperAcd(
    blockLabel: String,
    axisCoordinate: Double,
    track1Number: Int,
    track2Number: Int,
    trackQty: Int,
    val supplyQty: Int,
    branchIndex: Int = 0
) : BlockJumperAC(axisCoordinate, blockLabel, track1Number, track2Number, trackQty, branchIndex) {

    override fun toDto(): BlockACDto {
        return BlockJumperACDto(
            axisCoordinate = axisCoordinate,
            blockLabel = blockLabel,
            description = description,
            track1Number = track1Number,
            track2Number = track2Number,
            switchState = switchState,
            trackQty = trackQty
        )
    }

    override fun solution(): InstantCircuitSolutionDataEntry {
        return InstantCircuitAcdSolutionDataEntry(
            coordinate = axisCoordinate,
            objectName = blockLabel,
            description = description,
            trackQty = trackQty,
            cnAmperages = listOf(jumperEdge.amp ?: ZERO),
            cnVoltages = listOf(cn1Node.p ?: ZERO),
            supplyAmperages = emptyList(),
            supplyVoltages = emptyList()
        )
    }

}
