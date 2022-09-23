package ru.vniizht.currentspreading.controller

import org.apache.commons.math3.complex.Complex
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.CurrentSpreadingComputer
import ru.vniizht.currentspreading.core.acnew.InstantCircuitAC
import ru.vniizht.currentspreading.core.dcnew.InstantCircuitDC
import ru.vniizht.currentspreading.core.schedule.TrainPosition
import ru.vniizht.currentspreading.dto.DCNetworkDto
import ru.vniizht.currentspreading.service.ElectricalSchemaService
import ru.vniizht.currentspreading.service.SessionDataStorage

@RestController
@RequestMapping("/api/ic")
class InstantCircuitController(
    private val sessionDataStorage: SessionDataStorage,
    private val schemaService: ElectricalSchemaService
) {

    @GetMapping("/dc/solve")
    fun solveDcSchema(@RequestParam coord: Double, @RequestParam amp: Double): List<DoubleArray> {
        val schema = schemaService.getAnyDcSchema()
        if (sessionDataStorage.icDc == null) {
            sessionDataStorage.icDc = InstantCircuitDC(schema)
        }
        val ic = sessionDataStorage.icDc!!
        ic.removePayloads()
        ic.addPayload(1, amp, coord)
        ic.build()
        ic.solve()
        val solutions = ic.getReport()
        val pl = solutions.findPayload()
        val mpzBounds = solutions.getMpzBoundaries(pl)
        val x0 = pl.axisCoordinate()
        val L = mpzBounds.second - mpzBounds.first
        val i = Complex((pl as InstantCircuitDCSolutionDataEntry).amperages[0])
        val zr = schema.mainSchema.network[0].findRailResistance(x0)
        val rp = 25.0

        val csc = CurrentSpreadingComputer(rp, Complex(zr), Complex.ZERO)
        var x = mpzBounds.first
        val step = L / 1000.0
        val result = mutableListOf<DoubleArray>()
        while (x < mpzBounds.second) {
            result.add(doubleArrayOf(x, csc.fi(x, x0, i, mpzBounds.first, mpzBounds.second).real))
            x += step
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    @GetMapping("/ac/solve")
    fun solveAcSchema(
        @RequestParam coord: Double,
        @RequestParam activeAmp: Double,
        @RequestParam fullAmp: Double
    ): List<DoubleArray> {
        val schema = schemaService.getAnyAcSchema()
        if (sessionDataStorage.icAc == null) {
            sessionDataStorage.icAc = InstantCircuitAC.fromDto(schema)
        }
        val ic = sessionDataStorage.icAc!!
        ic.removePayloads()
        ic.addTrainPosition(TrainPosition(coord, activeAmp, 1, 1.0, 0, fullAmp), 2, null)
        ic.build()
        ic.solve()

        val solutions = ic.getSolution()
        val pl = solutions.findPayload()
        val mpzBounds = solutions.getMpzBoundaries(pl)
        val x0 = pl.axisCoordinate()
        val L = mpzBounds.second - mpzBounds.first
        val i = when (pl) {
            is InstantCircuitACSolutionDataEntry -> pl.amperages[0]
            is InstantCircuitAcdSolutionDataEntry -> pl.cnAmperages[0]
            else -> throw IllegalStateException("Неизвестный тип поезда")
        }
        val zr = findRailResistance()
        val rp = 25.0

        val csc = CurrentSpreadingComputer(rp, zr)
        var x = mpzBounds.first
        val step = L / 1000.0
        val result = mutableListOf<DoubleArray>()
        while (x < mpzBounds.second) {
            result.add(doubleArrayOf(x, csc.fi(x, x0, i, mpzBounds.first, mpzBounds.second).abs()))
            x += step
        }
        return result
    }

    private fun List<InstantCircuitSolutionDataEntry>.findPayload(): IPayloadSolution = this.find { it.isPayload() }!!

    private fun List<InstantCircuitSolutionDataEntry>.getMpzBoundaries(pl: IPayloadSolution): Pair<Double, Double> {
        val ssList = this.filter { it.objectName.contains("ЭЧЭ") || it.objectName.contains("SS") }
        val mpzSubstations = ssList.zipWithNext().find {
            pl.axisCoordinate() in it.first.coordinate..it.second.coordinate
        }!!
        return mpzSubstations.first.coordinate to mpzSubstations.second.coordinate
    }

    private fun List<DCNetworkDto>.findRailResistance(x: Double): Double {
        for (dto in this) {
            if (x <= dto.endSection) return dto.network.railsResistance!!
        }
        throw IllegalStateException("Не удалось вычислить сопротивление рельса")
    }

    private fun findRailResistance(): Complex {
        return Complex(0.182, 0.582) // todo: посчитать
    }

}