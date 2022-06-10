package ru.vniizht.asuterkortes.counter.tractive

import ru.vniizht.currentspreading.core.tractive.cInt
import ru.vniizht.currentspreading.core.tractive.getGeneratorResistance
import ru.vniizht.currentspreading.dao.Train
import kotlin.math.pow

/* Расчет массива скоростей  и координат, при которых должен быть выполнен переход в режим выбега */
fun getIdleArray(targetCoordinate: Double, targetSpeedLimit: Int,
                 currentSpeedLimit: Int, currentTrainCoordinate: Double,
                 profile: List<ProfileElement>,
                 idleResistance: DoubleArray,
                 totalWeight: Double,
                 train: Train,
                 timeSlot: Double
): List<RetardationElement> {
    var currentSpeed = targetSpeedLimit.toDouble()
    var currentCoordinate = targetCoordinate
    var currentElementNumber = getTargetElementNumber(profile, targetCoordinate)
    var wo_wc: Double
    var dV: Double
    var generatorResistance: Double
    val result: MutableList<RetardationElement> = mutableListOf(RetardationElement(currentSpeed, currentCoordinate))

    while (
        result.size < cInt(10 / timeSlot)
        && currentSpeed < currentSpeedLimit + 0.6
        && currentCoordinate > currentTrainCoordinate
        && currentSpeed >= 0.0
    ) {
        if (currentCoordinate <= profile[currentElementNumber].startCoordinate) {
            currentElementNumber--
        }
        wo_wc = getIdleWoWc(currentSpeed, idleResistance)
        generatorResistance = getGeneratorResistance(currentSpeed, train) / totalWeight
        dV = fi * (wo_wc + profile[currentElementNumber].wir + generatorResistance) * timeSlot
        currentCoordinate = currentCoordinate - ONE_MINUTE * (currentSpeed + dV / 2) * timeSlot
        currentSpeed = currentSpeed + dV

        if (currentCoordinate > targetCoordinate) {
            break
        }
        result.add(RetardationElement(currentSpeed, currentCoordinate))
        if (currentSpeed < 0) {
            while (currentCoordinate > currentTrainCoordinate) {
                currentCoordinate -= 0.1
                result.add(RetardationElement(currentSpeed, currentCoordinate))
            }
        }
    }

    return result
}

fun getTargetElementNumber(profile: List<ProfileElement>, targetCoordinate: Double): Int {
    for (i in profile.indices) {
        if (targetCoordinate >= profile[i].startCoordinate &&
            targetCoordinate < profile[i].startCoordinate + profile[i].length) {
            return i
        }
    }
    return profile.lastIndex
}

fun getIdleWoWc(currentSpeed: Double, idleResistance: DoubleArray) =
        idleResistance[0] + idleResistance[1] * currentSpeed + idleResistance[2] * currentSpeed.pow(2)

