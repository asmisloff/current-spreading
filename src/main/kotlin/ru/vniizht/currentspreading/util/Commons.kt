package ru.vniizht.currentspreading.util

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.util.Precision
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import ru.vniizht.asuterkortes.dao.model.jsonb.ResistanceToMotion
import ru.vniizht.currentspreading.core.schedule.OrderedList
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val logger = LoggerFactory.getLogger("Commons")

data class PagedResult<T>(
    val entities: List<T>,
    val totalCount: Long,
    val totalPages: Int
)

fun <T : Any> Page<T>.toDto() = PagedResult<T>(
    entities = content,
    totalCount = totalElements,
    totalPages = totalPages
)

fun Double.round(n: Int) =
    Precision.round(this, n)

fun Double.toFixed(n: Int) = when (n) {
    0 -> this.round(n).toInt().toString()
    else -> this.round(n).toString()
}

fun Double.smartRound(n: Int) =
    when {
        abs(this) > 1 * 10.0.pow(4 * n) -> {
            Precision.round(this, -2 * n)
        }
        abs(this) > 1 * 10.0.pow(2 * n) -> {
            Precision.round(this, -n)
        }
        abs(this) > 1 * 10.0.pow(n) -> {
            Precision.round(this, -n / 2)
        }
        abs(this) > 1 / 10.0.pow(n + 1) -> {
            Precision.round(this, n)
        }
        abs(this) > 1 / 10.0.pow(2 * n + 1) -> {
            Precision.round(this, 2 * n)
        }
        abs(this) > 1 / 10.0.pow(3 * n + 1) -> {
            Precision.round(this, 3 * n)
        }
        abs(this) > 1 / 10.0.pow(4 * n + 1) -> {
            Precision.round(this, 4 * n)
        }
        abs(this) > 1 / 10.0.pow(5 * n + 1) -> {
            Precision.round(this, 5 * n)
        }
        abs(this) > 1 / 10.0.pow(6 * n + 1) -> {
            Precision.round(this, 6 * n)
        }
        abs(this) > 1 / 10.0.pow(7 * n + 1) -> {
            Precision.round(this, 7 * n)
        }
        abs(this) > 1 / 10.0.pow(8 * n + 1) -> {
            Precision.round(this, 8 * n)
        }
        abs(this) > 1 / 10.0.pow(9 * n + 1) -> {
            Precision.round(this, 9 * n)
        }
        else -> {
            Precision.round(this, 10 * n)
        }
    }

fun checkPositiveOrZeroField(value: Double, fieldName: String) {
    if (value < 0) {
        throw IllegalArgumentException("Параметр $fieldName = '$value' не должен быть меньше нуля")
    }
}

fun checkFieldInRange(value: Double, fieldName: String, min: Double, max: Double) {
    if (value < min || value > max) {
        throw IllegalArgumentException("Параметр $fieldName = '$value' должен быть в диапазоне между $min и $max")
    }
}

fun checkPositiveField(value: Double, fieldName: String) {
    if (value <= 0) {
        throw IllegalArgumentException("Параметр $fieldName = '$value' должен быть больше нуля")
    }
}

fun checkResistance(resistanceToMotion: ResistanceToMotion, size: Int) {

    if (resistanceToMotion.componentRail.size != size || resistanceToMotion.continuousRail.size != size) {
        throw IllegalArgumentException("Неверное количество коэффициентов удельного сопротивления движению: $resistanceToMotion")
    }
    resistanceToMotion.continuousRail.forEach {
        if (it == null || it < 0) {
            throw IllegalArgumentException("Некорректный коэффициент удельного сопротивления движению $it. Все коэффициенты: $resistanceToMotion")
        }
    }
    resistanceToMotion.componentRail.forEach {
        if (it == null || it < 0) {
            throw IllegalArgumentException("Некорректный коэффициент удельного сопротивления движению $it. Все коэффициенты: $resistanceToMotion")
        }
    }
}

const val PAGEABLE_DEFAULT_SIZE = 100

fun LocalDateTime.toDateTime(): String {
    return try {
        format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
    } catch (e: Exception) {
        logger.warn(e.message)
        " - "
    }
}

fun complexMod(complex: Complex) =
    sqrt(complex.real.pow(2) + complex.imaginary.pow(2))

fun realMod(complex: Complex) =
    abs(complex.real)

infix fun Double.eq(other: Double) = abs(this - other) < 1e-6

infix fun Double.eq(other: Int) = abs(this - other.toDouble()) < 1e-6

/**
 * Возвращает subList с левой половиной списка.
 * Если в списке нечетное количество элементов, центральный элемент относится к правой части.
 * */
fun <T> List<T>.leftHalf() = this.subList(0, size / 2)

/**
 * Возвращает subList с правой половиной списка.
 * Если в списке нечетное количество элементов, центральный элемент относится к правой части.
 * */
fun <T> List<T>.rightHalf() = this.subList(size / 2, size)

fun <K, V> List<Map<K, V>>.merge(collisionResolver: (V, V) -> V): Map<K, V> {
    val result = mutableMapOf<K, V>()
    for (map in this) {
        for ((key, value) in map) {
            val existingValue = result[key]
            result[key] = if (existingValue == null) value else collisionResolver(existingValue, value)
        }
    }
    return result
}

fun <T, K : Comparable<K>> List<T>.findIndexIntervalForKey(
    key: K,
    sortingOrder: SortingOrder = SortingOrder.ASC,
    selector: (T) -> K
): Pair<Int, Int> {
    fun tr(index: Int) = when (sortingOrder) { // пересчитать индексы в зависимости от порядка сортировки
        SortingOrder.ASC -> index
        SortingOrder.DESC -> lastIndex - index
    }

    val list = when (sortingOrder) {
        SortingOrder.ASC -> this
        SortingOrder.DESC -> this.asReversed()
    }
    return when (val index = list.binarySearchBy(key, 0, this.size, selector)) {
        in list.indices -> Pair(tr(index), tr(index))
        else -> {
            val i1 = tr(
                when (val i = -index - 2) {
                    in 0..lastIndex -> i
                    else -> 0
                }
            )
            val i2 = tr(
                when (val i = -index - 1) {
                    in 0..lastIndex -> i
                    else -> lastIndex
                }
            )
            Pair(min(i1, i2), max(i1, i2))
        }
    }
}

enum class SortingOrder {
    ASC, DESC;

    companion object {
        fun <T> of(lst: OrderedList<T>, selector: (T) -> Double) = when {
            lst.size <= 1 -> ASC
            selector(lst.last()) - selector(lst.first()) >= 0 -> ASC
            else -> DESC
        }

        fun <T> of(lst: List<T>, selector: (T) -> Double) = when {
            lst.size <= 1 -> ASC
            selector(lst.last()) - selector(lst.first()) >= 0 -> ASC
            else -> DESC
        }
    }
}

fun <E> MutableList<E>.swap(i1: Int, i2: Int) {
    val tmp = this[i1]
    this[i1] = this[i2]
    this[i2] = tmp
}

fun <T> List<T>.combinations(): Sequence<Pair<T, T>> = sequence {
    for (i in indices) {
        for (j in i..lastIndex) {
            yield(Pair(get(i), get(j)))
        }
    }
}

fun <T, E> combinations(lst1: List<T>, lst2: List<E>): Sequence<Pair<T, E>> = sequence {
    for (i in lst1.indices) {
        for (j in lst2.indices) {
            yield(Pair(lst1[i], lst2[j]))
        }
    }
}

/**
 * Вычислить интеграл по формуле прямоугольников
 * @param xList список значений аргумента
 * @param yList список значений функции
 * @param a нижняя граница интегрирования
 * @param b верхняя граница интегрирования
 */
fun quadRect(xList: List<Double>, yList: List<Double>, a: Double, b: Double): Double {
    assert(xList.size == yList.size) { "Список значений аргумента и список значений функции должны быть одной длины" }
    var result = 0.0
    for ((i1, i2) in xList.indices.zipWithNext()) {
        val x1 = xList[i1]
        val x2 = xList[i2]
        val xLeft = max(a, x1)
        val xRight = min(b, x2)
        result += when {
            xLeft >= xRight -> 0.0
            else -> {
                yList[i1] * (xRight - xLeft)
            }
        }
    }
    return result
}

/** Проверить, лежит ли данный диапазон внутри другого */
infix fun ClosedRange<Double>.within(other: ClosedRange<Double>): Boolean {
    return other.contains(this.start) && other.contains(this.endInclusive)
}

/** Проверить, лежит ли данный диапазон внутри другого */
infix fun Pair<Double, Double>.within(other: Pair<Double, Double>): Boolean {
    return other.first <= this.first && other.second >= this.second
}

fun Double.withinExcl(x1: Double, x2: Double): Boolean {
    val xLeft = min(x1, x2)
    val xRight = max(x1, x2)
    return this < xRight && this > xLeft
}

infix fun ClosedRange<Double>.overlaps(other: ClosedRange<Double>): Boolean {
    return when {
        other.start >= this.endInclusive -> false
        other.endInclusive <= this.start -> false
        else -> true
    }
}

fun ClosedRange<Double>.overlaps(x1: Double, x2: Double): Boolean {
    return when {
        min(x1, x2) >= this.endInclusive -> false
        max(x1, x2) <= this.start -> false
        else -> true
    }
}

inline fun <T : Any> T.check(msg: String, cond: (T) -> Boolean): T {
    check(cond(this)) { msg }
    return this
}

inline fun <T : Any> T?.checkNotNull(lazyMsg: () -> String): T {
    return checkNotNull(this, lazyMsg)
}