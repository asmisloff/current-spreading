package ru.vniizht.currentspreading.core.schedule

import org.apache.commons.math3.complex.Complex
import ru.vniizht.currentspreading.core.acnew.square
import ru.vniizht.currentspreading.core.throughout.capacity.integral.indices.MAX_ROUTE_QTY
import ru.vniizht.asuterkortes.dto.PartRegime
import ru.vniizht.asuterkortes.dto.ScheduleParametersDto
import ru.vniizht.asuterkortes.dto.getPartRegimeOfRussianNames
import ru.vniizht.currentspreading.dao.TractiveCalculate
import ru.vniizht.currentspreading.dao.jsonb.TractiveCalculateResult
import java.io.FileWriter
import kotlin.math.abs
import kotlin.math.sqrt

/** Crossing time - Время скрещивания */
const val Tc = 2.0

/** Nonsimultaneous arrival time - время неодновременного прибытия */
const val Tna = 2.0

/**
 * Класс-обертка вокруг стандартного списка, представляющий копию оригинала, отсортированную по заданному критерию.
 * @param lst исходный список
 * @param comparator функция сравнения
 */
class OrderedList<T>(lst: List<T>, comparator: Comparator<T>? = null) : List<T> {

    private val _lst = if (comparator == null) lst else lst.sortedWith(comparator)

    override val size = lst.size
    override fun contains(element: T) = _lst.contains(element)
    override fun containsAll(elements: Collection<T>) = _lst.containsAll(elements)
    override fun get(index: Int) = _lst[index]
    override fun isEmpty() = _lst.isEmpty()
    override fun iterator() = _lst.iterator()
    override fun lastIndexOf(element: T) = _lst.lastIndexOf(element)
    override fun listIterator() = _lst.listIterator()
    override fun listIterator(index: Int) = _lst.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int) = _lst.subList(fromIndex, toIndex)
    override fun indexOf(element: T) = _lst.indexOf(element)

    companion object {
        fun <T> empty() = OrderedList(emptyList<T>())
    }
}

fun <T> List<T>.toOrderedList(comparator: Comparator<T>? = null): OrderedList<T> =
    OrderedList(this, comparator)

data class TrainPosition(
    val coord: Double,
    val activeAmp: Double,
    val trackNumber: Int,
    val massRate: Double,
    val routeIndex: Int,
    val fullAmp: Double?
) {

    fun dcCsa() = -activeAmp * massRate

    fun acCsa(): Complex {
        checkNotNull(fullAmp) {
            "В результатах одного или нескольких тяговых расчетов не найдены реактивные составляющие токов."
        }
        check(fullAmp >= activeAmp) {
            "В результатах тягового расчета содержится один или несколько элементов, у которых активный ток больше полного. " +
                    "Проверьте характеристики локомотива. " +
                    "Точка: $this"
        }
        val reactiveAmp = sqrt(fullAmp.square() - activeAmp.square())
        return Complex(-activeAmp * massRate, reactiveAmp * massRate)
    }

    override fun toString(): String {
        return "{coord=$coord, activeAmp=$activeAmp, trackNumber=$trackNumber, massRate=$massRate, routeIndex=$routeIndex, fullAmp=$fullAmp}"
    }


}

/**
 * Расписание движения
 */
interface MotionSchedule {
    operator fun invoke(t: Double): List<TrainPosition>
    val averagingPeriod: Double
    val computationBoundaries: Pair<Double, Double>

    /**
     * Сохранить график в файл
     */
    fun save(filePath: String, boundaries: Pair<Double, Double>? = null) {
        val trainPositions = mutableListOf<List<String>>()
        val (tLeft, tRight) = boundaries ?: this.computationBoundaries
        var t = tLeft
        while (t <= tRight) {
            trainPositions.add(this(t).map { "${t}\t${it.activeAmp}\t${it.trackNumber}\t${it.coord}\t${it.massRate}\t${it.routeIndex}\n" })
            t += this.averagingPeriod
        }

        FileWriter(filePath).use {
            for (line in trainPositions.flatten()) {
                it.write(line)
            }
        }
    }
}

/**
 * Расписание движения по одному пути
 */
interface SingleTrackSchedule : MotionSchedule {
    val trackNumber: Int
    fun update()
}

/**
 * График движения поездов по нескольким путям
 * @param singleTrackSchedules список однопутевых графиков движения, отсортированный по номеру пути.
 */
class MultiTrackSchedule(val singleTrackSchedules: OrderedList<SingleTrackSchedule>) : MotionSchedule {

    override val averagingPeriod: Double

    init {
        if (singleTrackSchedules.isEmpty())
            throw IllegalArgumentException("MultiTrackSchedule: список расписаний движения поездов на отдельных путях не может быть пустым.")

        val singleTrackAvgPeriods = singleTrackSchedules.map { it.averagingPeriod }.distinct().filter { it > 0 }
        averagingPeriod = when (singleTrackAvgPeriods.size) {
            0 -> -1.0
            1 -> singleTrackAvgPeriods[0]
            else -> throw IllegalStateException("У тяговых расчетов не совпадают периоды усреднения")
        }
    }

    override val computationBoundaries = singleTrackSchedules
        .map { it.computationBoundaries }
        .unzip()
        .let {
            Pair(it.first.minOrNull()!!, it.second.maxOrNull()!!)
        }

    override operator fun invoke(t: Double): List<TrainPosition> = singleTrackSchedules.flatMap { it(t) }

    companion object {
        fun fromDtos(
            dtos: List<ScheduleParametersDto>,
            tractiveCalculates: List<TractiveCalculate>,
            interval: Int? = null,
            tLeft: Double? = null,
            tRight: Double? = null
        ): MultiTrackSchedule {
            assert(dtos.size == tractiveCalculates.size)

            data class TractiveResultAndScheduleParams(val tc: TractiveCalculate, val sp: ScheduleParametersDto)

            val srcData = HashMap<Int, MutableList<TractiveResultAndScheduleParams>>()
            for (calc in tractiveCalculates) {
                val sp = dtos.find { it.tractionCountId == calc.id }!!
                if (sp.totalCount > 0) { // 0 может быть при опции "окно"
                    srcData
                        .getOrPut(calc.singleTrack.trackNumber) { mutableListOf() }
                        .add(TractiveResultAndScheduleParams(calc, sp))
                }
            }

            val lst = mutableListOf<SingleTrackSchedule>()
            for ((trackNumber, entries) in srcData) {
                when (entries.size) {
                    1 -> { // Односторонний режим
                        val (tc, sp) = entries[0]
                        lst.add(
                            USingleTrackSchedule(
                                trackNumber = trackNumber,
                                nRoutes = when {
                                    sp.totalCount > MAX_ROUTE_QTY || sp.totalCount < 0 -> MAX_ROUTE_QTY
                                    else -> sp.totalCount
                                },
                                tc = tc,
                                interval = interval?.toDouble() ?: sp.interval.toDouble(),
                                interleaving = getPartRegimeOfRussianNames(sp.regime),
                                largestMass = sp.largestMass.toDouble(),
                                middleMass = sp.middleMass.toDouble(),
                                connected = sp.connected,
                                tLeft = tLeft,
                                tRight = tRight
                            )
                        )
                    }

                    2 -> { // двусторонний частично-пакетный режим
                        val pack = entries.minByOrNull { it.sp.totalCount }!!
                        val flow = entries.maxByOrNull { it.sp.totalCount }!!
                        lst.add(
                            BSingleTrackSchedule(
                                trackNumber = trackNumber,
                                packageNRoutes = when {
                                    pack.sp.totalCount > MAX_ROUTE_QTY || pack.sp.totalCount < 0 -> MAX_ROUTE_QTY
                                    else -> pack.sp.totalCount
                                },
                                packageTc = pack.tc,
                                flowTc = flow.tc,
                                packageInterval = interval?.toDouble() ?: pack.sp.interval.toDouble(),
                                packageInterleaving = getPartRegimeOfRussianNames(pack.sp.regime),
                                flowInterleaving = getPartRegimeOfRussianNames(flow.sp.regime),
                                packageLargestMass = pack.sp.largestMass.toDouble(),
                                packageMiddleMass = pack.sp.middleMass.toDouble(),
                                flowLargestMass = flow.sp.largestMass.toDouble(),
                                flowMiddleMass = flow.sp.middleMass.toDouble(),
                                packageConnected = pack.sp.connected,
                                flowConnected = flow.sp.connected,
                                tLeft = tLeft,
                                tRight = tRight
                            )
                        )
                    }

                    else -> throw IllegalStateException("Для пути №$trackNumber задано более двух направлений движения.")
                }
            }
            return MultiTrackSchedule(OrderedList(lst) { x1, x2 -> x1.trackNumber - x2.trackNumber })
        }
    }
}

/**
 *  Unidirectional single track Schedule
 * @param nRoutes количество поездов.
 * */
class USingleTrackSchedule(
    override val trackNumber: Int,
    val nRoutes: Int,
    var tc: TractiveCalculate,
    val interval: Double,
    val interleaving: PartRegime,
    val largestMass: Double,
    val middleMass: Double,
    val connected: Boolean,
    val tLeft: Double? = null,
    val tRight: Double? = null
) : SingleTrackSchedule {

    override lateinit var computationBoundaries: Pair<Double, Double>

    override val averagingPeriod = tc.result.averagingPeriod

    init {
        assert(nRoutes >= 0)
        assert(trackNumber > 0)
        assert(interval >= 0)

        update()
    }

    override fun update() {
        val t0 = tc.result.duration() - 4 // 4 - время скрещивания + время неодновр. прибытия
        computationBoundaries = Pair(tLeft ?: t0, tRight ?: (t0 + 120))
    }

    override operator fun invoke(t: Double): List<TrainPosition> {
        check(t >= 0) { "Момент времени на графике движения не может быть отрицательным числом" }
        val result = mutableListOf<TrainPosition>()
        var routeIndex = (t / interval).toInt() + 1 // порядковый номер кривой на графике
        var tt =
            t % interval // время в системе отсчета, начало которой совмещено с началом текущей кривой на графике движения
        while (routeIndex >= 1) {
            if (nRoutes == 0 || routeIndex < nRoutes) {
                val i = tcrEltIndex(tt, tc.result) ?: break
                val elt = tc.result.elements[i]
                val mRate = massRate(routeIndex, largestMass, middleMass, tc.weight.toDouble(), interleaving, connected)
                result.add(
                    TrainPosition(
                        coord = elt.c,
                        activeAmp = elt.actA,
                        trackNumber = trackNumber,
                        massRate = mRate,
                        routeIndex = routeIndex,
                        fullAmp = elt.fullA
                    )
                )
            }
            tt += interval
            --routeIndex
        }
        return result
    }

}

/**
 *  Bidirectional single track Schedule
 * */
class BSingleTrackSchedule(
    override val trackNumber: Int,
    val packageNRoutes: Int,
    var packageTc: TractiveCalculate,
    var flowTc: TractiveCalculate,
    val packageInterval: Double,
    val packageInterleaving: PartRegime,
    val flowInterleaving: PartRegime,
    val packageLargestMass: Double,
    val packageMiddleMass: Double,
    val flowLargestMass: Double,
    val flowMiddleMass: Double,
    val packageConnected: Boolean,
    val flowConnected: Boolean,
    val tLeft: Double? = null,
    val tRight: Double? = null
) : SingleTrackSchedule {

    private val routes = mutableListOf<Route>()

    private var packageBandWidth: Double // ширина полосы пакетного графика вдоль временной оси

    override lateinit var computationBoundaries: Pair<Double, Double>

    override val averagingPeriod: Double

    private var routeIndex = 0

    init {
        averagingPeriod = packageTc.result.averagingPeriod
        if (averagingPeriod != flowTc.result.averagingPeriod)
            throw IllegalStateException("У тяговых расчетов не совпадают периоды усреднения")

        packageBandWidth = (packageNRoutes - 1) * packageInterval

        createRoutes()
    }

    private fun createRoutes() {
        val pStart = packageTc.result.periods.first().coordinate
        val pFinish = packageTc.result.periods.last().coordinate
        val fStart = flowTc.result.periods.first().coordinate
        val fFinish = flowTc.result.periods.last().coordinate
        assert(pStart == fFinish && pFinish == fStart)
        assert(packageNRoutes >= 0)

        makePackageRouts()
        makeFlowRoutes()

        computationBoundaries = computationBoundaries()
    }

    override fun update() {
        routeIndex = 0
        routes.clear()
        createRoutes()
    }

    override fun invoke(t: Double): List<TrainPosition> {
        return routes.mapNotNull { it(t) }
    }

    private fun packageStartTime() = flowTc.result.duration() + Tna

    private fun makePackageRouts() {
        var t = packageStartTime()
        for (i in 0 until packageNRoutes) {
            val massRate = massRate(
                routeIndex = i,
                largestMass = packageLargestMass,
                mediumMass = packageMiddleMass,
                referenceMass = packageTc.weight.toDouble(),
                interleaving = packageInterleaving,
                connected = packageConnected
            )
            routes.add(Route(packageTc, t, 0.0, 0.0, trackNumber, massRate, ++routeIndex))
            t += packageInterval
        }
    }

    private fun makeFlowRoutes() {
        assert(routes.size > 0) // пакетные поезда должны быть уже созданы

        val firstRouteInPackage = routes.first()
        val lastRouteInPackage = routes.last()

        var flowRouteIndex = 0
        val r0 = Route( // маршрут из нулевой точки - не пересекается с пакетом
            tc = flowTc,
            start = 0.0,
            waitingStart = 0.0,
            waitingEnd = 0.0,
            trackNumber = trackNumber,
            massRate = massRate(
                routeIndex = ++flowRouteIndex,
                largestMass = flowLargestMass,
                mediumMass = flowMiddleMass,
                referenceMass = flowTc.weight.toDouble(),
                interleaving = flowInterleaving,
                connected = flowConnected
            ),
            routeIndex = ++routeIndex
        )
        routes.add(r0)

        /**
         * Список узловых станций.
         * При построении графика проходить по этому списку нужно в направлении движения пакета, поэтому он обращается.
         * */
        val loopStations = flowTc.result.periods.subList(1, flowTc.result.periods.lastIndex).reversed()
        for (ls in loopStations) {
            val tCross = arrivalTime(firstRouteInPackage, ls.coordinate)
            val waitingStart = tCross - Tna
            val tls = arrivalTime(r0, ls.coordinate) // время хода потокового поезда до данной узловой станции
            val start = waitingStart - tls
            routes.add(
                Route(
                    tc = flowTc,
                    start = start,
                    waitingStart = waitingStart,
                    waitingEnd = waitingStart + Tna + packageBandWidth + Tc,
                    trackNumber = trackNumber,
                    massRate = massRate(
                        routeIndex = ++flowRouteIndex,
                        largestMass = flowLargestMass,
                        mediumMass = flowMiddleMass,
                        referenceMass = flowTc.weight.toDouble(),
                        interleaving = flowInterleaving,
                        connected = flowConnected
                    ),
                    ++routeIndex
                )
            )
        }

        /* Последний маршрут в потоке - после пропуска пакета */
        routes.add(
            Route(
                tc = flowTc,
                start = lastRouteInPackage.start + lastRouteInPackage.tc.result.duration() + Tc,
                waitingStart = 0.0,
                waitingEnd = 0.0,
                trackNumber = trackNumber,
                massRate = massRate(
                    routeIndex = ++flowRouteIndex,
                    largestMass = flowLargestMass,
                    mediumMass = flowMiddleMass,
                    referenceMass = flowTc.weight.toDouble(),
                    interleaving = flowInterleaving,
                    connected = flowConnected
                ),
                ++routeIndex
            )
        )
    }

    /** Время прибытия маршрута в заданную координату
     * @param route маршрут
     * @param coord ордината на графике движения
     * */
    private fun arrivalTime(route: Route, coord: Double): Double {
        val elts = route.tc.result.elements
        val dt = route.tc.result.averagingPeriod

        if (elts.isEmpty()) throw IllegalStateException("В тяговом расчете отсутствуют данные")
        if (elts.size == 1) return 0.0

        val asc = 1
        val desc = -1
        val sortingOrder = if (elts[0].c < elts[1].c) asc else desc
        var start = 0
        var end = elts.lastIndex

        when (sortingOrder) {
            asc -> {
                if (coord < elts[0].c) return 0.0
                if (coord > elts.last().c) return dt * elts.size
            }
            desc -> {
                if (coord > elts[0].c) return 0.0
                if (coord < elts.last().c) return dt * elts.size
            }
        }

        while (end - start > 1) {
            val i = (end + start) / 2
            val c = elts[i].c
            when {
                abs(coord - c) <= 1e-3 -> return i * dt + route.start

                sortingOrder == asc -> {
                    when {
                        coord > c -> start = i
                        coord < c -> end = i
                    }
                }

                sortingOrder == desc -> {
                    when {
                        coord < c -> start = i
                        coord > c -> end = i
                    }
                }
            }
        }

        /**
         * Цикл завершен, следовательно, точное совпадение не найдено.
         * Цикл остановился между двумя ближайшими значениями - вернуть среднее.
         * */
        return (start + end) / 2.0 * dt + route.start
    }

    private fun computationBoundaries(): Pair<Double, Double> {
        val flowLimitTransferTime = when {
            this.tLeft == null || this.tRight == null -> 0.0
            else -> flowTc.result.periods // Время хода потокового поезда по лим. перегону
                .map { it.inTime }
                .maxOrNull()
                ?: throw IllegalStateException("На участке не заданы узловые станции")
        }

        val tLeft: Double = when {
            this.tLeft != null -> this.tLeft
            else -> {
                val tFlow = flowTc.result.duration() // полное время хода потокового поезда
                tFlow - flowLimitTransferTime
            }
        }

        val tRight = when {
            this.tRight != null -> this.tRight
            else -> {
                val packageLimitTransferTime = packageTc.result.periods // время хода пакетного поезда по лим. перегону
                    .map { it.inTime }
                    .maxOrNull()
                    ?: throw IllegalStateException("На участке не заданы узловые станции")
                tLeft + packageTc.result.duration() + packageBandWidth + flowLimitTransferTime + packageLimitTransferTime + Tc + Tna
            }
        }

        return Pair(tLeft, tRight)
    }

}

/** Маршрут одного поезда - кривая на графике (двустороннего) движения.
 *  @param tc результаты тягового расчета для данного пути в данном направлении
 *  @param start время начала поездки
 *  @param waitingStart время начала периода ожидания при пропуске пакета
 *  @param waitingEnd время завершения периода ожидания при пропуске пакета
 *  @param trackNumber номер пути
 *  @param massRate отношение средней массы к максимальной для данного поезда
 * */
class Route(
    var tc: TractiveCalculate,
    val start: Double,
    val waitingStart: Double,
    val waitingEnd: Double,
    val trackNumber: Int,
    val massRate: Double,
    val routeIndex: Int
) {

    init {
        assert(waitingStart < tc.result.duration() + start)
    }

    private val finish = start + tc.result.duration() + (waitingEnd - waitingStart)

    operator fun invoke(t: Double): TrainPosition? {
        if (t > finish || t < start) {
            return null
        }

        val tt = when {
//            t == finish -> { // без этого условия при больших шагах дискретизации (0,5 и подобн.) возможен выход за правую границу tcr.elements
//                tc.result.elements.lastIndex * tc.result.averagingPeriod // при таком tt index будет равен tcr.elements.lastIndex
//            }
            t < waitingStart -> t - start
            t > waitingEnd -> t - start - (waitingEnd - waitingStart)
            else ->
                return null
        }

        val index = tcrEltIndex(tt, tc.result) ?: return null
        val elt = if (index > tc.result.elements.lastIndex) tc.result.elements.last() else tc.result.elements[index]
        return TrainPosition(
            coord = elt.c,
            activeAmp = elt.actA,
            trackNumber = trackNumber,
            massRate = massRate,
            routeIndex = routeIndex,
            fullAmp = elt.fullA
        )
    }
}

fun TractiveCalculateResult.duration() = elements.size * averagingPeriod

/**
 * Массовый коэффициент с учетом чередования поездов и флага "Соед."
 * */
fun massRate(
    routeIndex: Int,
    largestMass: Double,
    mediumMass: Double,
    referenceMass: Double,
    interleaving: PartRegime,
    connected: Boolean
): Double {
    val fullMassTrainIndex = when (interleaving) {
        PartRegime.ALL -> 1
        PartRegime.NONE -> 0
        PartRegime.DOUBLE -> 2
        PartRegime.TRIPLE -> 3
        PartRegime.FOURTH -> 4
    }
    val isFullMass = if (fullMassTrainIndex == 0) false else routeIndex % fullMassTrainIndex == 0
    return when {
        isFullMass && connected -> 2.0 * largestMass / referenceMass
        isFullMass && !connected -> 1.0 * largestMass / referenceMass
        else -> mediumMass / referenceMass
    }
}

/**
 * Индекс элемента в списке результатов тягового расчета
 * @param t момент времени, мин.
 * @param tcr - результат тягового расчета
 */
fun tcrEltIndex(t: Double, tcr: TractiveCalculateResult): Int? {
    return when (val index = (t / tcr.averagingPeriod).toInt()) {
        in 0..tcr.elements.lastIndex -> index
        else -> null
    }
}