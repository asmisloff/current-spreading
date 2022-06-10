package ru.vniizht.currentspreading.core.acnew

import org.apache.commons.math3.complex.Complex
import org.ejml.data.Complex_F64
import org.ejml.data.ZMatrixRMaj
import org.ejml.dense.row.CommonOps_ZDRM
import org.ejml.dense.row.factory.LinearSolverFactory_ZDRM
import org.ejml.interfaces.linsol.LinearSolverDense
import ru.vniizht.currentspreading.core.circuit.Circuit
import ru.vniizht.asuterkortes.counter.circuit.CircuitEdgeAC
import ru.vniizht.asuterkortes.counter.circuit.CircuitNodeAC
import ru.vniizht.asuterkortes.counter.circuit.ICircuitEdge
import ru.vniizht.currentspreading.util.round
import kotlin.math.abs

@Suppress("PropertyName", "PrivatePropertyName")
class TransitionalCircuitAC(private val network: List<NetworkResistanceRangeAC>) :
    Circuit<Complex, CircuitEdgeAC, CircuitNodeAC>(CircuitEdgeAC::class.java) {

    var Z: ZMatrixRMaj? = null
    var K: ZMatrixRMaj? = null
    var I: ZMatrixRMaj? = null
    private var E: ZMatrixRMaj? = null

    private var KZ: ZMatrixRMaj? = null
    private var KE: ZMatrixRMaj? = null
    private var KZI: ZMatrixRMaj? = null
    var A: ZMatrixRMaj? = null
    var B: ZMatrixRMaj? = null
    var Icc: ZMatrixRMaj? = null
    private var J: ZMatrixRMaj? = null
    var dU: ZMatrixRMaj? = null

    private lateinit var solver: LinearSolverDense<ZMatrixRMaj>

    var zeroNode: CircuitNodeAC? = null

    private var ss1IssEdge: CircuitEdgeAC? = null
    private var ss1HalfIssEdge: CircuitEdgeAC? = null
    private var ss2IssEdge: CircuitEdgeAC? = null
    private var ss2HalfIssEdge: CircuitEdgeAC? = null
    private var ssEdgesList: List<CircuitEdgeAC>? = null

    private val wiringEdges = mutableMapOf<Int, MutableList<ICircuitEdge>>()

    fun ssLeft() = checkNotNull(blocks.first() as? BlockSSAC) {
        "Схема МПЗ в рассогласованном состоянии: первым блоком должна быть ТП, фактически обнаружен ${blocks.first()::class}"
    }

    fun ssRight() = checkNotNull(blocks.last() as? BlockSSAC) {
        "Схема МПЗ в рассогласованном состоянии: последним блоком должна быть ТП, фактически обнаружен ${blocks.last()::class}"
    }

    fun wire() {
        val nodes = mutableMapOf<Int, MutableList<CircuitNodeAC>>()
        for (node in graph.vertexSet().sortedBy { it.coordinate }) { // узлы КС и ПП
            if (node.trackNumber < 1) continue
            nodes
                .getOrPut(node.trackNumber) { mutableListOf() }
                .add(node)
        }
        for (tn in nodes.keys) { // Соединить узлы ветвями
            for ((n1, n2) in nodes[tn]!!.zipWithNext()) {
                if (n1.breaking || n2.breaking) continue
                wiringEdges
                    .getOrPut(tn) { mutableListOf() }
                    .also { edgeList ->
                        val label = if (n1.trackNumber < 1000) "CN" else "SPL"
                        val edge = CircuitEdgeAC(label, ZERO)
                        graph.addEdge(n1, n2, edge)
                        edgeList.add(edge)
                    }
            }
        }
        renumberEdges(); renumberNodes()
    }

    fun build() {
        val cycles = fundamentalCycles!!
        check(cycles.isNotEmpty()) { "Схема не замкнута." }
        val edges = graph.edgeSet()
        val ss1 = blocks.first() as? BlockSSAC
            ?: throw RuntimeException("Ошибка построения схемы МПЗ: на первом месте должна быть подстанция.")
        val ss2 = blocks.last() as? BlockSSAC
            ?: throw RuntimeException("Ошибка построения схемы МПЗ: на последнем месте должна быть подстанция.")

        if (edges.isEmpty()) throw RuntimeException("Схема не содержит элементов.")

        Z = ZMatrixRMaj(edges.size, edges.size)
        I = ZMatrixRMaj(edges.size, 1)
        E = ZMatrixRMaj(edges.size, 1)

        for (block in blocks) { // ребра блоков
            for (edge in block.graph.edgeSet()) {
                Z!!.set(edge.index, edge.index, edge.value.real, edge.value.imaginary)
                I!!.set(edge.index, 0, edge.csa.real, edge.csa.imaginary)
                E!!.set(edge.index, 0, edge.emf.real, edge.emf.imaginary)
            }
        }

        fillMutualResistanceCells() // ребра КС

        K = ZMatrixRMaj(cycles.size, edges.size)
        cycles.forEachIndexed { row, c ->
            c.forEach { (col, value) ->
                K!!.set(row, col, value, 1e-9)
            }
        }
        KZ = ZMatrixRMaj(K)

        A = ZMatrixRMaj(cycles.size, cycles.size)
        B = ZMatrixRMaj(cycles.size, 1)

        KE = ZMatrixRMaj(cycles.size, 1)
        KZI = ZMatrixRMaj(cycles.size, 1)

        Icc = ZMatrixRMaj(cycles.size, 1)
        J = ZMatrixRMaj(edges.size, 1)
        dU = ZMatrixRMaj(edges.size, 1)

        ss1IssEdge = ss1.issEdge
        ss1HalfIssEdge = ss1.halfIssEdge
        ss2IssEdge = ss2.issEdge
        ss2HalfIssEdge = ss2.halfIssEdge
        ssEdgesList = listOf(ss1HalfIssEdge!!, ss2HalfIssEdge!!, ss1IssEdge!!, ss2IssEdge!!) +
                ssLeft().feederEdges + ssRight().feederEdges

        CommonOps_ZDRM.mult(K, Z, KZ)
        CommonOps_ZDRM.multTransB(KZ, K, A)
        CommonOps_ZDRM.mult(K, E, KE)

        solver = LinearSolverFactory_ZDRM.lu(A!!.numRows)
        if (!solver.setA(A)) {
            throw IllegalStateException("Невозможно выполнить расчет мгн. схемы: матрица системы вырождена")
        }
    }

    fun solve() {
        ssLeft().prevFeedersAmp = ssLeft().totalFeederAmp()
        ssRight().prevFeedersAmp = ssRight().totalFeederAmp()

        CommonOps_ZDRM.mult(KZ, I, KZI)
        CommonOps_ZDRM.subtract(KE, KZI, B)

        solver.solve(B, Icc)
        CommonOps_ZDRM.multTransA(K, Icc, J)
        CommonOps_ZDRM.mult(Z, J, dU)

        saveAmperagesInGraphEdges()
        computeNodePotentials()
    }

    @Suppress("UNCHECKED_CAST")
    fun adjustPayloadsPhases(tolerance: Double, blocks: List<BlockAC> = this.blocks as List<BlockAC>): Boolean {
        var result = false
        for (block in blocks) {
            when (block) {
                is BlockPayloadAC -> {
                    val voltArg = block.cnNode.p?.argument ?: return true
                    val diff = voltArg - (block.pPrev?.argument ?: 0.0)
                    if (abs(diff) > tolerance) {
                        block.iplEdge.csa = block.amperage.rotate(voltArg)
                        I!!.set(block.iplEdge.index, 0, block.iplEdge.csa.real, block.iplEdge.csa.imaginary)
                        block.pPrev = block.cnNode.p
                        result = true
                    }
                }
                is BlockBranchAc -> adjustPayloadsPhases(tolerance, block.blocks)
                else -> continue
            }

        }
        return result
    }

    fun updateEmfVector() {
        I!!.set(ssLeft().issEdge.index, 0, ssLeft().issEdge.csa.real, ssLeft().issEdge.csa.imaginary)
        I!!.set(ssRight().issEdge.index, 0, ssRight().issEdge.csa.real, ssRight().issEdge.csa.imaginary)
        I!!.set(ssLeft().halfIssEdge.index, 0, ssLeft().halfIssEdge.csa.real, ssLeft().halfIssEdge.csa.imaginary)
        I!!.set(ssRight().halfIssEdge.index, 0, ssRight().halfIssEdge.csa.real, ssRight().halfIssEdge.csa.imaginary)
    }

    fun edgeAmperagesStringRepr() = graph.edgeSet()
        .sortedBy { it.name }
        .map { "${it.name}: ${it.amp?.abs()?.round(1)}e(${it.amp?.argument?.toDeg()?.round(1)})" }

    fun nodePotentialsStringRepr() = graph.vertexSet()
        .sortedBy { it.label }
        .map { "${it.label}: ${it.p?.abs()?.round(1)}e(${it.p?.argument?.toDeg()?.round(1)})" }

    private fun fillMutualResistanceCells() {
        val inductivelyCoupledSpans = wiringEdges.toInductivelyCoupledSpans()
        for ((trackNumbers, spans) in inductivelyCoupledSpans) {
            for ((i1, i2, xLeft, xRight) in spans) {
                val res = network.computeResistance(xLeft, xRight, trackNumbers.first, trackNumbers.second)
                Z!!.set(i1, i2, res.real, res.imaginary)
                Z!!.set(i2, i1, res.real, res.imaginary)
            }
        }
    }

    private fun saveAmperagesInGraphEdges() {
        val z = Complex_F64()
        for (edge in graph.edgeSet()) {
            J!!.get(edge.index, 0, z)
            edge.amp = Complex(z.real, z.imaginary)
        }
    }

    private fun computeNodePotentials() {
        val zeroNode = this.zeroNode!!
        zeroNode.p = ZERO
        val visitedNodes = mutableSetOf<CircuitNodeAC>()
        visitedNodes.add(zeroNode)
        crossEdge(ssRight().xCompXInvEdge, zeroNode, visitedNodes)
        crossEdge(ssLeft().xCompXInvEdge, zeroNode, visitedNodes)
    }

    private fun crossEdge(edge: CircuitEdgeAC, nodeFrom: CircuitNodeAC, visitedNodes: MutableSet<CircuitNodeAC>) {
        if (edge.value.isInf()) return // ветви с "бесконечным" номиналом считаются отсутствующими

        val nodeTo = if (nodeFrom != edge.getSourceNode()) edge.getSourceNode() else edge.getTargetNode()
        if (nodeTo in visitedNodes) return

        val sign = if (nodeTo == edge.getTargetNode()) -1 else +1

        val du = Complex_F64()
        dU?.get(edge.index, 0, du)

        nodeTo.p = (nodeFrom.p!!) + sign * (du - edge.emf)
        visitedNodes.add(nodeTo)

        for (e in graph.edgesOf(nodeTo)) {
            crossEdge(e as CircuitEdgeAC, nodeTo, visitedNodes)
        }
    }

    fun saveCnPowerLoss(dest: Complex_F64) {
        val buffer = Complex_F64()
        for (edges in wiringEdges.values) {
            for (edge in edges) {
                edge as CircuitEdgeAC
                dU!!.get(edge.index, 0, buffer)
                buffer.timesAssign(edge.amp?.real ?: 0.0, -(edge.amp?.imaginary ?: 0.0))
                dest.plusAssign(buffer)
            }
        }
    }

}
