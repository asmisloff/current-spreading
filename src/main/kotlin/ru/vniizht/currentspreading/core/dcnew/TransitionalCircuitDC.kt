package ru.vniizht.asuterkortes.counter.dcnew

import org.ejml.data.DMatrixSparseCSC
import org.ejml.data.DMatrixSparseTriplet
import org.ejml.ops.DConvertMatrixStruct
import org.ejml.sparse.csc.CommonOps_DSCC
import ru.vniizht.currentspreading.core.circuit.Circuit
import ru.vniizht.asuterkortes.counter.circuit.CircuitEdgeDC
import ru.vniizht.asuterkortes.counter.circuit.CircuitNodeDC
import ru.vniizht.currentspreading.core.dcnew.BlockSSDC
import ru.vniizht.currentspreading.core.dcnew.NetworkResistanceRangeDC
import ru.vniizht.currentspreading.util.round

/**
 * Схема МПЗ
 */
class TransitionalCircuitDC : Circuit<Double, CircuitEdgeDC, CircuitNodeDC>(CircuitEdgeDC::class.java) {

    var Z: DMatrixSparseCSC? = null
    var K: DMatrixSparseCSC? = null
    var K_: DMatrixSparseTriplet? = null
    var I: DMatrixSparseCSC? = null
    var E: DMatrixSparseCSC? = null
    var KTrans: DMatrixSparseCSC? = null
    var ZI: DMatrixSparseCSC? = null
    var E_ZI: DMatrixSparseCSC? = null
    var A: DMatrixSparseCSC? = null
    var B: DMatrixSparseCSC? = null
    var Icc: DMatrixSparseCSC? = null
    var J: DMatrixSparseCSC? = null
    var dU: DMatrixSparseCSC? = null

    var ss1IssEdge: CircuitEdgeDC? = null
    var ss1HalfIssEdge: CircuitEdgeDC? = null
    var ss2IssEdge: CircuitEdgeDC? = null
    var ss2HalfIssEdge: CircuitEdgeDC? = null

    var ss1RightFeederAmperage = 0.0
    var ss2LeftFeederAmperage = 0.0
    var ss1DeltaFA = Double.POSITIVE_INFINITY
    var ss2DeltaFA = Double.POSITIVE_INFINITY

    private val INF = 9e7

    companion object {
        fun cnResistance(
            startCoord: Double,
            endCoord: Double,
            networkResistanceRanges: List<NetworkResistanceRangeDC>
        ): Double {
            assert(startCoord <= endCoord)
            var res = 0.0
            var x0 = startCoord
            var i = 0

            while (x0 < endCoord) {
                check(i < networkResistanceRanges.size) {
                    "Ошибка трассировки схемы. Для участка $startCoord-$endCoord км, не задана контактная сеть (последняя секция заканчивается на ${networkResistanceRanges.last().xMax} км)."
                }
                if (networkResistanceRanges[i].xMax < x0) {
                    ++i
                    continue
                }
                res += networkResistanceRanges[i].r * (minOf(networkResistanceRanges[i].xMax, endCoord) - x0)
                x0 = minOf(networkResistanceRanges[i].xMax, endCoord)
                ++i
            }

            return res
        }
    }

    fun wire(network: MutableMap<Int, MutableList<NetworkResistanceRangeDC>>) {
        val m = mutableMapOf<Int, MutableList<CircuitNodeDC>>()
        this.graph.vertexSet()
            .sortedBy { it.coordinate }
            .forEach {
                val tn = it.trackNumber
                if (tn >= 0) { // только узлы, принадлежащие контактной сети
                    if (tn !in m.keys) {
                        m[tn] = mutableListOf()
                    }
                    m[tn]!!.add(it)
                }
            }

        m.keys
            .sortedBy { it }
            .forEach { tn ->
                m[tn]!!.zipWithNext().forEachIndexed { i, (source, target) ->
                    if (!(source.breaking || target.breaking)) {
                        this.graph.addEdge(
                            source,
                            target,
                            CircuitEdgeDC(
                                name = "CN_${tn}_$i",
                                value = cnResistance(
                                    startCoord = source.coordinate,
                                    endCoord = target.coordinate,
                                    networkResistanceRanges = network[tn]
                                        ?: throw RuntimeException("Ошибка сборки схемы: нет данных контактной сети для пути №$tn")
                                )
                            )
                        )
                    }
                }
            }
        this.renumberNodes()
        this.renumberEdges()
    }

    fun buildMatrices() {
        val cycles = fundamentalCycles ?: throw RuntimeException("Схема не замкнута.")
        val edges = getEdges()
        val ss1 = blocks.first() as? BlockSSDC
            ?: throw RuntimeException("Ошибка построения схемы МПЗ: на первом месте должна быть подстанция.")
        val ss2 = blocks.last() as? BlockSSDC
            ?: throw RuntimeException("Ошибка построения схемы МПЗ: на первом месте должна быть подстанция.")

        if (edges.isEmpty()) throw RuntimeException("Схема не содержит элементов.")


        Z = DMatrixSparseCSC(edges.size, edges.size)
        I = DMatrixSparseCSC(edges.size, 1)
        E = DMatrixSparseCSC(edges.size, 1)

        edges.forEachIndexed { i, edge ->
            Z!![i, i] = edge.value
            if (edge.csa != 0.0) {
                I!![edge.index, 0] = edge.csa
            }
            if (edge.emf != 0.0) {
                E!![edge.index, 0] = edge.emf
            }
        }

        val kNonZeroQty = cycles
            .map { it.size }
            .reduce { acc, it -> acc + it }
        K = DMatrixSparseCSC(cycles.size, edges.size, kNonZeroQty)
        KTrans = DMatrixSparseCSC(edges.size, kNonZeroQty, cycles.size)
        K_ = DMatrixSparseTriplet(cycles.size, edges.size, kNonZeroQty)
        cycles.forEachIndexed { row, c ->
            c.forEach { (col, value) ->
                K_!!.addItem(row, col, value)
            }
        }
        DConvertMatrixStruct.convert(K_, K)

        A = DMatrixSparseCSC(cycles.size, cycles.size)
        B = DMatrixSparseCSC(cycles.size, 1)

        ZI = DMatrixSparseCSC(edges.size, 1)
        E_ZI = DMatrixSparseCSC(cycles.size, 1)

        Icc = DMatrixSparseCSC(cycles.size, 1)
        J = DMatrixSparseCSC(edges.size, 1)
        dU = DMatrixSparseCSC(edges.size, 1)

        ss1IssEdge = ss1.issEdge
        ss1HalfIssEdge = ss1.halfIssEdge
        ss2IssEdge = ss2.issEdge
        ss2HalfIssEdge = ss2.halfIssEdge
    }

    fun updateSSCardinalParameters() {
        if (Z == null || E == null) return
        val z = this.Z!!
        val e = this.E!!

        val ssl = blocks.first() as BlockSSDC
        val ssr = blocks.last() as BlockSSDC
        for (ss in listOf(ssl, ssr)) {
            val i = ss.zssEdge.index
            z.set(i, i, ss.zSS)
            e.set(i, 0, ss.vOut)
        }
    }

    fun solve() {
        KTrans = CommonOps_DSCC.transpose(K, KTrans, null)

        A = CommonOps_DSCC.mult(
            CommonOps_DSCC.mult(K, Z, null),
            KTrans,
            A
        )

        B = CommonOps_DSCC.mult(
            K,
            CommonOps_DSCC.add(
                1.0, E, -1.0,
                CommonOps_DSCC.mult(Z, I, ZI), E_ZI, null, null
            ),
            B
        )

        CommonOps_DSCC.solve(A, B, Icc)
        CommonOps_DSCC.mult(KTrans, Icc, J)
        CommonOps_DSCC.mult(Z, J, dU)

        val ss1 = blocks.first() as BlockSSDC
        val ss2 = blocks.last() as BlockSSDC

        var tmp = ss1RightFeederAmperage
        ss1RightFeederAmperage = ss1.rightFeederEdges.sumOf { J!![it.index, 0] }
        ss1DeltaFA = ss1RightFeederAmperage - tmp

        tmp = ss2LeftFeederAmperage
        ss2LeftFeederAmperage = ss2.leftFeederEdges.sumOf { J!![it.index, 0] }
        ss2DeltaFA = ss2LeftFeederAmperage - tmp
    }

    fun computeNodePotentials() {
        val ssLeft = blocks.first() as BlockSSDC
        val visitedNodes = mutableSetOf<CircuitNodeDC>()
        ssLeft.gnd.p = 0.0
        crossEdge(ssLeft.zssEdge, ssLeft.gnd, visitedNodes)
        crossEdge(ssLeft.xCompXInvEdge, ssLeft.gnd, visitedNodes)
    }

    private fun crossEdge(edge: CircuitEdgeDC, nodeFrom: CircuitNodeDC, visitedNodes: MutableSet<CircuitNodeDC>) {
        if (edge.value.isInf()) return // ветви с "бесконечным" номиналом считаются отсутствующими

        val nodeTo = if (nodeFrom != edge.getSourceNode()) edge.getSourceNode() else edge.getTargetNode()
        if (nodeTo in visitedNodes) return

        val sign = if (nodeTo == edge.getTargetNode()) -1 else +1 // потенциал уменьшается по направлению тока
        val i = J?.get(edge.index, 0)
            ?: throw RuntimeException("Перед расчетом узловых потенциалов необходимо выполнить расчет токов.")
        val r = edge.value

        nodeTo.p = (nodeFrom.p ?: 0.0) + sign * (i * r - edge.emf)
        visitedNodes.add(nodeTo)

        for (e in graph.edgesOf(nodeTo)) {
            crossEdge(e as CircuitEdgeDC, nodeTo, visitedNodes)
        }
    }

    private fun Double.isInf() = this >= INF

    fun getReport(): String {
        val edges = graph.edgeSet()
        val trackQty = (blocks.first() as BlockSSDC).rightFeederEdges.size
        val report = StringBuilder()

        report.append(toString())
        report.append("\n")

        for (tn in 1..trackQty) {
            val cnAmperages = edges
                .filter { it.name.contains("CN_$tn") }
                .sortedBy { it.name }
                .map { J!![it.index, 0] }
                .map { it.round(0) }
            report.append("Токи на пути №$tn: ")
            report.append(cnAmperages.toString() + "\n")
        }

        val feederAmperages = edges
            .filter { it.name.contains("Zf") }
            .sortedBy { it.name }
            .map { J!![it.index, 0] }
            .map { it.round(0) }
        report.append("Токи в фидерах ТП: ")
        report.append(feederAmperages.toString() + "\n")

        val spFeederAmperages = edges
            .filter { it.name.contains("Zb") }
            .sortedBy { it.name }
            .map { J!![it.index, 0] }
            .map { it.round(0) }
        report.append("Токи в фидерах ПС: ")
        report.append(spFeederAmperages.toString() + "\n")

        return report.toString()
    }

    fun saveAmperagesInGraphEdges() {
        for (edge in graph.edgeSet()) {
            edge.amp = J!!.get(edge.index, 0)
        }
    }

    fun getFeederAmperages(): Pair<List<Double>, List<Double>> {
        val leftSSFeederAmps = (blocks.first() as BlockSSDC).rightFeederEdges.map { J!![it.index, 0] }
        val rightSSFeederAmps = (blocks.first() as BlockSSDC).leftFeederEdges.map { J!![it.index, 0] }
        return Pair(leftSSFeederAmps, rightSSFeederAmps)
    }

    override fun toString(): String {
        val result = StringBuilder()
        for (block in blocks) {
            result.append("$block -- ")
        }
        result.setLength(result.length - 4)
        return result.toString()
    }

}