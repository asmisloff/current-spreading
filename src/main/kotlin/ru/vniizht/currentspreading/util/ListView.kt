package ru.vniizht.currentspreading.util

/**
 * Отображение части списка. Неструктурные изменения в оригинальном списке транслируются в отображение.
 * Структурные изменения в оригинальном списке приведут к неопределенному поведению.
 * @param list оригинальный список
 * @param ranges диапазоны индексов оригинального списка, которые будут включены в отображение.
 */
class ListView<T>(
    private val list: List<T>,
    vararg ranges: IntRange
) : List<T> {

    private val srcIndices = ranges.flatMap { it.toList() }

    /** Индекс первого элемента в исходном списке */
    val headIndex = when {
        isEmpty() -> -1
        else -> srcIndices.first()
    }

    /** Индекс последнего элемента в исходном списке */
    val tailIndex = when {
        isEmpty() -> -1
        else -> srcIndices.last()
    }

    override val size = srcIndices.size

    override fun contains(element: T): Boolean {
        for (i in srcIndices) {
            if (element == list[i]) return true
        }
        return false
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        for (elt in elements) {
            if (!contains(elt)) return false
        }
        return true
    }

    override fun get(index: Int) = list[srcIndices[index]]

    override fun indexOf(element: T): Int {
        for ((ii, i) in srcIndices.withIndex()) {
            if (element == list[i]) return ii
        }
        return -1
    }

    override fun isEmpty() = srcIndices.isEmpty()

    override fun iterator() = ListViewIterator()

    override fun lastIndexOf(element: T): Int {
        for (i in size - 1 downTo 0) {
            if (get(i) == element) return i
        }
        return -1
    }

    override fun listIterator() = ListViewIterator()

    override fun listIterator(index: Int) = ListViewIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int) = (fromIndex until toIndex).map { get(it) }

    override fun toString(): String {
        return "[${this.joinToString { it.toString() }}]"
    }

    inner class ListViewIterator(private var index: Int = -1) : ListIterator<T> {

        override fun hasNext() = !isEmpty() && index < srcIndices.lastIndex

        override fun hasPrevious() = index > 0

        override fun next() = when {
            hasNext() -> get(++index)
            else -> throw NoSuchElementException()
        }

        override fun nextIndex() = index + 1

        override fun previous() = when {
            index > 0 -> get(--index)
            else -> throw NoSuchElementException()
        }

        override fun previousIndex() = index - 1

    }

}

fun <T> List<T>.listView(vararg ranges: IntRange) = ListView(this, *ranges)

/**
 * @return IntRange, состоящий из заданного количества последовательных целых чисел, начиная с n
 *
 * Пример: (10 starting from 1) -> 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
 */
infix fun Int.startingFrom(n: Int) = n until (this + n)
