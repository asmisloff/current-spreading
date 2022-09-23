package ru.vniizht.asuterkortes.counter.dcnew

import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.dcnew.*
import ru.vniizht.currentspreading.dto.*

/**
 * Фабрика блоков схемы МПЗ на постоянном токе.
 * Вспомогательный класс для упрощения нумерации блоков при построении схем.
 */
class BlockDCFactory {

    var ssCnt: Int = 0
    var payloadCnt = 0
    var spCnt = 0
    var jumperCnt = 0
    var branchCnt = 0
    var splitterCnt = 0
    var shortCircuitPointCnt = 0

    /**
     * Создать блок ТП
     */
    fun makeSS(
        axisCoordinate: Double,
        suckingCoordinate: Double,
        zSS: Double,
        xCompXInv: Double,
        vOut: Double = 3600.0,
        branchIndex: Int = 0
    ): BlockSSDC =
        BlockSSDC(axisCoordinate, suckingCoordinate, zSS, xCompXInv, vOut, "SS${++ssCnt}", branchIndex)

    /**
     * Создать блок нагрузки
     */
    fun makePayload(
        trackNumber: Int,
        amperage: Double,
        coordinate: Double,
        routeIndex: Int = 0,
        branchIndex: Int = 0
    ): BlockPayloadDC =
        BlockPayloadDC(trackNumber, amperage, coordinate, "PL${++payloadCnt}", routeIndex, branchIndex)

    fun makeShortCircuitPoint(dto: BlockShortCircuitPointDcDto) = with(dto) {
        BlockShortCircuitPointDc(
            axisCoordinate, "ТКЗ-${++shortCircuitPointCnt}", resistance, trackNumber, branchIndex
        )
    }

    /**
     * Создать блок поста секционирования
     */
    fun makeSP(axisCoordinate: Double, coordinates: DoubleArray, zB: DoubleArray, branchIndex: Int = 0): BlockSPDC =
        BlockSPDC(axisCoordinate, coordinates, zB, "SP${++spCnt}", branchIndex = branchIndex)

    fun makeJumper(axisCoordinate: Double, track1Number: Int, track2Number: Int, branchIndex: Int = 0): BlockJumperDC =
        BlockJumperDC(axisCoordinate, "J${++jumperCnt}", track1Number, track2Number, branchIndex)

    fun makeSplitter(axisCoordinate: Double, wiringLayout: List<Connection<Int>>, connectedBranchIndex: Int, branchIndex: Int = 0) =
        BlockSplitterDc(
            axisCoordinate = axisCoordinate,
            wiringLayout = wiringLayout,
            blockLabel = "Spl${++splitterCnt}",
            connectedBranchIndex = connectedBranchIndex,
            branchIndex = branchIndex
        )

    fun fromDto(o: ConvertableToBlockDc, trackQty: Int): BlockDC {
        return when (o) {
            is DCStationDto -> o.toBlock(++ssCnt, trackQty)
            is DCSectionDto -> o.toBlock(++spCnt, trackQty)
            is ConnectorDto -> o.toBlock(++jumperCnt, trackQty)
            is DCBranchDto -> o.toBlock(++branchCnt, trackQty)
            is BranchPointDto -> o.toBlock(++splitterCnt, trackQty)
        }
    }

}
