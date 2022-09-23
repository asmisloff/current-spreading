package ru.vniizht.currentspreading.core.acnew

import org.apache.commons.math3.complex.Complex
import ru.vniizht.asuterkortes.counter.circuit.ICircuitEdge
import ru.vniizht.currentspreading.core.circuit.amendConsideringBranchIndex
import ru.vniizht.currentspreading.dto.ACNetworkDto
import ru.vniizht.currentspreading.core.schedule.OrderedList
import ru.vniizht.currentspreading.util.checkNotNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Комплексные удельные сопротивления эквивалентного провода.
 * Ключ - пара (путь, путь), значение - соотв. приведенное сопротивление
 */
internal typealias MutualResistivities = Map<Pair<Int, Int>, Complex>

/**
 * Приведенные сопротивления КС (ПП) на переменном токе
 * @param xMax координата, на которой заканчивается данный участок КС
 * @param rr комплексные сопротивления эквивалентных проводов данного участка КС.
 * Ключ - пара (путь, путь), значение - соответствующее приведенное сопротивление
 */
data class NetworkResistanceRangeAC(val xMax: Double, val rr: MutualResistivities) {

    fun computeResistance(x1: Double, x2: Double, tn1: Int, tn2: Int): Complex {
        require(x1 <= x2)
        require(tn1 <= tn2)
        val resistivity = rr[Pair(tn1, tn2)].checkNotNull {
            "Не рассчитано взаимное сопротивление между проводами $tn1 и $tn2"
        }
        val xLeft = min(x1, xMax)
        val xRight = min(x2, xMax)

        return resistivity * (xRight - xLeft)
    }

}

fun OrderedList<NetworkResistanceRangeAC>.merge(): List<NetworkResistanceRangeAC> {
    assert(this.isNotEmpty())

    val m = mutableMapOf<Double, MutableMap<Pair<Int, Int>, Complex>>()
    for (i in 0..lastIndex) {
        val r = this[i]
        val resistivities = m.getOrPut(r.xMax) { mutableMapOf() }
        if (resistivities.isNotEmpty()) continue
        for (j in i..lastIndex) {
            for ((trackPair, resistivityForTrackPair) in this[j].rr) {
                if (resistivities.containsKey(trackPair)) break
                resistivities[trackPair] = resistivityForTrackPair
            }
        }
    }

    return m.keys.map { xMax ->
        NetworkResistanceRangeAC(xMax, m[xMax] as MutualResistivities)
    }
}

/**
 * Параметры пары индуктивно связанных участков КС (ПП)
 * @param i1 индекс первой ветви схемы
 * @param i2 индекс второй ветви схемы
 * @param xLeft левая координата участка взаимоиндукции
 * @param xRight правая координата участка взаимоиндукции
 */
internal data class InductivelyCoupledSpan(val i1: Int, val i2: Int, val xLeft: Double, val xRight: Double) {

    override fun toString(): String {
        return "{$i1-$i2: ($xLeft, $xRight)}"
    }

}

/**
 * Вычисляет взаимное сопротивление двух элементов эквивалентного провода.
 * Если track1Number == track2Number, будет вычислено собственное сопротивление.
 * @param xLeft левая координата участка КС
 * @param xRight правая координата участка КС
 * @param track1Number первый номер пути
 * @param track2Number второй номер пути
 */
internal fun List<NetworkResistanceRangeAC>.computeResistance(
    xLeft: Double,
    xRight: Double,
    track1Number: Int,
    track2Number: Int
): Complex {
    require(xLeft <= xRight)
    require(track1Number <= track2Number)
    require(this.isNotEmpty()) { "Не рассчитаны удельные сопротивления проводов КС" }
    check(this.last().xMax >= xRight) {
        "Ошибка трассировки схемы. Для участка $xLeft-$xRight км, не задана контактная сеть (последняя секция заканчивается на ${this.last().xMax} км)."
    }
    var re = 0.0
    var im = 0.0
    var xMin = xLeft
    for (rr in this) {
        val z = rr.computeResistance(xMin, xRight, track1Number, track2Number)
        re += z.real
        im += z.imaginary
        if (xRight <= rr.xMax) { /* все последующие диапазоны вернут ZERO, нет смысла их обходить */
            break
        }
        xMin = max(rr.xMax, xLeft)
    }
    return when {
        track1Number.isCnTrackNumber() && track2Number.isCnTrackNumber() -> Complex(re, im)
        track1Number.isSupplyTrackNumber() && track2Number.isSupplyTrackNumber() -> Complex(re, im)
        track1Number.isSupplyTrackNumber() && track2Number.isCnTrackNumber() -> Complex(-re, -im)
        track1Number.isCnTrackNumber() && track2Number.isSupplyTrackNumber() -> Complex(-re, -im)
        else -> throw NotImplementedError(
            "Неподдерживаемая комбинация номеров путей: ($track1Number, $track2Number). Приведенное сопротивление не может быть рассчитано."
        )
    }
}

fun Int.isSupplyTrackNumber(): Boolean {
    return (this % 10_000) in 1001..1006
}

fun Int.isCnTrackNumber(): Boolean {
    return (this % 10_000) in 1..6
}

/**
 * Возвращает список индуктивно связанных участков КС и ПП.
 */
internal fun <T : ICircuitEdge> inductivelyCoupledSpans(
    edges1: List<T>,
    edges2: List<T>
): List<InductivelyCoupledSpan> {
    val res = mutableListOf<InductivelyCoupledSpan>()
    var j0 = 0

    for (i in edges1.indices) {
        val e1 = edges1[i]
        val i1 = e1.index
        val xLeft1 = e1.getSourceNode().coordinate
        val xRight1 = e1.getTargetNode().coordinate
        for (j in j0..edges2.lastIndex) {
            val e2 = edges2[j]
            val i2 = e2.index
            val xLeft2 = e2.getSourceNode().coordinate
            val xRight2 = e2.getTargetNode().coordinate
            when {
                xLeft1 >= xRight2 -> continue

                xLeft2 >= xRight1 -> break

                else -> {
                    val xLeft = max(xLeft1, xLeft2)
                    val xRight = min(xRight1, xRight2)
                    res.add(InductivelyCoupledSpan(i1, i2, xLeft, xRight))
                    j0 = j
                }
            }
        }
    }

    return res
}

internal fun Map<Int, List<ICircuitEdge>>.toInductivelyCoupledSpans(): Map<Pair<Int, Int>, List<InductivelyCoupledSpan>> {
    val res = mutableMapOf<Pair<Int, Int>, List<InductivelyCoupledSpan>>()
    val keys = this.keys.sorted()
    for (i in 0..keys.lastIndex) {
        for (j in i..keys.lastIndex) {
            val key1 = keys[i]
            val key2 = keys[j]
            if (abs(key1 - key2) > 5_000) { // нет взаимоиндукции между разными ответвлениями
                continue
            }
            res[Pair(key1, key2)] = inductivelyCoupledSpans(edges1 = this[key1]!!, edges2 = this[key2]!!)
        }
    }

    return res
}

val acdMutualPattern = Regex("^[КПкп]\\d-[КПкп]\\d\$") // к1-п2
val acMutualPattern = Regex("^\\d-\\d\$") // 1-1
fun ACNetworkDto.toMutualResistances(): MutualResistivities {
    fun parseTrackName(trackName: String, errorMsg: String): Int {
        return try {
            val trackNumber = when {
                trackName.lowercase().startsWith("п") -> (1000 + trackName.substring(1).toInt())
                trackName.lowercase().startsWith("к") -> trackName.substring(1).toInt()
                else -> trackName.first() - '0'
            }
            trackNumber.amendConsideringBranchIndex(this.branchIndex)
        } catch (e: NumberFormatException) {
            throw IllegalStateException(errorMsg)
        }
    }

    val result = mutableMapOf<Pair<Int, Int>, Complex>()
    for (param in this.network.parameters) {
        val key = when {
            acMutualPattern.containsMatchIn(param.trackName) || acdMutualPattern.containsMatchIn(param.trackName) -> {
                val errorMsg by lazy { "Неверный формат данных в БД. Индексы путей во взаимном сопротивлении: ${param.trackName}" }
                param.trackName
                    .split('-')
                    .map { parseTrackName(it, errorMsg) }
                    .sorted()
                    .also { check(it.size == 2) { errorMsg } }
                    .let { Pair(it[0], it[1]) }
            }

            param.trackName == "Прл" -> null

            else -> {
                val errorMsg by lazy { "Неверный формат данных в БД. Индексы путей в сопротивлении эквивалентного провода: ${param.trackName}" }
                val trackNumber = parseTrackName(param.trackName, errorMsg)
                Pair(trackNumber, trackNumber)
            }
        }

        if (key != null) {
            result[key] = Complex(param.activeResistance, param.inductiveResistance)
        }
    }

    return result
}