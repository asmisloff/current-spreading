package ru.vniizht.currentspreading.controller

import org.springframework.web.bind.annotation.*
import ru.vniizht.currentspreading.dao.enums.LocomotiveCurrent
import ru.vniizht.currentspreading.service.LocomotiveChartData
import ru.vniizht.currentspreading.service.LocomotiveService

@RestController
@RequestMapping("/api/locomotive")
class LocomotiveController(
    val locomotiveService: LocomotiveService
) {

    @GetMapping("/chartData/{locomotiveCurrent}")
    fun getChartData(@PathVariable locomotiveCurrent: LocomotiveCurrent): LocomotiveChartData {
        return locomotiveService.getChartData(locomotiveCurrent)
    }

}