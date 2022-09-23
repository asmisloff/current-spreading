package ru.vniizht.currentspreading.core

import org.apache.commons.math3.complex.Complex
import ru.vniizht.currentspreading.core.acnew.minus
import kotlin.math.ln
import kotlin.math.sqrt

class CurrentSpreadingComputer(rp: Double, zr: Complex, mu: Complex? = null) {

    private val m = 1
    private val gamma = (zr * m / rp).pow(0.5)
    private val zw = (zr * rp / m).pow(0.5)
    private val sigma = 0.01
    private val dKpr = 6.5
    private val dNtr = 8.3
    private val mu = mu ?: (Complex(0.05, 0.0628 * (4.54 - ln(sqrt(dKpr * dNtr * sigma)))) / zr)

    private fun fiLeft(x: Double, i: Complex, l: Double, L: Double): Complex {
        val t1 = 0.5 * zw * (1 - mu) * i
        val t2 = (gamma.negate() * x).exp()
        val t3 = (1 - l / L) * (gamma.negate() * (l - x)).exp()
        val t4 = l / L * (gamma.negate() * (L - l + x)).exp()
        return t1 * (t2 - t3 - t4)
    }

    private fun fiRight(x: Double, i: Complex, l: Double, L: Double): Complex {
        val t1 = 0.5 * zw * (1 - mu) * i
        val t2 = (gamma.negate() * x).exp()
        val t3 = (1 - l / L) * (gamma.negate() * (l + x)).exp()
        val t4 = l / L * (gamma.negate() * (L - l - x)).exp()
        return t1 * (t2 - t3 - t4)
    }

    fun fi(x: Double, x0: Double, i: Complex, xLeft: Double, xRight: Double): Complex {
        val l = x0 - xLeft
        val L = xRight - xLeft
        return when {
            x >= x0 -> {
                fiRight(x - x0, i, l, L)
            }
            x in 0.0..x0 -> {
                fiLeft(x0 - x, i, l, L)
            }
            else -> Complex.ZERO
        }
    }

}

operator fun Complex.times(other: Complex): Complex = this.multiply(other)
operator fun Complex.times(other: Double): Complex = this.multiply(other)
operator fun Double.times(other: Complex): Complex = other.multiply(this)
operator fun Complex.times(other: Int): Complex = this.multiply(other)

operator fun Complex.div(other: Complex): Complex = this.divide(other)
operator fun Complex.div(other: Double): Complex = this.divide(other)
operator fun Complex.div(other: Int): Complex = this.divide(other.toDouble())

operator fun Int.minus(other: Complex) = Complex(other.real - this, other.imaginary)