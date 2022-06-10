package ru.vniizht.currentspreading.core.throughout.capacity.integral.indices

import org.apache.commons.math3.complex.Complex
import org.ejml.data.ComplexPolar_F32
import org.ejml.data.ComplexPolar_F64
import org.ejml.data.Complex_F32
import org.ejml.data.Complex_F64
import ru.vniizht.currentspreading.dao.enums.WireAndRailType
import kotlin.math.PI

const val MAX_ROUTE_QTY = 100

val exp60deg: Complex_F64 = ComplexPolar_F64(1.0, PI / 3).toStandard()
val expMinus60deg: Complex_F64 = ComplexPolar_F64(1.0, -PI / 3).toStandard()

val exp60degF: Complex_F32 = ComplexPolar_F32(1f, PI.toFloat() / 3).toStandard()
val expMinus60degF: Complex_F32 = ComplexPolar_F32(1f, -PI.toFloat() / 3).toStandard()

/**
 * Данные лимитирующих проводов на выходе подстанции
 * @param leftCn список для питающих фидеров КС левого плеча
 * @param rightCn список для питающих фидеров КС правого плеча
 * @param sucking для фидера или КС отсасывающей линии
 * @param leftSp список для питающих фидеров ПП левого плеча. null для схем DC и AC.
 * @param rightSp список для питающих фидеров ПП правого плеча. null для схем DC и AC.
 */
class SsWiresLimitInfo(
    val leftCn: List<LimitWireInfoAc>,
    val rightCn: List<LimitWireInfoAc>,
    val sucking: LimitWireInfoAc,
    val leftSp: List<LimitWireInfoAc>? = null,
    val rightSp: List<LimitWireInfoAc>? = null
)

/**
 * Модельные параметры лимитирующего провода (далее ЛП)
 * @param trackCatenaryName наименование подвески, в которую входит ЛП
 * @param limitWireName наименование ЛП
 * @param networkLimitAmperage лимитирующий ток эквивалентного провода (ГОСТ Р 57670-2017, приложение Б.2.2), в который входит ЛП
 * @param limitWireTemperature предельная допустимая температура нагрева ЛП
 * @param limitWireCurrentFraction доля тока в ЛП (для схем постоянного тока мнимая часть нулевая)
 * @param limitWireType тип провода ЛП
 * @param limitWireRadius радиус ЛП в см
 * @param limitWireThermalCapacity теплоемкость ЛП
 * @param limitWireResistance удельное сопротивление ЛП
 */
data class LimitWireInfoAc(
    val trackCatenaryName: String,
    val limitWireName: String,
    val networkLimitAmperage: Int,
    val limitWireTemperature: Int,
    val limitWireCurrentFraction: Complex,
    val limitWireType: WireAndRailType,
    val limitWireRadius: Double,
    val limitWireThermalCapacity: Double,
    val limitWireResistance: Double
)

/**
 * Возвращает более слаботочный провод из двух или null, если оба аргумента null
 */
fun Pair<LimitWireInfoAc?, LimitWireInfoAc?>.weakest(): LimitWireInfoAc? {
    return when {
        first == null -> second
        second == null -> first
        else -> {
            when {
                first!!.networkLimitAmperage < second!!.networkLimitAmperage -> first
                else -> second
            }
        }
    }
}