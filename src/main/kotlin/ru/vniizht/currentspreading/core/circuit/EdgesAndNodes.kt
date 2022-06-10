package ru.vniizht.asuterkortes.counter.circuit

import org.apache.commons.math3.complex.Complex
import org.jgrapht.graph.DefaultWeightedEdge
import ru.vniizht.currentspreading.core.acnew.isInf
import ru.vniizht.currentspreading.util.round

interface ICircuitNode {
    var coordinate: Double
    val trackNumber: Int
    var breaking: Boolean
    var index: Int
}

/**
 * Узел электрической цепи
 */
abstract class CircuitNode<T>(
    override var coordinate: Double,
    override val trackNumber: Int,
    val label: String,
    override var breaking: Boolean = false
) : ICircuitNode {
    override var index: Int = -1
    abstract var p: T? // потенциал в узле
}

class CircuitNodeDC(coordinate: Double, trackNumber: Int, label: String, breaking: Boolean) :
    CircuitNode<Double>(coordinate.round(3), trackNumber, label, breaking) {
    override var p: Double? = 0.0
}

class CircuitNodeAC(coordinate: Double, trackNumber: Int, label: String, breaking: Boolean) :
    CircuitNode<Complex>(coordinate, trackNumber, label, breaking) {

    init {
        this.coordinate = coordinate.round(3)
    }

    override var p: Complex? = null

    override fun equals(other: Any?): Boolean = when {
        other == null -> false
        other !is CircuitNodeAC -> false
        other.trackNumber == 0 && this.trackNumber == 0 -> true
        else -> this === other
    }

    override fun hashCode() = when {
        trackNumber == 0 -> 0
        else -> super.hashCode()
    }

}

class NodeFactory {

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T, N : CircuitNode<T>> createInstance(
            clazz: Class<N>,
            coordinate: Double,
            trackNumber: Int,
            label: String,
            breaking: Boolean
        ): N = when (clazz) {
            CircuitNodeAC::class.java -> CircuitNodeAC(coordinate, trackNumber, label, breaking) as N
            CircuitNodeDC::class.java -> CircuitNodeDC(coordinate, trackNumber, label, breaking) as N
            else -> throw IllegalArgumentException("Неизвестный тип узла: $clazz")
        }
    }

}

interface ICircuitEdge {
    var index: Int
    fun getSourceNode(): ICircuitNode
    fun getTargetNode(): ICircuitNode
}

/**
 * Представление обобщенного сопротивления в форме ветви графа
 *
 * @param name
 * наименование обобщенного сопротивления
 */
abstract class CircuitEdge<T, N : CircuitNode<T>>(val name: String) :
    DefaultWeightedEdge(), ICircuitEdge {

    override var index: Int = -1

    /**
     * Номинал обобщенного сопротивления
     */
    abstract var value: T

    /**
     * ЭДС
     */
    abstract val emf: T

    /**
     * Сила тока источника тока (current source amperage) в ветви
     */
    abstract val csa: T

    abstract var amp: T?

    @Suppress("UNCHECKED_CAST")
    override fun getSourceNode(): N = source as N

    @Suppress("UNCHECKED_CAST")
    override fun getTargetNode(): N = target as N

    abstract fun disconnected(): Boolean

}

class CircuitEdgeDC(
    name: String, override var value: Double, override var emf: Double = 0.0, override var csa: Double = 0.0
) : CircuitEdge<Double, CircuitNodeDC>(name) {

    override var amp: Double? = 0.0

    override fun disconnected() = value < 1e6

}

class CircuitEdgeAC(
    name: String,
    override var value: Complex,
    override var emf: Complex = Complex.ZERO,
    override var csa: Complex = Complex.ZERO
) : CircuitEdge<Complex, CircuitNodeAC>(name) {

    override var amp: Complex? = null

    override fun disconnected() = value.isInf()

}
