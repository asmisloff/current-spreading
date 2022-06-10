package ru.vniizht.currentspreading.service

import org.springframework.stereotype.Service
import ru.vniizht.currentspreading.dao.enums.LocomotiveCurrent
import ru.vniizht.currentspreading.repository.LocomotiveRepository

@Service
class LocomotiveService(
    private val locomotiveRepository: LocomotiveRepository
) {

    fun getChartData(locomotiveCurrent: LocomotiveCurrent): LocomotiveChartData {
        val forceOnSpeed: MutableMap<Double, MutableList<Double?>> = mutableMapOf()
        val motorAmperageOnSpeed: MutableMap<Double, MutableList<Double?>> = mutableMapOf()
        val activeAmperageOnSpeed: MutableMap<Double, MutableList<Double?>> = mutableMapOf()
        val positions = mutableListOf<String>()
        val locomotive = locomotiveRepository.findByCurrent(locomotiveCurrent)

        for ((iPos, pos) in locomotive.electricalCharacteristics.withIndex()) {
            positions.add(pos.name)
            for (ch in pos.characteristics) {
                forceOnSpeed
                    .getOrPut(ch.speed ?: 0.0) { MutableList(locomotive.electricalCharacteristics.size) { null } }
                    .set(iPos, ch.force)
                motorAmperageOnSpeed
                    .getOrPut(ch.speed ?: 0.0) { MutableList(locomotive.electricalCharacteristics.size) { null } }
                    .set(iPos, ch.motorAmperage)
                activeAmperageOnSpeed
                    .getOrPut(ch.speed ?: 0.0) { MutableList(locomotive.electricalCharacteristics.size) { null } }
                    .set(iPos, ch.activeCurrentAmperage)
            }
        }

        return LocomotiveChartData(
            positions = listOf("v") + positions,
            forceOnSpeed = forceOnSpeed.keys.sorted().map { speed -> listOf(speed) + forceOnSpeed[speed]!! },
            motorAmperageOnSpeed = motorAmperageOnSpeed.keys.sorted()
                .map { speed -> listOf(speed) + motorAmperageOnSpeed[speed]!! },
            activeAmperageOnSpeed = activeAmperageOnSpeed.keys.sorted()
                .map { speed -> listOf(speed) + activeAmperageOnSpeed[speed]!! }
        )
    }

}

data class LocomotiveChartData(
    val positions: List<String>,
    val forceOnSpeed: List<List<Double?>>,
    val motorAmperageOnSpeed: List<List<Double?>>,
    val activeAmperageOnSpeed: List<List<Double?>>
)