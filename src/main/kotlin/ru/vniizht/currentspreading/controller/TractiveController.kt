package ru.vniizht.currentspreading.controller

import org.springframework.web.bind.annotation.*
import ru.vniizht.currentspreading.dto.CurrentSpreadingTractiveRequestDto
import ru.vniizht.currentspreading.dto.TractionCountGraphicDto
import ru.vniizht.currentspreading.service.SessionDataStorage
import ru.vniizht.currentspreading.service.TractiveService
import ru.vniizht.currentspreading.util.findIndexIntervalForKey

@RestController
@RequestMapping("api/tractive")
class TractiveController(
    val tractiveService: TractiveService,
    val sessionDataStorage: SessionDataStorage
) {

    @PostMapping("/perform")
    fun performComputation(@RequestBody request: CurrentSpreadingTractiveRequestDto): List<TractionCountGraphicDto> {
        val tcData = tractiveService.performTractiveComputation(request)
        sessionDataStorage.tcData = tcData
        return tcData
    }

    @GetMapping
    fun getAmperageByCoordinate(@RequestParam x: Double): Double {
        if (sessionDataStorage.tcData == null) return 0.0
        val index = sessionDataStorage.tcData!!
            .findIndexIntervalForKey(x, selector = { it.c })
            .first
        return sessionDataStorage.tcData!![index].a
    }

}