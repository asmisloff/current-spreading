package ru.vniizht.asuterkortes.counter.acnew

import org.apache.commons.math3.complex.Complex
import ru.vniizht.currentspreading.core.acnew.BlockAC
import ru.vniizht.currentspreading.core.acnew.BlockPayloadAC
import ru.vniizht.currentspreading.core.acnew.BlockPayloadAcd
import ru.vniizht.currentspreading.dto.*

// TODO: 09.02.2022 Пул поездов

class BlockACFactory {

    var ssCnt: Int = 0
    var payloadCnt = 0
    var spCnt = 0
    var jumperCnt = 0

    var spaCnt: Int = 0
    var atCnt: Int = 0

    var branchCnt = 0
    var splitterCnt = 0

    /**
     * Создать блок нагрузки
     */
    fun makePayload(
        trackNumber: Int,
        trackQty: Int,
        supplyQty: Int?,
        amperage: Complex,
        coordinate: Double,
        routeIndex: Int = 0
    ): BlockPayloadAC {
        return when (supplyQty) {
            null -> BlockPayloadAC(trackNumber, trackQty, amperage, coordinate, "Нагрузка-${++payloadCnt}", routeIndex)
            else -> BlockPayloadAcd("Нагрузка-${++payloadCnt}", coordinate, trackNumber, trackQty, amperage, routeIndex)
        }
    }

    fun fromDto(o: ConvertableToBlockAc, trackQty: Int, supplyQty: Int?): BlockAC {
        return when (o) {
            is ACStationDto, is ACDStationDto -> o.toBlock(++ssCnt, trackQty, supplyQty)
            is ACSectionDto, is ACDSectionDto -> o.toBlock(++spCnt, trackQty, supplyQty)
            is ACDSectionWithAtpDto -> o.toBlock(++spaCnt, trackQty, supplyQty)
            is ConnectorDto -> o.toBlock(++jumperCnt, trackQty, supplyQty)
            is AcdAtpDto -> o.toBlock(++atCnt, trackQty, supplyQty)
            is ACBranchDto -> o.toBlock(++branchCnt, trackQty, supplyQty)
            is BranchPointDto -> o.toBlock(++splitterCnt, trackQty, supplyQty)
            is AcdBranchPointDto -> o.toBlock(++splitterCnt, trackQty, supplyQty)
        }
    }

}