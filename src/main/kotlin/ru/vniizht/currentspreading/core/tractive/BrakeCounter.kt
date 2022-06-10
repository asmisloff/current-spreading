package ru.vniizht.asuterkortes.counter.tractive

import ru.vniizht.currentspreading.core.tractive.cInt
import ru.vniizht.currentspreading.core.tractive.getGeneratorResistance
import ru.vniizht.currentspreading.dao.Train

/* Расчет массива скоростей  и координат, при которых должен быть выполнен переход в режим торможения */
fun getBrakeArray(targetCoordinate: Double, targetSpeedLimit: Int,
                  currentSpeedLimit: Int, currentTrainCoordinate: Double,
                  profile: List<ProfileElement>, brakeForce: Int, adhesionCoefficient: Double,
                  totalWeight: Double,
                  train: Train,
                  timeSlot: Double
): List<RetardationElement> {
    var currentSpeed = targetSpeedLimit.toDouble()
    var currentCoordinate = targetCoordinate
    var currentElementNumber = getTargetElementNumber(profile, targetCoordinate)
    var dV: Double
    val result: MutableList<RetardationElement> = mutableListOf(RetardationElement(currentSpeed, currentCoordinate))
    var generatorResistance: Double

    while (result.size < cInt(10 / timeSlot) &&
        currentSpeed < currentSpeedLimit && currentCoordinate > currentTrainCoordinate && currentSpeed >= 0.0
    ) {
        if (currentCoordinate <= profile[currentElementNumber].startCoordinate) {
            currentElementNumber--
        }
        generatorResistance = getGeneratorResistance(currentSpeed, train) / totalWeight
        dV =
            fi * (profile[currentElementNumber].wir + adhesionCoefficient * brakeForce + generatorResistance) * timeSlot
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

