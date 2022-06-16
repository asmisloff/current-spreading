package ru.vniizht.currentspreading.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.vniizht.asuterkortes.dto.InstantCircuitDCSolutionDataEntry
import ru.vniizht.asuterkortes.dto.InstantCircuitSolutionDataEntry
import ru.vniizht.currentspreading.core.acnew.InstantCircuitAC
import ru.vniizht.currentspreading.core.dcnew.InstantCircuitDC
import ru.vniizht.currentspreading.core.schedule.TrainPosition
import ru.vniizht.currentspreading.service.ElectricalSchemaService
import ru.vniizht.currentspreading.service.SessionDataStorage

@RestController
@RequestMapping("/api/ic")
class InstantCircuitController(
    private val sessionDataStorage: SessionDataStorage,
    private val schemaService: ElectricalSchemaService
) {

    @GetMapping("/dc/solve")
    fun solveDcSchema(@RequestParam coord: Double, @RequestParam amp: Double): List<InstantCircuitDCSolutionDataEntry> {
        if (sessionDataStorage.icDc == null) {
            sessionDataStorage.icDc = InstantCircuitDC(schemaService.getAnyDcSchema())
        }
        val ic = sessionDataStorage.icDc!!
        ic.removePayloads()
        ic.addPayload(1, amp, coord)
        ic.build()
        ic.solve()
        return ic.getReport()
    }

    @Suppress("UNCHECKED_CAST")
    @GetMapping("/ac/solve")
    fun solveAcSchema(
        @RequestParam coord: Double,
        @RequestParam activeAmp: Double,
        @RequestParam fullAmp: Double
    ): List<InstantCircuitSolutionDataEntry> {
        if (sessionDataStorage.icAc == null) {
            sessionDataStorage.icAc = InstantCircuitAC.fromDto(schemaService.getAnyAcSchema())
        }
        val ic = sessionDataStorage.icAc!!
        ic.removePayloads()
        ic.addTrainPosition(TrainPosition(coord, activeAmp, 1, 1.0, 0, fullAmp), 2, null)
        ic.build()
        ic.solve()
        return ic.getSolution()
    }

}