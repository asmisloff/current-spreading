package ru.vniizht.asuterkortes.counter.tractive

import ru.vniizht.asuterkortes.dao.model.jsonb.MotorThermalCharacteristics
import kotlin.math.abs
import kotlin.math.exp

private const val EPS = 1e-2

/**
 * Режим движения поезда
 */
enum class MotionMode {
    /** Разгон */
    ACCELERATION,

    /** Выбег */
    RETARDATION,

    /** Остановка */
    STOP,

    /** Торможение */
    BREAKING,

    /** Рекуперативное торможение */
    RECUPERATIVE_BREAKING
}

data class ComputeTemperatureResult(val temperature: Double, val motorAmperage: Double)

/**
 * Расчет дополнительного нагрева
 * @param mode режим движения
 * @param v скорость движения поезда
 * @param pos номер позиции на характеристике тягового режима
 * @param adhesionCoef коэффициент сцепления
 * @param initialOverheat текущая температура
 * @param duration интервал времени, на котором выполняется расчет
 */
fun computeTemperature(
    mode: MotionMode,
    initialOverheat: Double,
    iEng: Double,
    duration: Double,
    mtc: MotorThermalCharacteristics
): Double {
    val ttc = mtc.thermalTimeConstant
    return when (mode) {
        MotionMode.ACCELERATION -> { // по ПТР
            (mtc.tauInf(iEng) * duration) / ttc + initialOverheat * (1 - duration / ttc)
        }

        MotionMode.RETARDATION, MotionMode.STOP, MotionMode.BREAKING -> { // по Кортэсу
            val t = initialOverheat * exp(-duration / ttc)
            if (t >= 15.0)
                t
            else
                15.0
        }

        MotionMode.RECUPERATIVE_BREAKING -> {
            (mtc.tauInf(iEng) * duration) / ttc + initialOverheat * (1 - duration / ttc)
        }
    }
}

/**
 * Линейная интерполяция
 */
fun linterp(x1: Double, x2: Double, y1: Double, y2: Double, x: Double) = y1 + (y2 - y1) * ((x - x1) / (x2 - x1))

/**
 * Линейная экстраполяция
 * @return ординату точки x0
 */
fun lextrap(x1: Double, y1: Double, x2: Double, y2: Double, x: Double) = y1 + (y2 - y1) / (x2 - x1) * (x - x1)

/**
 * Расчет установившегося превышения температуры обмоток тяговых машин над температурой охл. воздуха (по ПТР)
 * @param motorAmperage ток двигателя
 * @return Точное или интерполированное значение из таблицы характеристик
 */
private fun MotorThermalCharacteristics.tauInf(motorAmperage: Double): Double {
    if (this.characteristics.size < 2) {
        throw IllegalArgumentException("Thermal characteristic list length < 2")
    }
    val first = this.characteristics.first().motorAmperage!!
    if (motorAmperage <= first) {
        val x1 = this.characteristics[0].motorAmperage!!
        val x2 = this.characteristics[1].motorAmperage!!
        val y1 = this.characteristics[0].balancingOverheat!!
        val y2 = this.characteristics[1].balancingOverheat!!
        return lextrap(x1, y1, x2, y2, motorAmperage)
    }
    for (i in this.characteristics.indices) {
        val ampCur = this.characteristics[i].motorAmperage!!
        if (abs(motorAmperage - ampCur) <= EPS) {
            return this.characteristics[i].balancingOverheat!!
        }
        if (motorAmperage < ampCur) {
            return linterp(
                this.characteristics[i - 1].motorAmperage!!,
                this.characteristics[i].motorAmperage!!,
                this.characteristics[i - 1].balancingOverheat!!,
                this.characteristics[i].balancingOverheat!!,
                motorAmperage
            )
        }
    }

    // motorAmperage больше максимального табличного значения
    val sz = this.characteristics.size
    val x1 = this.characteristics[sz - 2].motorAmperage!!
    val x2 = this.characteristics[sz - 1].motorAmperage!!
    val y1 = this.characteristics[sz - 2].balancingOverheat!!
    val y2 = this.characteristics[sz - 1].balancingOverheat!!
    return lextrap(x1, y1, x2, y2, motorAmperage)
}