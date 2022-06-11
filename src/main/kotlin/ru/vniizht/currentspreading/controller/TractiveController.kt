package ru.vniizht.currentspreading.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.vniizht.currentspreading.dto.CurrentSpreadingTractiveRequestDto
import ru.vniizht.currentspreading.dto.TractionCountGraphicDto
import ru.vniizht.currentspreading.service.SessionDataStorage
import ru.vniizht.currentspreading.service.TractiveService
import ru.vniizht.currentspreading.service.toGraphicDto

@RestController
@RequestMapping("/tractive")
class TractiveController(
    val tractiveService: TractiveService,
    val sessionDataStorage: SessionDataStorage
) {

    @GetMapping("/perform")
    fun performComputation(@RequestBody request: CurrentSpreadingTractiveRequestDto): List<TractionCountGraphicDto> {
        val tc = tractiveService.performTractiveComputation(request)
        sessionDataStorage.tc = tc
        sessionDataStorage.testNumber++
        return tc.result.elements.map { it.toGraphicDto() }
    }

}