package ru.vniizht.currentspreading.core.circuit

import org.apache.commons.math3.complex.Complex
import org.jgrapht.alg.cycle.StackBFSFundamentalCycleBasis
import org.jgrapht.graph.DirectedWeightedMultigraph
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import ru.vniizht.currentspreading.core.circuit.Block
import ru.vniizht.asuterkortes.counter.circuit.CircuitEdge
import ru.vniizht.asuterkortes.counter.circuit.CircuitNode
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.StringWriter

/**
 * Базовый класс электросхемы
 */
open class Circuit<T, E : CircuitEdge<T, N>, N : CircuitNode<T>>(clazz: Class<E>) {
    val blocks = mutableListOf<Block<T, E, N>>()
    var graph = DirectedWeightedMultigraph<N, CircuitEdge<T, N>>(clazz)

    var fundamentalCycles: List<Map<Int, Double>>? = null
        get() {
            if (field == null) computeFundamentalCycles()
            return field
        }

    fun getEdges() = graph.edgeSet().sortedBy { it.index }

    fun addNode(node: N): Boolean {
        if (graph.addVertex(node)) {
            return true
        }
        return false
    }

    fun renumberNodes(): Int {
        var index = 0
        graph.vertexSet().forEach { it.index = index++ }
        return index
    }

    fun renumberEdges(): Int {
        var index = 0
        graph.edgeSet()
            .forEach { it.index = index++ }
        return index - 1
    }

    protected fun computeFundamentalCycles(): List<Map<Int, Double>> {
        val result = mutableListOf<Map<Int, Double>>()
        val cycles = StackBFSFundamentalCycleBasis(graph)
            .cycleBasis
            .cyclesAsGraphPaths

        cycles.forEach { c ->
            val nodes = c.vertexList
            val edges = c.edgeList
            val map = HashMap<Int, Double>()

            for (i in 0 until edges.size) {
                val e = edges[i]
                val node = nodes[i]
                map[e.index] =
                    if (e.getSourceNode() == node)
                        1.0
                    else
                        -1.0
            }
            result.add(map)
        }

        fundamentalCycles = result
        return result
    }

    fun printCycles(filter: String) {
        val names = (fundamentalCycles ?: computeFundamentalCycles())
            .map { c ->
                val indexes = c.keys
                indexes
                    .map { i ->
                        graph.edgeSet()
                            .filter { it.index == i }
                            .map { "${if (c[i] == -1.0) "-" else ""}${it.name}" }
                            .first()
                    }
            }

        println(
            names.filter lambda@{
                for (s in it) {
                    if (s.contains(filter)) return@lambda true
                }
                false
            }
        )
    }

    private val exporter = DOTExporter<N, CircuitEdge<T, N>> { it.index.toString() }
    fun toDOT(printToConsole: Boolean = false): String {
        exporter.setVertexAttributeProvider { node ->
            val color = when {
                node.breaking -> "red"
                node.trackNumber == 0 -> "khaki4"
                node.trackNumber == 1 -> "#B0C4DE"
                node.trackNumber == 2 -> "#F4A460"
                node.trackNumber == 3 -> "#98FB98"
                node.trackNumber == 4 -> "#66CDAA"
                node.trackNumber == 5 -> "#FFC0CB"
                node.trackNumber == 6 -> "#A9A9A9"
                node.trackNumber == 1001 -> "#FF66CC"
                node.trackNumber == 1002 -> "#FF00CC"
                node.trackNumber == 1003 -> "#FF33CC"
                node.trackNumber == 1004 -> "#CC6699"
                node.trackNumber == 1005 -> "#CC0099"
                node.trackNumber == 1006 -> "#990066"
                node.trackNumber >= 10_000 -> "white"
                else -> "grey"
            }

            mapOf(
                "label" to DefaultAttribute.createAttribute(
                    "{${node.index} (${node.coordinate} км)|Путь: ${node.trackNumber}|${node.label}}"
                ),
                "shape" to DefaultAttribute.createAttribute("record"),
                "style" to DefaultAttribute.createAttribute("filled"),
                "fillcolor" to DefaultAttribute.createAttribute(color)
            )
        }

        exporter.setEdgeAttributeProvider {
            val label = "${it.index}(${it.name.replace("CN_0", "Rail")})"
            val color = when {
                it.emf != Complex.ZERO -> "red"
                it.csa != Complex.ZERO -> "blue"
                it.disconnected() -> "grey"
                it.getSourceNode().trackNumber == 0 -> "khaki4"
                it.getSourceNode().trackNumber == 1 -> "#B0C4DE"
                it.getSourceNode().trackNumber == 2 -> "#F4A460"
                it.getSourceNode().trackNumber == 3 -> "#98FB98"
                it.getSourceNode().trackNumber == 4 -> "#66CDAA"
                it.getSourceNode().trackNumber == 5 -> "#FFC0CB"
                it.getSourceNode().trackNumber == 6 -> "#A9A9A9"
                it.getSourceNode().trackNumber == 1001 -> "hotpink1"
                it.getSourceNode().trackNumber == 1002 -> "hotpink3"
                else -> "black"
            }
            mapOf(
                "label" to DefaultAttribute.createAttribute(label),
                "color" to DefaultAttribute.createAttribute(color),
                "fontcolor" to DefaultAttribute.createAttribute(color),
            )
        }

        val writer = StringWriter()
        var dot: String
        renumberEdges()
        renumberNodes()
        writer.use {
            exporter.exportGraph(graph, writer)
            dot = writer.toString()
            if (printToConsole) {
                println(dot)
            } else {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(dot), null)
            }
        }

        return dot.trim().replace("\r", "")
    }

}
