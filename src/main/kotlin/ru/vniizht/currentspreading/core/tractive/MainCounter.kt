package ru.vniizht.currentspreading.core.tractive

import ru.vniizht.asuterkortes.counter.tractive.*
import ru.vniizht.currentspreading.dao.Train
import ru.vniizht.currentspreading.dao.enums.LocomotiveCurrent
import ru.vniizht.currentspreading.dao.enums.LocomotiveType
import ru.vniizht.currentspreading.dao.enums.TrackType
import ru.vniizht.asuterkortes.dao.model.jsonb.MotorThermalCharacteristics
import ru.vniizht.currentspreading.core.schedule.OrderedList
import ru.vniizht.currentspreading.dao.Locomotive
import ru.vniizht.currentspreading.dto.RealVoltageDto
import ru.vniizht.currentspreading.util.round
import java.text.DecimalFormat
import kotlin.math.*

fun tractiveCount(
    forthProfile: List<ProfileElement>, // список элементов профиля пути
    forthSpeedLimits: List<SpeedLimit>, // список ограничений скорости
    forthStartCoordinate: Double, // координата начала расчета (первой станции)
    trackType: TrackType, // тип пути
    startSpeed: Double, // начальная скорость
    startPosition: Int, // начальная позиция
    tractivePositions: List<Position>, // список тяговых характеристик
    forthFinishCoordinate: Double, // координата конца расчета (конечной станции)
    speedZone: Int, // зона регулирования скорости
    amperageSelfConsumption: Double, // ток собственных нужд локомотива
    recuperation: Boolean, // флаг включенной рекуперации
    adhesionCoefficient: Double, // коэффициент сцепления
    recuperationPositions: List<Position>, // характеристики рекуперативного торможения
    locomotive: Locomotive, // тип локомотива (грузовой, пассажирский, пригородный)
    locomotiveCurrent: LocomotiveCurrent, // тип рабочего тока локомотива: пост. или перем
    reverseDirection: Boolean = false, // флаг четного направления
    idleOptimisation: Boolean = false, // флаг оптимизации выбега
    voltageArray: OrderedList<RealVoltageDto>, // список напряжений на участке
    train: Train, // характеристики состава
    stops: List<TractiveStop>, // список остановок
    timeSlot: Double, // шаг расчета
    tractionRateList: List<TractionRate>, // кол. локомотивов
    motorThermalCharacteristics: MotorThermalCharacteristics,
    initialOverheat: Double = 15.0
): List<Element> {
    val profile = if (reverseDirection) {
        getReverseProfile(forthProfile)
    } else {
        forthProfile
    }
    val cleanForthSpeedLimits: List<SpeedLimit> = validateAndCleanSpeedLimits(forthSpeedLimits)
    val speedLimits = if (reverseDirection) {
        getReverseSpeedLimits(getSpeedLimitsWithStops(cleanForthSpeedLimits, stops), forthFinishCoordinate)
    } else {
        getSpeedLimitsWithStops(cleanForthSpeedLimits, stops)
    }
    var realVoltage: Double
    var voltageCoefficient: Double
    val standardBrakeForce = locomotive.type.brakeForce
    var generatorResistance: Double
    var currentSpeed = startSpeed
    val dPoz = if (tractivePositions.lastIndex > 1) 1.0 else 0.1
    var currentPosition = startPosition.toDouble()
    var currentCoordinate = if (reverseDirection) -forthFinishCoordinate else forthStartCoordinate
    val finishCoordinate = if (reverseDirection) -forthStartCoordinate else forthFinishCoordinate
    var currentElementNumber = getCurrentElement(profile, currentCoordinate)
//    var deltaV: Double
    var dv: Double
    var fk: Double
    var currentSpeedLimitNumber = 0
    var currentSpeedLimit = getCurrentSpeedLimit(currentSpeedLimitNumber, speedLimits)
    var vc = 0
    var vt = 0
    var nextSpeedLimit = getNextSpeedLimit(currentSpeedLimitNumber, speedLimits)
    var regime = 1
    var wir: Double
    var wo_wc: Double
    var fu: Double
    val tractionRates = normalizeTractionRateList(tractionRateList, reverseDirection)
    var totalResistance = getTotalResistance(
        locomotive,
        train,
        trackType,
        tractionRateForCoordinate(tractionRates, currentCoordinate)
    )
    var totalWeight = getTotalWeight(train, locomotive, tractionRateForCoordinate(tractionRates, currentCoordinate))
    val result: MutableList<Element> = mutableListOf()
    var idleArray = if (idleOptimisation) {
        getIdleArray(
            nextSpeedLimit.coordinate,
            nextSpeedLimit.limit,
            currentSpeedLimit,
            currentCoordinate,
            profile,
            totalResistance.idleResistance,
            totalWeight,
            train,
            timeSlot
        )
    } else {
        emptyList()
    }
    var idleStep = if (idleOptimisation) idleArray.size - 1 else 0
    var brakeArray = getBrakeArray(
        nextSpeedLimit.coordinate,
        nextSpeedLimit.limit,
        currentSpeedLimit,
        currentCoordinate,
        profile,
        standardBrakeForce,
        adhesionCoefficient,
        totalWeight,
        train,
        timeSlot
    )
    var brakeStep = brakeArray.size - 1
    var motorAmperage: Double
    var activeAmperage: Double
    var fullAmperage: Double?
    var fx: Double
    var fc: Double
    var ie: Double?
    var iLock: Double?
    var iLockA: Double
    var iLocc: Double?
    var iLoccA: Double
    var iEng: Double
    var ieAct: Double
    var tormOrp: Boolean

    while (currentCoordinate < finishCoordinate) {
        totalResistance = getTotalResistance(
            locomotive,
            train,
            trackType,
            tractionRateForCoordinate(tractionRates, currentCoordinate)
        )
        totalWeight =
            getTotalWeight(train, locomotive, tractionRateForCoordinate(tractionRates, currentCoordinate))

        tormOrp = false
        realVoltage = getVoltageByCoordinate(voltageArray, currentCoordinate)
        voltageCoefficient = realVoltage / locomotiveCurrent.nominal

        //1
        if (currentElementNumber != getCurrentElement(profile, currentCoordinate)) {
            currentElementNumber = getCurrentElement(profile, currentCoordinate)
        }
        if (!(currentSpeedLimit == 0 && currentSpeed > 0)) {
            if (currentCoordinate >= nextSpeedLimit.coordinate) {
                currentSpeedLimitNumber++
                currentSpeedLimit = nextSpeedLimit.limit
//                val currentI = profile[currentElementNumber].i
//                vc = getNewVc(currentSpeedLimit, currentI, speedZone)
//                vt = getNewVt(currentSpeedLimit, currentI, speedZone)
                nextSpeedLimit = getNextSpeedLimit(currentSpeedLimitNumber, speedLimits)
                idleArray = if (idleOptimisation) {
                    getIdleArray(
                        nextSpeedLimit.coordinate,
                        nextSpeedLimit.limit,
                        currentSpeedLimit,
                        currentCoordinate,
                        profile,
                        totalResistance.idleResistance,
                        totalWeight,
                        train,
                        timeSlot
                    )
                } else {
                    emptyList()
                }
                idleStep = if (idleOptimisation) idleArray.size - 1 else 0
                brakeArray = getBrakeArray(
                    nextSpeedLimit.coordinate,
                    nextSpeedLimit.limit,
                    currentSpeedLimit,
                    currentCoordinate,
                    profile,
                    standardBrakeForce,
                    adhesionCoefficient,
                    totalWeight,
                    train,
                    timeSlot
                )
                brakeStep = brakeArray.size - 1
            }
        }
        if (currentSpeed < 0) {
            currentSpeed = 0.0
        }

        if (currentSpeed >= currentSpeedLimit + 0.6) {
            regime = -1
            currentPosition = 0.0
        } else {
            val currentI = profile[currentElementNumber].i
            vc = getNewVc(currentSpeedLimit, currentI, speedZone)

            vt = getNewVt(currentSpeedLimit, currentI, speedZone)

            if (currentSpeed < vt) {

                if (regime <= 0) {
                    if (currentSpeed > 50) {
                        currentPosition = 0.43 * tractivePositions.lastIndex
                    } else if (currentSpeed > 25) {
                        currentPosition = 0.73 * tractivePositions.lastIndex
                    }
                    if (tractivePositions.lastIndex > 1) {
                        currentPosition = cInt(currentPosition).toDouble()
                    }
                    currentPosition -= dPoz
                }
                regime = 1
                currentPosition += dPoz
            } else if (currentSpeed >= vc && regime >= 0) {
                regime = 1
                currentPosition -= dPoz
            }

            if (regime > 0 && currentPosition < 0.001) {
                regime = 0
            }

            if (currentPosition > tractivePositions.lastIndex) {
                currentPosition = tractivePositions.lastIndex.toDouble()
            }
            if (currentPosition < 0) {
                currentPosition = 0.0
                regime = 0
            }
            if (regime < 1) {
                currentPosition = 0.0
            }

// 2 проблема в том, что мы считаем массивы торможения и выбега справа налево, а поезд идет наоборот.
            while (idleStep > 0 && currentCoordinate > idleArray[idleStep].coordinate && currentSpeed < idleArray[idleStep].speed) {
                idleStep--
            }
            if (idleStep > 0
                && currentCoordinate >= idleArray[idleStep - 1].coordinate
                && currentSpeed >= idleArray[idleStep].speed
                && currentSpeed > nextSpeedLimit.limit
            ) {
                regime = 0
                currentPosition = 0.0
                vt = -1
                idleStep--
            }

            if (currentSpeed >= currentSpeedLimit + 0.6) {
                regime = -1
                currentPosition = 0.0
            }
            if (regime < 0 && currentSpeed < currentSpeedLimit - 10) {
                regime = 0
            }

            while (brakeStep > 0 && currentCoordinate > brakeArray[brakeStep].coordinate && currentSpeed < brakeArray[brakeStep].speed) {
                brakeStep--
            }
            if (brakeStep > 0
                && currentCoordinate >= brakeArray[brakeStep - 1].coordinate
                && currentSpeed >= brakeArray[brakeStep].speed
                && currentSpeed > nextSpeedLimit.limit
            ) {
                regime = -1
                currentPosition = 0.0
                vt = -1
                brakeStep--
                tormOrp = true
            }
        }

        //3
        val speed: Double
        if (currentSpeed <= 0) {
            regime = 1
            speed = 0.0
        } else {
            speed = currentSpeed
        }
        generatorResistance = getGeneratorResistance(speed, train) / totalWeight
        if (regime > 0 && !tormOrp) {
            if (tractivePositions.size < 3) {

                var dp1 = 0.005 //GoSub Compute_Tuner
                wo_wc = getMotoringWoWc(
                    speed,
                    totalResistance.motoringResistance
                )
                wir = profile[currentElementNumber].wir
                do {
                    val fk1 =
                        getFktp(tractivePositions, speed, currentPosition, adhesionCoefficient, voltageCoefficient).fktu
                    val fu1 = getFu(
                        fk1,
                        totalWeight,
                        wo_wc,
                        wir,
                        regime,
                        standardBrakeForce,
                        adhesionCoefficient,
                        generatorResistance = generatorResistance,
                        mLocs = tractionRateForCoordinate(tractionRates, currentCoordinate)
                    )
                    val dvc = getDv(fu1, timeSlot)
                    val vk0 = currentSpeed + dvc
                    if (vk0 < vt) {
                        currentPosition += dp1
                        if (currentPosition > 1) {
                            currentPosition = 1.0
                            break
                        }
                    } else if (vk0 > vc) {
                        currentPosition -= dp1
                        if (currentPosition < 0) {
                            currentPosition = 0.0
                            break
                        }
                    }
                    dp1 *= 0.8
                    if (dp1 < 0.001) {
                        dp1 = 0.001
                    }
                } while (!(vt <= vk0 && vk0 <= vc)) // end GoSub Compute_Tuner

                if (currentPosition < 0.001) {
                    fx = 0.0
                    ie = 0.0
                    iEng = 0.0
                    ieAct = 0.0
                    currentPosition = 0.0
                    regime = 0
                } else {
                    val forces =
                        getFktp(tractivePositions, speed, currentPosition, adhesionCoefficient, voltageCoefficient)
                    fx = forces.fktu
                    iLock = forces.iLoc
                    iLockA = forces.iLocA
                    ie = iLock
                    iEng = forces.iEng
                    ieAct = iLockA

//                    val forcesC = getFktu(positions, speed, 0.0, adhesionCoefficient)
//                    fc = forcesC.fktu
//                    iLocc = forcesC.iLoc
//                    iLoccA = forcesC.iLocA
//
//                    if (fx > fc) { // была ошибка из-за превышения ограничивающей, возможно после Compute_Tuner нужно будет удалить
//                        fx = fc
//                        ie = iLocc
//                        ieAct = iLoccA
//                    }

                }
            } else { // positions.size > 2
                val forcesC = getFktu(tractivePositions, speed, 0.0, adhesionCoefficient, voltageCoefficient, 1.0)
                fc = forcesC.fktu
                iLocc = forcesC.iLoc
                iLoccA = forcesC.iLocA
                val forcesK =
                    getFktu(tractivePositions, speed, currentPosition, adhesionCoefficient, voltageCoefficient, 1.0)
                fk = forcesK.fktu
                iLock = forcesK.iLoc
                iLockA = forcesK.iLocA
                if (fk > fc) {
                    fx = fc
                    ie = iLocc
                    iEng = forcesC.iEng
                    ieAct = iLoccA
                    currentPosition -= dPoz
                } else {
                    fx = fk
                    ie = iLock
                    iEng = forcesK.iEng
                    ieAct = iLockA
                }
            }

            wo_wc = getMotoringWoWc(
                speed,
                totalResistance.motoringResistance
            )
            wir = profile[currentElementNumber].wir
            fu = getFu(
                fx,
                totalWeight,
                wo_wc,
                wir,
                regime,
                standardBrakeForce,
                adhesionCoefficient,
                generatorResistance = generatorResistance,
                mLocs = tractionRateForCoordinate(tractionRates, currentCoordinate)
            )
            fullAmperage = when (locomotiveCurrent) {
                LocomotiveCurrent.DIRECT_CURRENT -> null
                LocomotiveCurrent.ALTERNATING_CURRENT -> ie?.plus(amperageSelfConsumption)
            }
            activeAmperage = ieAct + amperageSelfConsumption
            motorAmperage = iEng

        } else if (regime == 0 && !tormOrp) {
            wo_wc = getIdleWoWc(
                speed,
                totalResistance.idleResistance
            )
            wir = profile[currentElementNumber].wir
            fu = getFu(
                0.0,
                totalWeight,
                wo_wc,
                wir,
                regime,
                standardBrakeForce,
                adhesionCoefficient,
                generatorResistance = generatorResistance,
                mLocs = tractionRateForCoordinate(tractionRates, currentCoordinate)
            )
            activeAmperage = amperageSelfConsumption
            motorAmperage = 0.0
            fullAmperage = when (locomotiveCurrent) {
                LocomotiveCurrent.DIRECT_CURRENT -> null
                LocomotiveCurrent.ALTERNATING_CURRENT -> amperageSelfConsumption
            }
        } else {
            wir = profile[currentElementNumber].wir
            val currentBrakeForce = if (recuperation && currentSpeed < currentSpeedLimit) {
                val maxBrakeForces =
                    getDiscreteCurrentForces(recuperationPositions, 1, speed)
                val limitBrakeForces =
                    getDiscreteCurrentForces(recuperationPositions, 0, speed)
                if (maxBrakeForces.fvn > limitBrakeForces.fvn) {
                    getFv(limitBrakeForces, speed)
                } else {
                    getFv(maxBrakeForces, speed)
                }
            } else {
                0.0
            }
            wo_wc = getIdleWoWc(
                speed,
                totalResistance.idleResistance
            )
            fu = getFu(
                0.0,
                totalWeight,
                wo_wc,
                wir,
                regime,
                standardBrakeForce,
                adhesionCoefficient,
                currentBrakeForce,
                generatorResistance,
                tractionRateForCoordinate(tractionRates, currentCoordinate)
            )
            motorAmperage =
                if (recuperation && recuperationPositions.isNotEmpty() && currentSpeed < currentSpeedLimit) {
                    val rows = getDiscreteCurrentForces(
                        recuperationPositions,
                        1,
                        currentSpeed
                    )
                    linterp(rows.vn, rows.vn1, rows.imn, rows.imn1, speed)
                } else {
                    0.0
                }
            activeAmperage =
                if (recuperation && recuperationPositions.isNotEmpty() && currentSpeed < currentSpeedLimit) {
                    val i = getIv(
                        getDiscreteCurrentForces(
                            recuperationPositions,
                            1,
                            currentSpeed
                        ),
                        speed
                    )
                    amperageSelfConsumption - i
                } else {
                    amperageSelfConsumption
                }
            fullAmperage = when (locomotiveCurrent) {
                LocomotiveCurrent.DIRECT_CURRENT -> null
                LocomotiveCurrent.ALTERNATING_CURRENT -> if (recuperation && recuperationPositions.isNotEmpty() && currentSpeed < currentSpeedLimit) {
                    amperageSelfConsumption - getIvf(
                        getDiscreteCurrentForces(
                            recuperationPositions,
                            1,
                            currentSpeed
                        ),
                        speed
                    )
                } else {
                    amperageSelfConsumption
                }
            }
        }

        //4
        dv = getDv(fu, timeSlot)
        if (currentCoordinate <= currentCoordinate + ONE_MINUTE * (currentSpeed + dv / 2) * timeSlot) {
            currentCoordinate += ONE_MINUTE * (currentSpeed + dv / 2) * timeSlot
        }
        currentSpeed += dv

        //region Расчёт температуры нагрева обмоток ТЭД
        var temperature = 0.0
        if (motorThermalCharacteristics.characteristics.isNotEmpty()) {
            val mm = when {
                (regime > 0 && !tormOrp) -> MotionMode.ACCELERATION
                (regime == 0 && !tormOrp) -> MotionMode.RETARDATION
                (currentSpeedLimit <= 0 && currentSpeed <= 0) -> MotionMode.STOP
                else -> when (recuperation) {
                    true -> MotionMode.RECUPERATIVE_BREAKING
                    else -> MotionMode.BREAKING
                }
            }

            val duration = when (mm) {
                MotionMode.STOP -> {
                    val stopTime = stops
                        .find { it.coordinate == abs(speedLimits[currentSpeedLimitNumber].coordinate) }
                        ?.time?.toDouble() ?: 0.0
                    stopTime
                }
                else -> timeSlot
            }

            val prevStepOverheat = if (result.isEmpty()) initialOverheat else result.last().temperature

            temperature =
                computeTemperature(
                    mm,
                    prevStepOverheat,
                    abs(motorAmperage),
                    duration,
                    motorThermalCharacteristics
                )
        }
        //endregion

        //region Масштабирование элементарных токов на количество локомотивов
        activeAmperage *= tractionRateForCoordinate(tractionRates, currentCoordinate)
        fullAmperage = fullAmperage?.times(tractionRateForCoordinate(tractionRates, currentCoordinate))
        check(fullAmperage == null || fullAmperage >= activeAmperage) {
            "В ходе тягового расчета получен активный ток, больший полного тока. Проверьте характеристики локомотива."
        }
        //endregion

        result.add(
            Element(
                force = fu,
                activeAmperage = activeAmperage.round(3),
                fullAmperage = fullAmperage?.round(3),
                dv = dv,
                speed = currentSpeed.round(3),
                coordinate = abs(currentCoordinate).round(3),
                regime = regime,
                position = currentPosition,
                speedLimit = currentSpeedLimit,
                temperature = temperature,
                motorAmperage = motorAmperage
            )
        )
//        println(Element(force = fu, activeAmperage = activeAmperage, fullAmperage = fullAmperage, dv = dv, speed = currentSpeed, coordinate = currentCoordinate, regime = regime,
//                position = currentPosition, speedLimit = currentSpeedLimit, wir = wir, fv = fx, wo_wc = wo_wc, vt = vt, vc = vc))

        if (currentSpeedLimit > 0.1 && currentSpeed < 0) {
            println("STOP!")
            throw IllegalStateException("Для продолжения движения недостаточно мощности локомотива ($currentCoordinate км)")
        }
    }

//    return if (reverseDirection) {
//        result.reversed()
//    } else {
//        result
//    }

    return result
}

fun normalizeTractionRateList(tractionRateList: List<TractionRate>, reverseDirection: Boolean): List<TractionRate> {
    val result: List<TractionRate> =
        if (reverseDirection) tractionRateList.map { TractionRate(-it.coordinate, it.rate) }
        else tractionRateList
    return result.sortedBy { it.coordinate }
}

fun validateAndCleanSpeedLimits(forthSpeedLimits: List<SpeedLimit>): List<SpeedLimit> {
    require((forthSpeedLimits.count { it.limit <= 0 }) == 0) {
        "Невозможно выполнить расчет: на участке заданы нулевые или отрицательные скоростные ограничения."
    }
    val result = mutableListOf<SpeedLimit>()
    var prevLimit = 0
    forthSpeedLimits.forEach { limit ->
        if (limit.limit != prevLimit) {
            result.add(limit)
            prevLimit = limit.limit
        }
    }
    return result
}

fun getNewVc(currentSpeedLimit: Int, currentI: Double, speedZone: Int): Int {
    return if (speedZone != 0) {
        currentSpeedLimit - 1
    } else {
        if (currentI > 2) {
            currentSpeedLimit - 2
        } else if (currentI < -2) {
            currentSpeedLimit - 15
        } else {
            currentSpeedLimit - 5
        }
    }
}

fun getNewVt(currentSpeedLimit: Int, currentI: Double, speedZone: Int): Int {
    return if (speedZone != 0) {
        currentSpeedLimit - speedZone - 1
    } else {
        if (currentI > 2) {
            currentSpeedLimit - 5
        } else if (currentI < -2) {
            currentSpeedLimit - 15 - 20
        } else {
            currentSpeedLimit - 5 - 10
        }
    }
}

fun cInt(d: Double): Int {
    if (d - d.toInt() == 0.5 && d.toInt() % 2 == 0) return d.toInt()
    return d.roundToInt()
}

fun getSpeedLimitsWithStops(forthSpeedLimits: List<SpeedLimit>, stops: List<TractiveStop>): List<SpeedLimit> {
    if (stops.isEmpty()) {
        return forthSpeedLimits
    }
    var j = stops.lastIndex
    val result = mutableListOf<SpeedLimit>()
    for (i in forthSpeedLimits.lastIndex downTo 0) {
        while (j >= 0 && stops[j].coordinate > forthSpeedLimits[i].coordinate) {
            result.add(SpeedLimit(stops[j].coordinate, forthSpeedLimits[i].limit))
            result.add(SpeedLimit(stops[j].coordinate, 0))
            j--
        }
        if (j >= 0 && stops[j].coordinate == forthSpeedLimits[i].coordinate) {
            result.add(SpeedLimit(stops[j].coordinate, forthSpeedLimits[i].limit))
            result.add(SpeedLimit(stops[j].coordinate, 0))
            j--
        } else {
            result.add(SpeedLimit(forthSpeedLimits[i].coordinate, forthSpeedLimits[i].limit))
        }
    }
    return result.reversed()
}

fun getGeneratorResistance(speed: Double, train: Train): Double {
    return if (speed < 35 || train.undercarGeneratorPower == null || train.undercarGeneratorPower == 0.0) {
        0.0
    } else {
        (1330 * train.undercarGeneratorPower!!) /
                ((train.weight / (train.carsToTrain.sumOf { it.count * it.car!!.numberOfAxles.number })) * speed)
    }
}

fun getVoltageByCoordinate(voltages: List<RealVoltageDto>, currentCoordinate: Double): Double {
    for (i in voltages.indices) {
        if (abs(currentCoordinate) <= voltages[i].coordinate) {
            return voltages[i].voltage
        }
    }
    return voltages[0].voltage
}

fun getReverseSpeedLimits(speedLimits: List<SpeedLimit>, lastCoordinate: Double): List<SpeedLimit> {
    val newSpeedLimits = mutableListOf<SpeedLimit>()
    newSpeedLimits.add(SpeedLimit(-lastCoordinate, speedLimits[speedLimits.lastIndex].limit))
    for (i in speedLimits.lastIndex downTo 1) {
        newSpeedLimits.add(SpeedLimit(-speedLimits[i].coordinate, speedLimits[i - 1].limit))
    }
    return newSpeedLimits
}

fun getReverseProfile(profile: List<ProfileElement>): List<ProfileElement> {
    val newProfile = mutableListOf<ProfileElement>()
    for (i in profile.lastIndex downTo 0) {
        newProfile.add(
            ProfileElement(
                startCoordinate = -profile[i].startCoordinate - profile[i].length,
                length = profile[i].length,
                i = -profile[i].i,
                ikr = profile[i].ikr
            )
        )
    }
    return newProfile
}

private fun getCurrentDeltaSpeed(locomotiveType: LocomotiveType, currentI: Double, currentDeltaV: Int): Double {
    if (currentDeltaV <= 0) {
        return if (locomotiveType == LocomotiveType.FREIGHT_LOCOMOTIVE) { // PTR
            if (currentI > -4) {
                0.0
            } else if (currentI > -6) {
                4.0
            } else if (currentI > -8) {
                4.0
            } else if (currentI > -10) {
                4.0
            } else if (currentI > -12) {
                4.0
            } else if (currentI > -14) {
                5.0
            } else if (currentI > -16) {
                6.0
            } else if (currentI > -18) {
                7.0
            } else {
                8.0
            }
        } else if (locomotiveType == LocomotiveType.PASSENGER_LOCOMOTIVE) {
            if (currentI > -4) {
                0.0
            } else if (currentI > -6) {
                2.0
            } else if (currentI > -8) {
                2.0
            } else if (currentI > -10) {
                3.0
            } else if (currentI > -12) {
                4.0
            } else if (currentI > -14) {
                6.0
            } else if (currentI > -16) {
                7.0
            } else if (currentI > -18) {
                8.0
            } else {
                9.0
            }
        } else {
            0.0
        }
    } else {
        return if (currentI > -4) 0.0 else currentDeltaV.toDouble() //для похожести на КОРТЭС
    }
}

fun getFv(currentPositionCharacteristics: PositionCharacteristics, vx: Double) =
    with(currentPositionCharacteristics) {
        fvn + ((fvn1 - fvn) / (vn1 - vn)) * (vx - vn)
    }

/**
 * Активный ток локомотива - интерполяция по позиционной характеристике
 * @param vx текущая скорость
 */
fun getIv(currentPositionCharacteristics: PositionCharacteristics, vx: Double) =
    with(currentPositionCharacteristics) {
        ivn + ((ivn1 - ivn) / (vn1 - vn)) * (vx - vn)
    }

/**
 * Полный ток локомотива - интерполяция по позиционной характеристике
 * @param vx текущая скорость
 */
fun getIvf(currentPositionCharacteristics: PositionCharacteristics, vx: Double) =
    with(currentPositionCharacteristics) {
        ivfn!! + ((ivfn1!! - ivfn) / (vn1 - vn)) * (vx - vn)
    }

/**
 * Расчет основного удельного сопр. движению локомотива
 * Массив Wotrn в терминах Кортеса
 */
fun getMotoringWoWc(currentSpeed: Double, motoringResistance: DoubleArray) =
    motoringResistance[0] + motoringResistance[1] * currentSpeed + motoringResistance[2] * currentSpeed.pow(2)

/**
 * Вычислить удельную силу
 */
fun getFu(
    fv: Double, weight: Double, wo_wc: Double, wir: Double, regime: Int, standardBrakeForce: Int,
    adhesionCoefficient: Double, brakeForce: Double = 0.0, generatorResistance: Double, mLocs: Double
) =
    if (regime > 0)
        adhesionCoefficient * (1000.0 * fv * mLocs / weight) - wo_wc - wir - generatorResistance
    else if (regime == 0) {
        -wo_wc - wir - generatorResistance
    } else {
        if (brakeForce > 0.1) { //TODO описать что происходит
            max(
                min(-wo_wc - wir - adhesionCoefficient * (1000.0 * brakeForce / weight) - generatorResistance, 0.0),
                -wir - adhesionCoefficient * standardBrakeForce - generatorResistance
            )
        } else {
            -wir - adhesionCoefficient * standardBrakeForce - generatorResistance
        }
    }

fun getDv(fu: Double, timeSlot: Double) = fu * fi * timeSlot

fun getNextSpeedLimit(currentSpeedLimitNumber: Int, speedLimits: List<SpeedLimit>): SpeedLimit {
    return if (currentSpeedLimitNumber < speedLimits.size - 1) {
        speedLimits[currentSpeedLimitNumber + 1]
    } else {
        SpeedLimit(9999999.0, 9999)
    }
}

/**
 * Сила и токи локомотива, соответствующие данной позиции и скорости
 */
fun getFktu(
    positions: List<Position>,
    v: Double,
    poz: Double,
    rkpsi: Double, // коэф. сцепления
    kUCn: Double,
    kUEng: Double
): Fktu {
    val npz: Int
    val kvoi: Double
    val kvo: Double
    val kpsi1: Double
    var nx: Int
    if (poz < 0.001) {
        npz = 0
        kvo = 1.0
        kpsi1 = rkpsi
        nx = positions[0].characteristics.lastIndex
        if (v >= positions[0].characteristics.last().speed * kvo) {
            return Fktu(
                fktu = positions[0].characteristics.last().force,
                iEng = positions[0].characteristics.last().motorAmperage,
                iLoc = positions[0].characteristics.last().fullAmperage,
                iLocA = positions[0].characteristics.last().activeAmperage
            )
        }
    } else {
        npz = (poz - 0.01).toInt() + 1
        kvoi = poz / npz
        kvo = kvoi * kUCn * kUEng
        kpsi1 = 1.0
        nx = positions[npz].characteristics.lastIndex
        val last = positions[npz].characteristics.last()
        val v1 = last.speed * kvo
        if (v >= v1) {
            val v0 = v1 / v
            var iEng = positions[npz].characteristics.last().motorAmperage * v0
            if (positions.size == 2 &&
                positions[npz].characteristics[0].motorAmperage == positions[npz].characteristics[1].motorAmperage // так в Кортэсе
            ) {
                iEng *= kvoi
            }
            positions[npz].characteristics.last().motorAmperage * v0
            return Fktu(
                fktu = positions[npz].characteristics.last().force * v0 * v0,
                iEng = iEng,
                iLoc = positions[npz].characteristics.last().fullAmperage?.times(v0 * kvoi),
                iLocA = positions[npz].characteristics.last().activeAmperage * v0 * kvoi
            )
        }
    }
    positions[npz].characteristics.let {
        while (v < it[nx].speed * kvo && nx > 0.01) {
            nx -= 1
        }
        val v0 = it[nx].speed * kvo
        val v1 = it[nx + 1].speed * kvo
        val dvo = (v - v0) / (v1 - v0)
        val iEng = linterp(v0, v1, it[nx].motorAmperage, it[nx + 1].motorAmperage, v) * kpsi1
        return Fktu(
            fktu = (it[nx].force + (it[nx + 1].force - it[nx].force) * dvo) * kpsi1,
            iEng = iEng,
            iLoc = ((it[nx].fullAmperage?.let { it1 -> it[nx + 1].fullAmperage?.minus(it1) })?.times(dvo)
                ?.let { it1 -> it[nx].fullAmperage?.plus(it1) })?.times(kpsi1),
            iLocA = (it[nx].activeAmperage + (it[nx + 1].activeAmperage - it[nx].activeAmperage) * dvo) * kpsi1
        )
    }
}

/**
 * Расчет тяги при плавном регулировании скорости
 */
fun getFktp(
    positions: List<Position>,
    v: Double,
    poz: Double,
    adhesionCoefficient: Double,
    kUCn: Double
): Fktu {
    val fc1 = getFktu(positions, v, 0.0, adhesionCoefficient, kUCn, poz)
    val fk1 = getFktu(positions, v, 1.0, adhesionCoefficient, kUCn, poz)

    return if (fk1.fktu > fc1.fktu) {
        Fktu(
            fktu = fc1.fktu * poz,
            iEng = fc1.iEng * poz,
            iLoc = fc1.iLoc?.times(poz),
            iLocA = fc1.iLocA * poz
        )
    } else {
        getFktu(positions, v, poz, adhesionCoefficient, kUCn, poz)
    }
}

fun getDiscreteCurrentForces(
    positions: List<Position>,
    currentPosition: Int,
    currentSpeed: Double
): PositionCharacteristics {
    if (currentPosition > 0) {
        if (currentSpeed
            /** voltageCoefficient*/
            >= positions[currentPosition].characteristics.last().speed
        ) {
            val speedCoef = positions[currentPosition].characteristics.last().speed / currentSpeed
            return PositionCharacteristics(
                fvn = positions[currentPosition].characteristics.last().force * speedCoef * speedCoef,
                fvn1 = positions[currentPosition].characteristics.last().force * speedCoef * speedCoef,
                imn = positions[currentPosition].characteristics.last().motorAmperage * speedCoef,
                imn1 = positions[currentPosition].characteristics.last().motorAmperage * speedCoef,
                ivn = positions[currentPosition].characteristics.last().activeAmperage * speedCoef,
                ivn1 = positions[currentPosition].characteristics.last().activeAmperage * speedCoef,
                ivfn = positions[currentPosition].characteristics.last().fullAmperage?.times(speedCoef),
                ivfn1 = positions[currentPosition].characteristics.last().fullAmperage?.times(speedCoef),
                vn = positions[currentPosition].characteristics.last().speed,
                vn1 = currentSpeed
            )
        } else {
            for (i in positions[currentPosition].characteristics.size - 2 downTo 0) {
                if (currentSpeed
                    /** voltageCoefficient*/
                    >= positions[currentPosition].characteristics[i].speed
                ) {
                    return PositionCharacteristics(
                        fvn = positions[currentPosition].characteristics[i].force,
                        fvn1 = positions[currentPosition].characteristics[i + 1].force,
                        imn = positions[currentPosition].characteristics[i].motorAmperage,
                        imn1 = positions[currentPosition].characteristics[i + 1].motorAmperage,
                        ivn = positions[currentPosition].characteristics[i].activeAmperage,
                        ivn1 = positions[currentPosition].characteristics[i + 1].activeAmperage,
                        ivfn = positions[currentPosition].characteristics[i].fullAmperage,
                        ivfn1 = positions[currentPosition].characteristics[i + 1].fullAmperage,
                        vn = positions[currentPosition].characteristics[i].speed,
                        vn1 = positions[currentPosition].characteristics[i + 1].speed
                    )
                }
            }
            return PositionCharacteristics(
                fvn = positions[currentPosition].characteristics[0].force,
                fvn1 = positions[currentPosition].characteristics[0 + 1].force,
                imn = positions[currentPosition].characteristics[0].motorAmperage,
                imn1 = positions[currentPosition].characteristics[0 + 1].motorAmperage,
                ivn = positions[currentPosition].characteristics[0].activeAmperage,
                ivn1 = positions[currentPosition].characteristics[0 + 1].activeAmperage,
                ivfn = positions[currentPosition].characteristics[0].fullAmperage,
                ivfn1 = positions[currentPosition].characteristics[0 + 1].fullAmperage,
                vn = positions[currentPosition].characteristics[0].speed,
                vn1 = positions[currentPosition].characteristics[0 + 1].speed
            )
        }
    } else {
        if (currentSpeed
            /** voltageCoefficient*/
            >= positions[0].characteristics.last().speed
        ) {
            return PositionCharacteristics(
                fvn = positions[0].characteristics.last().force,
                fvn1 = positions[0].characteristics.last().force,
                imn = positions[0].characteristics.last().motorAmperage,
                imn1 = positions[0].characteristics.last().motorAmperage,
                ivn = positions[0].characteristics.last().activeAmperage,
                ivn1 = positions[0].characteristics.last().activeAmperage,
                ivfn = positions[0].characteristics.last().fullAmperage,
                ivfn1 = positions[0].characteristics.last().fullAmperage,
                vn = positions[0].characteristics.last().speed,
                vn1 = currentSpeed
            )
        } else {
            for (i in positions[0].characteristics.size - 2 downTo 0) {
                if (currentSpeed /** voltageCoefficient*/ >= positions[0].characteristics[i].speed) {
                    return PositionCharacteristics(
                        fvn = positions[0].characteristics[i].force,
                        fvn1 = positions[0].characteristics[i + 1].force,
                        imn = positions[0].characteristics[i].motorAmperage,
                        imn1 = positions[0].characteristics[i + 1].motorAmperage,
                        ivn = positions[0].characteristics[i].activeAmperage,
                        ivn1 = positions[0].characteristics[i + 1].activeAmperage,
                        ivfn = positions[0].characteristics[i].fullAmperage,
                        ivfn1 = positions[0].characteristics[i + 1].fullAmperage,
                        vn = positions[0].characteristics[i].speed,
                        vn1 = positions[0].characteristics[i + 1].speed
                    )
                }
            }
            val maxCharact = positions[0].characteristics.lastIndex
            return PositionCharacteristics(
                fvn = positions[0].characteristics[maxCharact].force,
                fvn1 = positions[0].characteristics[maxCharact].force,
                imn = positions[0].characteristics[maxCharact].motorAmperage,
                imn1 = positions[0].characteristics[maxCharact].motorAmperage,
                ivn = positions[0].characteristics[maxCharact].activeAmperage,
                ivn1 = positions[0].characteristics[maxCharact].activeAmperage,
                ivfn = positions[0].characteristics[maxCharact].fullAmperage,
                ivfn1 = positions[0].characteristics[maxCharact].fullAmperage,
                vn = positions[0].characteristics[maxCharact].speed,
                vn1 = currentSpeed
            )
        }
    }
}

fun getCurrentSpeedLimit(currentSpeedLimitNumber: Int, speedLimits: List<SpeedLimit>): Int {
    return speedLimits[currentSpeedLimitNumber].limit
}

fun getCurrentElement(profile: List<ProfileElement>, currentCoordinate: Double): Int {
    for (i in profile.indices) {
        if (currentCoordinate < profile[i].startCoordinate + profile[i].length) {
            return i
        }
    }
    throw IllegalStateException("Current coordinate $currentCoordinate is outside profile")
}

data class PositionCharacteristics(
    val fvn: Double,
    val fvn1: Double,
    val imn: Double,
    val imn1: Double,
    val ivn: Double,
    val ivn1: Double,
    val ivfn: Double?,
    val ivfn1: Double?,
    val vn: Double,
    val vn1: Double
)

data class Fktu(
    val fktu: Double, // Сила тяги на ободе колеса
    val iEng: Double, // Ток двигателя
    val iLoc: Double?, // Ток локомотива Id
    val iLocA: Double // Ток локомотива Ida
)

data class Element(
    val coordinate: Double,
    val speed: Double,
    val activeAmperage: Double,
    val fullAmperage: Double? = null,
    val regime: Int, //TODO убрать лишнее
    val dv: Double,
    val force: Double,
    val position: Double,
    val speedLimit: Int,
    val temperature: Double,
    val motorAmperage: Double,
    val wir: Double? = null,
    val fv: Double? = null,
    val wo_wc: Double? = null,
    val vt: Int? = null,
    val vc: Int? = null
) {
    override fun toString(): String {
        val df = DecimalFormat("0000.000")
        return "${df.format(force)}\t${df.format(activeAmperage)}\t${df.format(fullAmperage)}\t${df.format(dv)}\t" +
                "${df.format(speed)}\t${df.format(coordinate)}\t${regime}\t${df.format(position)}\t${speedLimit}\t" //+
//                "${df.format(wir)}\t${df.format(fv)}\t${df.format(wo_wc)}\t${vt}\t${vc}"
    }
}

fun averaging(elements: List<Element>, averagingPeriod: Double, timeSlot: Double): List<AverageElement> {
    val count: Int = (averagingPeriod / timeSlot).roundToInt()
    var commutator: AverageElement
    var counter: Int
    val result = mutableListOf<AverageElement>()

    for (i in elements.indices step count) {
        counter = 0
        commutator = AverageElement(
            s = 0.0,
            c = 0.0,
            actA = 0.0,
            fullA = if (elements[0].fullAmperage == null) null else 0.0,
            rgm = 0,
            t = 0.0,
            ma = 0.0
        )
        for (j in i until i + count) {
            if (j < elements.size) {
                commutator = AverageElement(
                    s = commutator.s + elements[j].speed,
                    c = commutator.c + elements[j].coordinate,
                    actA = commutator.actA + elements[j].activeAmperage,
                    fullA = if (commutator.fullA == null) null else commutator.fullA!! + elements[j].fullAmperage!!,
                    rgm = commutator.rgm + elements[j].regime,
                    t = commutator.t + elements[j].temperature,
                    ma = commutator.ma + elements[j].motorAmperage
                )
                counter++
            }
        }
        if (counter > 0) {
            commutator = AverageElement(
                s = (commutator.s / counter).round(3),
                c = (commutator.c / counter).round(3),
                actA = (commutator.actA / counter).round(3),
                fullA = if (commutator.fullA == null) null else (commutator.fullA!! / counter).round(3),
                rgm = if (commutator.rgm / counter > 0.5) 1 else if (commutator.rgm / counter < -0.5) -1 else 0,
                t = (commutator.t / counter).round(3),
                ma = (commutator.ma / counter).round(3)
            )
            result.add(commutator)
        }
    }
    return result
}

/**
 * Количество локомотивов для данного участка
 */
fun tractionRateForCoordinate(tractiveRateList: List<TractionRate>, currentCoordinate: Double): Double {
    if (tractiveRateList.isEmpty()) return 1.0
    for (i in tractiveRateList.indices.reversed()) {
        if (currentCoordinate >= tractiveRateList[i].coordinate) {
            return tractiveRateList[i].rate
        }
    }
    return 1.0
}

private fun getTotalResistance(
    locomotive: Locomotive,
    train: Train,
    type: TrackType,
    tractionRate: Double
): TractiveResistanceToMotion {
    val idleResistance: DoubleArray
    val motoringResistance: DoubleArray
    val totalLocomotiveWeight = tractionRate * locomotive.weight
    val totalTrainWeight = train.weight
    when (type) {
        TrackType.CONTINUOUS -> {
            idleResistance = getCoefficients(
                totalLocomotiveWeight, totalTrainWeight,
                locomotive.resistanceToMotion.idleResistanceCoefficients.continuousRail,
                train.resistanceToMotion!!.continuousRail
            )
            motoringResistance = getCoefficients(
                totalLocomotiveWeight, totalTrainWeight,
                locomotive.resistanceToMotion.motoringResistanceCoefficients.continuousRail,
                train.resistanceToMotion!!.continuousRail
            )
        }
        TrackType.COMPONENT -> {
            idleResistance = getCoefficients(
                totalLocomotiveWeight, totalTrainWeight,
                locomotive.resistanceToMotion.idleResistanceCoefficients.componentRail,
                train.resistanceToMotion!!.componentRail
            )
            motoringResistance = getCoefficients(
                totalLocomotiveWeight, totalTrainWeight,
                locomotive.resistanceToMotion.motoringResistanceCoefficients.componentRail,
                train.resistanceToMotion!!.componentRail
            )
        }
    }
    if (motoringResistance.any { it.isNaN() } || idleResistance.any { it.isNaN() }) {
        throw IllegalStateException(
            "Invalid resistance coefficients: " +
                    "${TractiveResistanceToMotion(motoringResistance, idleResistance)}"
        )
    }
    return TractiveResistanceToMotion(motoringResistance = motoringResistance, idleResistance = idleResistance)
}

private fun getCoefficients(
    totalLocomotiveWeight: Double,
    totalTrainWeight: Double,
    locomotiveCoefficients: Array<Double?>,
    trainCoefficients: Array<Double?>
) = doubleArrayOf(
    (totalLocomotiveWeight * locomotiveCoefficients[0]!! + totalTrainWeight * trainCoefficients[0]!!) / (totalTrainWeight + totalLocomotiveWeight),
    (totalLocomotiveWeight * locomotiveCoefficients[1]!! + totalTrainWeight * trainCoefficients[1]!!) / (totalTrainWeight + totalLocomotiveWeight),
    (totalLocomotiveWeight * locomotiveCoefficients[2]!! + totalTrainWeight * trainCoefficients[2]!!) / (totalTrainWeight + totalLocomotiveWeight)
)

private fun getTotalWeight(train: Train, locomotive: Locomotive, tractionRate: Double) =
    train.weight + locomotive.weight * tractionRate
