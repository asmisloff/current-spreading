package ru.vniizht.currentspreading.core.circuit

import org.jgrapht.Graphs
import org.jgrapht.graph.DirectedWeightedMultigraph
import ru.vniizht.asuterkortes.counter.circuit.CircuitEdge
import ru.vniizht.asuterkortes.counter.circuit.CircuitNode
import ru.vniizht.asuterkortes.counter.circuit.NodeFactory

/**
 * Базовый класс блоков схемы
 */
abstract class Block<T, E : CircuitEdge<T, N>, N : CircuitNode<T>>(classE: Class<E>, val classN: Class<N>) {
    private var maxNodeIndex = 0
    private var maxEdgeIndex = 0
    abstract val axisCoordinate: Double
    abstract val branchIndex: Int
    val graph = DirectedWeightedMultigraph<N, E>(classE)
    var description: String = ""

    fun addNode(node: N): Boolean {
        node.index = maxNodeIndex++
        return graph.addVertex(node)
    }

    fun addNode(
        coordinate: Double,
        trackNumber: Int,
        label: String,
        breaking: Boolean = false,
        index: Int = maxNodeIndex + 1
    ): N {
        val node = NodeFactory.createInstance(classN, coordinate, trackNumber, label, breaking)
        node.index = index
        maxNodeIndex = maxNodeIndex.coerceAtLeast(index)
        graph.addVertex(node)
        return node
    }

    fun addEdge(source: N, target: N, edge: E): E {
        edge.index = maxEdgeIndex++
        graph.addEdge(source, target, edge)
        return edge
    }

    fun addEdge(sourceIndex: Int, targetIndex: Int, edge: E): E {
        val source = graph.vertexSet().find { it.index == sourceIndex }
            ?: throw IllegalArgumentException("Неверный индекс узла: $sourceIndex")
        val target = graph.vertexSet().find { it.index == targetIndex }
            ?: throw IllegalArgumentException("Неверный индекс узла: $targetIndex")
        addEdge(source, target, edge)
        return edge
    }

    open fun mergeInto(circuit: Circuit<T, E, N>) {
        Graphs.addGraph(circuit.graph, this.graph)
        circuit.blocks.add(this)
    }

    /**
     * Внести поправку в номер пути с учетом индекса ответвления, в которое входит данный блок.
     *
     *  Соглашение о нумерации путей:
     *
     *  0 - рельс,
     *  1-6 - номера путей основного хода,
     *  1001, 1002 - номера линий ПП в схемах основного хода системы 2х25
     *
     *  10_000 - рельс ответвления №1
     *  20_000 - рельс ответвления №2
     *  и т.д.
     *  10_001 - 10_006 - номера путей ответвления №1
     *  20_001 - 20_006 - номера путей ответвления №2
     *  и т.д.
     *  11_001, 11_002 - номера линий ПП в ответвлении №1 системы 2х25
     *  21_001, 21_002 - номера линий ПП в ответвлении №2 системы 2х25
     *  и т.д.
     *
     * @receiver - номер пути в "локальной" системе отсчета, связанной с ветвью, к которой принадлежит блок.
     */
    fun Int.amendConsideringBranchIndex() = this.amendConsideringBranchIndex(branchIndex)

    fun Int.extractBranchIndex() = this / 10_000

}


/**
 * Внести поправку в номер пути с учетом индекса ответвления, в которое входит данный блок.
 *
 * Соглашение о нумерации путей:
 *
 *  0 - рельс,
 *  1-6 - номера путей основного хода,
 *  1001, 1002 - номера линий ПП в схемах основного хода системы 2х25
 *
 *  10_000 - рельс ответвления №1
 *  20_000 - рельс ответвления №2
 *  и т.д.
 *  10_001 - 10_006 - номера путей ответвления №1
 *  20_001 - 20_006 - номера путей ответвления №2
 *  и т.д.
 *  11_001, 11_002 - номера линий ПП в ответвлении №1 системы 2х25
 *  21_001, 21_002 - номера линий ПП в ответвлении №2 системы 2х25
 *  и т.д.
 *
 * @receiver - номер пути в "локальной" системе отсчета, связанной с ветвью, к которой принадлежит блок.
 * @param branchIndex индекс ветви
 */
fun Int.amendConsideringBranchIndex(branchIndex: Int) = 10_000 * branchIndex + this

data class BranchFeederInfo<T>(
    val branchIndex: Int,
    val trackNumber: Int,
    var connectionPoint: Double,
    var feederResistance: T,
    var switchedOn: Boolean = true
)
