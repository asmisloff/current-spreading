package ru.vniizht.asuterkortes.counter.throughout.capacity.integral.indices

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.commons.math3.complex.Complex
import org.ejml.data.Complex_F32
import org.ejml.data.Complex_F64
import ru.vniizht.currentspreading.core.acnew.assign
import ru.vniizht.currentspreading.core.acnew.plusAssign
import ru.vniizht.asuterkortes.dto.IPayloadSolution

/**
 * Группы интегральных показателей режимов работы СТЭ
 */
enum class IntegralIndexTag {
    MainReport, TransformerLoadFactors, AutoTransformerLoadFactors, WiresHeating, PowerLosses, TrainVoltages
}

/**
 * Запись в таблице интегральных показателей
 */
data class IntegralIndexRecord(val tag: IntegralIndexTag, val data: Map<String, IntegralIndexDataEntry>) {

    override fun toString(): String {
        return "{$tag: [$data]}"
    }

}

/**
 * Ячейка данных интегрального показателя
 * @param value значение
 * @param cf коэффициент исчерпания нагрузочной способности (capacity factor)
 * @param annotation опциональное примечание
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "valueType")
@JsonSubTypes(
    JsonSubTypes.Type(value = IntegralIndexIntDataEntry::class, name = "Int"),
    JsonSubTypes.Type(value = IntegralIndexDoubleDataEntry::class, name = "Double"),
    JsonSubTypes.Type(value = IntegralIndexComplexDataEntry::class, name = "Complex"),
    JsonSubTypes.Type(value = IntegralIndexStringDataEntry::class, name = "String"),
    JsonSubTypes.Type(value = IntegralIndexDoubleDataEntry::class, name = "LimitWireInfo"),
)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class IntegralIndexDataEntry(open val value: Any, open val cf: Double?, open val annotation: String? = null) {

    override fun toString(): String {
        val cfStr = if (cf != null) ", $cf" else ""
        val annStr = if (annotation != null) ", $annotation" else ""
        return "{$value$cfStr$annStr}"
    }

}

data class IntegralIndexIntDataEntry(
    override val value: Int,
    override val cf: Double = 0.0,
    override val annotation: String? = null
) : IntegralIndexDataEntry(value, cf, annotation) {

    override fun toString(): String {
        return super.toString()
    }

}

data class IntegralIndexDoubleDataEntry(
    override val value: Double,
    override val cf: Double = 0.0,
    override val annotation: String? = null
) : IntegralIndexDataEntry(value, cf, annotation) {

    override fun toString(): String {
        return super.toString()
    }

}

data class IntegralIndexComplexDataEntry(
    override val value: Complex,
    override val cf: Double = 0.0,
    override val annotation: String? = null
) : IntegralIndexDataEntry(value, cf, annotation) {

    override fun toString(): String {
        return super.toString()
    }

}

data class IntegralIndexStringDataEntry(
    override val value: String,
    override val cf: Double? = null,
    override val annotation: String? = null
) : IntegralIndexDataEntry(value, cf, annotation) {

    override fun toString(): String {
        return super.toString()
    }

}

/**
 * Частичное решение задачи расчета режимов работы СТЭ.
 * Полное решение состоит из одного или нескольких частичных. Частичное решение относится к некоторому временному периоду
 * параллельного графика движения. Частные решения могут быть рассчитаны параллельно в отдельных потоках.
 */
class CompactSolutionAcChunk(
    val ssChunk: SsAcSolutionChunk,
    val payloadChunk: PayloadSolutionChunk,
    val atChunk: AtAcdSolutionChunk,
    var powerLoss: Complex_F64
)

/** Маркерный интерфейс */
interface CompactSolution

/**
 * Данные нагрузки по итогам расчета мгновенной схемы в "компактной" (с точки зрения памяти) форме.
 * @param axisCoordinate координата нагрузки
 * @param voltage напряжение на токосъемнике
 * @param trackNumber номер пути
 * @param routeIndex номер маршрута на графике движения
 */
class CompactPayloadSolution(
    val axisCoordinate: Float,
    val voltage: Float,
    val trackNumber: Byte,
    val routeIndex: Short
) : CompactSolution, IPayloadSolution {

    @JsonIgnore
    override fun axisCoordinate() = axisCoordinate.toDouble()

    @JsonIgnore
    override fun isPayload() = true

    @JsonIgnore
    override fun trackNumber() = trackNumber.toInt()

    @JsonIgnore
    override fun routeIndex() = routeIndex.toInt()

    @JsonIgnore
    override fun pantographVoltage() = voltage.toDouble()

    override fun toString(): String {
        return "{coord=$axisCoordinate, voltage=$voltage, trackNumber=$trackNumber, routeIndex=$routeIndex}"
    }

}

/**
 * Электрические характеристики подстанций 25 кВ по итогам расчета мгновенной схемы в "компактной" (с точки зрения памяти) форме.
 * @param leftActiveCnFeederAmperages массив активных составляющих токов в фидерах левого плеча
 * @param leftReactiveCnFeederAmperages массив реактивных составляющих токов в фидерах левого плеча
 * @param leftActiveCnVoltage массив активных составляющих напряжений на выходах фидеров левого плеча
 * @param leftReactiveCnVoltage массив реактивных составляющих напряжений на выходах фидеров левого плеча
 *
 * @param rightActiveCnFeederAmperages массив активных составляющих токов в фидерах левого плеча
 * @param rightReactiveCnFeederAmperages массив реактивных составляющих токов в фидерах левого плеча
 * @param rightActiveCnVoltage массив активных составляющих напряжений на выходах фидеров левого плеча
 * @param rightReactiveCnVoltage массив реактивных составляющих напряжений на выходах фидеров левого плеча
 *
 * @param suckerActiveAmperage активный ток в цепи отсоса
 * @param suckerReactiveAmperage реактивный ток в цепи отсоса
 */
open class CompactSsAcSolution(
    open val leftActiveCnFeederAmperages: FloatArray,
    open val leftReactiveCnFeederAmperages: FloatArray,
    open val leftActiveCnVoltage: Float,
    open val leftReactiveCnVoltage: Float,

    open val rightActiveCnFeederAmperages: FloatArray,
    open val rightReactiveCnFeederAmperages: FloatArray,
    open val rightActiveCnVoltage: Float,
    open val rightReactiveCnVoltage: Float,

    open val suckerActiveAmperage: Float,
    open val suckerReactiveAmperage: Float,

    val totalBranchCnActiveAmperage: Float = 0f,
    val totalBranchCnReactiveAmperage: Float = 0f
) : CompactSolution {

    open fun getLeftTotalAmp(buffer: Complex_F32): Complex_F32 {
        return getLeftCnAmp(buffer)
    }

    open fun getRightTotalAmp(buffer: Complex_F32): Complex_F32 {
        return getRightCnAmp(buffer)
    }

    open fun getLeftCnAmp(buffer: Complex_F32): Complex_F32 {
        return loop(leftActiveCnFeederAmperages, leftReactiveCnFeederAmperages, buffer)
    }

    open fun getRightCnAmp(buffer: Complex_F32): Complex_F32 {
        loop(rightActiveCnFeederAmperages, rightReactiveCnFeederAmperages, buffer)
        buffer.plusAssign(totalBranchCnActiveAmperage, totalBranchCnReactiveAmperage)
        return buffer
    }

    open fun getLeftTotalVoltage(buffer: Complex_F32): Complex_F32 {
        buffer.assign(leftActiveCnVoltage, leftReactiveCnVoltage)
        return buffer
    }

    open fun getRightTotalVoltage(buffer: Complex_F32): Complex_F32 {
        buffer.assign(rightActiveCnVoltage, rightReactiveCnVoltage)
        return buffer
    }

    private fun loop(active: FloatArray, reactive: FloatArray, buffer: Complex_F32): Complex_F32 {
        var act = 0f
        var react = 0f
        for (i in active.indices) {
            act += active[i]
            react += reactive[i]
        }
        buffer.assign(act, react)
        return buffer
    }

    override fun toString(): String {
        return "{left: {a=${leftActiveCnFeederAmperages.contentToString()}, r=${leftReactiveCnFeederAmperages.contentToString()}, u=$leftActiveCnVoltage},\n" +
                "right: {a=${rightActiveCnFeederAmperages.contentToString()}, r=${rightReactiveCnFeederAmperages.contentToString()}, u=$rightActiveCnVoltage},\n" +
                "sucker: $suckerActiveAmperage"
    }
}

/**
 * Электрические характеристики подстанций 2х25 кВ по итогам расчета мгновенной схемы в "компактной" (с точки зрения памяти) форме.
 * @param leftActiveCnFeederAmperages массив активных составляющих токов в фидерах КС левого плеча
 * @param leftReactiveCnFeederAmperages массив реактивных составляющих токов в фидерах КС левого плеча
 * @param leftActiveCnVoltage массив активных составляющих напряжений на выходах фидеров КС левого плеча
 * @param leftReactiveCnVoltage массив реактивных составляющих напряжений на выходах фидеров КС левого плеча
 * @param leftActiveSpFeederAmperages массив активных составляющих токов в фидерах ПП левого плеча
 * @param leftReactiveSpFeederAmperages массив реактивных составляющих токов в фидерах ПП левого плеча
 * @param leftActiveSpVoltage массив активных составляющих напряжений на выходах фидеров ПП левого плеча
 * @param leftReactiveSpVoltage массив реактивных составляющих напряжений на выходах фидеров ПП левого плеча
 *
 * @param rightActiveCnFeederAmperages массив активных составляющих токов в фидерах КС левого плеча
 * @param rightReactiveCnFeederAmperages массив реактивных составляющих токов в фидерах КС левого плеча
 * @param rightActiveCnVoltage массив активных составляющих напряжений на выходах фидеров КС левого плеча
 * @param rightReactiveCnVoltage массив реактивных составляющих напряжений на выходах фидеров КС левого плеча
 * @param rightActiveSpFeederAmperages массив активных составляющих токов в фидерах ПП левого плеча
 * @param rightReactiveSpFeederAmperages массив реактивных составляющих токов в фидерах ПП левого плеча
 * @param rightActiveSpVoltage массив активных составляющих напряжений на выходах фидеров ПП левого плеча
 * @param rightReactiveSpVoltage массив реактивных составляющих напряжений на выходах фидеров ПП левого плеча
 *
 * @param suckerActiveAmperage активный ток в цепи отсоса
 * @param suckerReactiveAmperage реактивный ток в цепи отсоса
 */
class CompactSsAcdSolution(
    leftActiveCnFeederAmperages: FloatArray,
    leftReactiveCnFeederAmperages: FloatArray,
    leftActiveCnVoltage: Float,
    leftReactiveCnVoltage: Float,

    val leftActiveSpFeederAmperages: FloatArray,
    val leftReactiveSpFeederAmperages: FloatArray,
    val leftActiveSpVoltage: Float,
    val leftReactiveSpVoltage: Float,

    rightActiveCnFeederAmperages: FloatArray,
    rightReactiveCnFeederAmperages: FloatArray,
    rightActiveCnVoltage: Float,
    rightReactiveCnVoltage: Float,

    val rightActiveSpFeederAmperages: FloatArray,
    val rightReactiveSpFeederAmperages: FloatArray,
    val rightActiveSpVoltage: Float,
    val rightReactiveSpVoltage: Float,

    suckerActiveAmperage: Float,
    suckerReactiveAmperage: Float,

    totalBranchCnActiveAmperage: Float,
    totalBranchCnReactiveAmperage: Float,
    val totalBranchSpActiveAmperage: Float,
    val totalBranchSpReactiveAmperage: Float,
) : CompactSsAcSolution(
    leftActiveCnFeederAmperages,
    leftReactiveCnFeederAmperages,
    leftActiveCnVoltage,
    leftReactiveCnVoltage,
    rightActiveCnFeederAmperages,
    rightReactiveCnFeederAmperages,
    rightActiveCnVoltage,
    rightReactiveCnVoltage,
    suckerActiveAmperage,
    suckerReactiveAmperage,
    totalBranchCnActiveAmperage,
    totalBranchCnReactiveAmperage
), CompactSolution {

    fun getLeftSpAmp(buffer: Complex_F32): Complex_F32 {
        buffer.assign(leftActiveSpFeederAmperages.sum(), leftReactiveSpFeederAmperages.sum())
        return buffer
    }

    fun getRightSpAmp(buffer: Complex_F32): Complex_F32 {
        buffer.assign(rightActiveSpFeederAmperages.sum(), rightReactiveSpFeederAmperages.sum())
        buffer.plusAssign(totalBranchSpActiveAmperage, totalBranchSpReactiveAmperage)
        return buffer
    }

    override fun getLeftTotalAmp(buffer: Complex_F32): Complex_F32 {
        return loop(
            leftActiveCnFeederAmperages, leftActiveSpFeederAmperages,
            leftReactiveCnFeederAmperages, leftReactiveSpFeederAmperages, buffer
        )
    }

    override fun getRightTotalAmp(buffer: Complex_F32): Complex_F32 {
        loop(
            rightActiveCnFeederAmperages, rightActiveSpFeederAmperages,
            rightReactiveCnFeederAmperages, rightReactiveSpFeederAmperages, buffer
        )
        buffer.plusAssign(
            totalBranchCnActiveAmperage + totalBranchSpActiveAmperage,
            totalBranchCnReactiveAmperage + totalBranchSpReactiveAmperage
        )
        return buffer
    }

    override fun getLeftTotalVoltage(buffer: Complex_F32): Complex_F32 {
        buffer.assign(leftActiveCnVoltage + leftActiveSpVoltage, leftReactiveCnVoltage + leftReactiveSpVoltage)
        return buffer
    }

    override fun getRightTotalVoltage(buffer: Complex_F32): Complex_F32 {
        buffer.assign(rightActiveCnVoltage + rightActiveSpVoltage, rightReactiveCnVoltage + rightReactiveSpVoltage)
        return buffer
    }

    private fun loop(
        active1: FloatArray, active2: FloatArray, reactive1: FloatArray, reactive2: FloatArray, buffer: Complex_F32
    ): Complex_F32 {
        var re = 0f
        var im = 0f
        for (i in active1.indices) {
            re += active1[i] + active2[i]
            im += reactive1[i] + reactive2[i]
        }
        buffer.assign(re, im)
        return buffer
    }

}

class CompactAtAcdSolution(
    val atActiveAmperages: FloatArray,
    val atReactiveAmperages: FloatArray
) : CompactSolution

class PayloadSolutionChunk(val zoneQty: Int) {

    private val data = MutableList(zoneQty) { mutableListOf<CompactPayloadSolution>() }

    fun addSolution(zoneIndex: Int, solution: CompactPayloadSolution) {
        data[zoneIndex].add(solution)
    }

    operator fun get(zoneIndex: Int) = data[zoneIndex]
}

class SsAcSolutionChunk(val ssQty: Int) {

    private val data = MutableList(ssQty) { mutableListOf<CompactSsAcSolution>() }

    fun addSolution(ssIndex: Int, solution: CompactSsAcSolution) {
        data[ssIndex].add(solution)
    }

    operator fun get(ssIndex: Int) = data[ssIndex]
}

class AtAcdSolutionChunk(atBlockQty: Int) {

    private val data = List(atBlockQty) { mutableListOf<CompactAtAcdSolution>() }

    fun addSolution(blockIndex: Int, solution: CompactAtAcdSolution) {
        data[blockIndex].add(solution)
    }

    operator fun get(blockIndex: Int) = data[blockIndex]

}