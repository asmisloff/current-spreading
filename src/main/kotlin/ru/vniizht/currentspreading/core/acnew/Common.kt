package ru.vniizht.currentspreading.core.acnew

import org.apache.commons.math3.complex.Complex
import org.ejml.data.Complex_F32
import org.ejml.data.Complex_F64
import ru.vniizht.currentspreading.dto.ACDTrackFiderDto
import ru.vniizht.currentspreading.dto.ACFiderDto
import ru.vniizht.currentspreading.dto.ACTrackFiderDto
import ru.vniizht.asuterkortes.dto.TransformerDto
import ru.vniizht.currentspreading.util.check
import ru.vniizht.currentspreading.util.checkNotNull
import ru.vniizht.currentspreading.util.eq
import ru.vniizht.currentspreading.util.toFixed
import kotlin.math.*

val ZERO = Complex(1e-9, 1e-9) // 0 + 0i плохо сказывается на устойчивости решений
val INF = Complex(1e9, 0.0)

/** Критерий остановки итерационного расчета по углу нагрузки */
val PAYLOAD_VOLTAGE_TOLERANCE = 1.0.toRad()

/** Критерий остановки итерационного расчета по токам в смежных плечах подстанции */
const val FEEDER_AMP_TOLERANCE = 1.0

fun <T> List<T>.sumOfOrNull(selector: (T) -> Complex?): Complex? {
    if (this.isEmpty()) return null
    var re = 0.0
    var im = 0.0
    for (c in this) {
        val z = selector(c) ?: return null
        re += z.real
        im += z.imaginary
    }
    return Complex(re, im)
}

operator fun Int.times(other: Complex): Complex = other.multiply(this)

operator fun Complex.minus(other: Complex): Complex = this.subtract(other)

operator fun Complex_F64.times(other: Complex): Complex {
    val re = this.real * other.real - this.imaginary * other.imaginary
    val im = this.real * other.imaginary + this.imaginary * other.real
    return Complex(re, im)
}

operator fun Complex_F64.minus(other: Complex) =
    Complex(this.real - other.real, this.imaginary - other.imaginary)

fun Complex_F64.assign(re: Double, im: Double) {
    real = re; imaginary = im
}

fun Complex_F64.assign(other: Complex_F32) {
    real = other.real.toDouble(); imaginary = other.imaginary.toDouble()
}

fun Complex_F64.assign(other: Complex) {
    real = other.real; imaginary = other.imaginary
}

operator fun Complex_F64.plusAssign(other: Complex_F64) {
    this.real += other.real
    this.imaginary += other.imaginary
}

operator fun Complex_F64.plusAssign(other: Complex_F32) {
    this.real += other.real
    this.imaginary += other.imaginary
}

operator fun Complex_F64.plusAssign(other: Complex) {
    this.real += other.real
    this.imaginary += other.imaginary
}

fun Complex_F64.plusAssign(real: Double, imaginary: Double) {
    this.real += real
    this.imaginary += imaginary
}

operator fun Complex_F64.timesAssign(other: Complex_F64) {
    val re = this.real * other.real - this.imaginary * other.imaginary
    val im = this.real * other.imaginary + this.imaginary * other.real
    this.real = re
    this.imaginary = im
}

fun Complex_F64.timesAssign(real: Double, imaginary: Double) {
    val re = this.real * real - this.imaginary * imaginary
    val im = this.real * imaginary + this.imaginary * real
    this.real = re
    this.imaginary = im
}

operator fun Complex_F64.timesAssign(scale: Double) {
    this.real *= scale
    this.imaginary *= scale
}

operator fun Complex_F64.timesAssign(other: Complex) {
    timesAssign(other.real, other.imaginary)
}

fun Complex_F32.assign(re: Float, im: Float) {
    real = re; imaginary = im
}

fun Complex_F32.assign(other: Complex_F32) {
    real = other.real; imaginary = other.imaginary
}

operator fun Complex_F32.plusAssign(other: Complex_F32) {
    this.real += other.real
    this.imaginary += other.imaginary
}

fun Complex_F32.plusAssign(re: Float, im: Float) {
    this.real += re
    this.imaginary += im
}

fun Complex_F32.minusAssign(re: Float, im: Float) {
    this.real -= re
    this.imaginary -= im
}

operator fun Complex_F32.timesAssign(other: Complex_F32) {
    val re = this.real * other.real - this.imaginary * other.imaginary
    val im = this.real * other.imaginary + this.imaginary * other.real
    this.real = re
    this.imaginary = im
}

fun Complex_F32.timesAssign(real: Float, imaginary: Float) {
    val re = this.real * real - this.imaginary * imaginary
    val im = this.real * imaginary + this.imaginary * real
    this.real = re
    this.imaginary = im
}

operator fun Complex_F32.timesAssign(scale: Float) {
    this.real *= scale
    this.imaginary *= scale
}

operator fun Complex_F32.component1(): Float = real
operator fun Complex_F32.component2(): Float = imaginary

fun Complex.isInf() = abs(real) + abs(imaginary) > 1e7

fun Complex.isNotInf() = !this.isInf()

infix fun Complex.eq(other: Complex) = this.real eq other.real && this.imaginary eq other.imaginary

operator fun Complex.plus(addend: Complex): Complex = this.add(addend)

operator fun Complex.times(factor: Double): Complex = this.multiply(factor)

operator fun Complex.times(other: Complex): Complex = this.multiply(other)

fun Complex.square(): Complex = this * this

fun Complex.quadrant(): Int {
    val arg = this.argument
    return when {
        arg >= 0 && arg < PI / 2 -> 1
        arg >= PI / 2 && arg < PI -> 2
        arg >= -PI && arg < -PI / 2 -> 3
        else -> 4
    }
}

fun Double.toRad() = this / 180.0 * PI

fun Double.toDeg() = this * 180 / PI

fun complexMagn(re: Double, im: Double) = sqrt(re.square() + im.square())
fun complexMagn(re: Float, im: Float) = sqrt(re.square() + im.square())
fun complexMagn2(re: Double, im: Double) = re.square() + im.square()
fun complexMagn2(re: Float, im: Float) = re.square() + im.square()

fun Double.square() = this * this
fun Float.square() = this * this

/**
 * Модельные параметры фидеров устройства для схемы переменного тока
 * @param leftCnCoords координаты точек присоединения к проводам конт. сети слева от устройства
 * @param leftCnResistances сопротивления фидеров КС слева от устройства
 * @param rightCnCoords координаты точек присоединения к проводам конт. сети справа от устройства
 * @param rightCnResistances сопротивления фидеров КС справа от устройства
 * @param leftSupplyCoords координаты точек присоединения к проводам питающей сети слева от устройства
 * @param leftSupplyResistances сопротивления фидеров питающей линии слева от устройства
 * @param rightSupplyCoords координаты точек присоединения к проводам питающей сети справа от устройства
 * @param rightSupplyResistances сопротивления фидеров питающей линии справа от устройства
 */
data class AcFeederParameters(
    val leftCnCoords: List<Double>,
    val leftCnResistances: List<Complex>,

    val rightCnCoords: List<Double>,
    val rightCnResistances: List<Complex>,

    val leftSupplyCoords: List<Double>,
    val leftSupplyResistances: List<Complex>,

    val rightSupplyCoords: List<Double>,
    val rightSupplyResistances: List<Complex>,
) {

    companion object {
        fun fromAcTrackDtos(trackFeederDtos: List<ACTrackFiderDto>, schemaObjectName: String): AcFeederParameters {
            val leftCnResistances = mutableListOf<Complex>()
            val rightCnResistances = mutableListOf<Complex>()
            val leftCnCoordinates = mutableListOf<Double>()
            val rightCnCoordinates = mutableListOf<Double>()

            val leftSupplyResistances = mutableListOf<Complex>()
            val rightSupplyResistances = mutableListOf<Complex>()
            val leftSupplyCoordinates = mutableListOf<Double>()
            val rightSupplyCoordinates = mutableListOf<Double>()

            fun ACFiderDto.getFeederResistance(trackIndex: Int): Complex {
                if (this.length eq 0.0) {
                    return ZERO
                }

                val params = this.type
                    ?.parameters
                    .checkNotNull { "Некорректно задано сопротивление левого фидера объекта \"$schemaObjectName\", путь №${trackIndex + 1}" }
                    .check("Фидер должен состоять из одного кабеля (объект \"$schemaObjectName\", путь №${trackIndex + 1}, левый)") {
                        it.size == 1
                    }
                    .get(index = 0)

                return Complex(
                    params.activeResistance * this.length,
                    params.inductiveResistance * this.length
                )
            }

            for ((trackIndex, dto) in trackFeederDtos.withIndex()) {
                leftCnCoordinates.add(dto.leftFider.coordinate)
                rightCnCoordinates.add(dto.rightFider.coordinate)
                leftCnResistances.add(dto.leftFider.getFeederResistance(trackIndex))
                rightCnResistances.add(dto.rightFider.getFeederResistance(trackIndex))

                if (dto is ACDTrackFiderDto) {
                    leftSupplyCoordinates.add(dto.leftSupplyFider.coordinate)
                    rightSupplyCoordinates.add(dto.rightSupplyFider.coordinate)
                    leftSupplyResistances.add(dto.leftSupplyFider.getFeederResistance(trackIndex))
                    rightSupplyResistances.add(dto.rightSupplyFider.getFeederResistance(trackIndex))
                }
            }

            return AcFeederParameters(
                leftCnCoordinates, leftCnResistances, rightCnCoordinates, rightCnResistances,
                leftSupplyCoordinates, leftSupplyResistances, rightSupplyCoordinates, rightSupplyResistances
            )
        }

        fun getFeederResistance(f: ACFiderDto, hostBlockLabel: String): Complex {
            if (f.length eq 0.0) {
                return ZERO
            }

            val params = f.type
                ?.checkNotNull { "Не заданы электрические параметры фидера ($hostBlockLabel)" }
                ?.parameters
                .checkNotNull { "Некорректно задано сопротивление фидера ${f.type!!.name} ($hostBlockLabel)" }
                .check("Фидер должен состоять из одного кабеля ($hostBlockLabel, ${f.type!!.name})") {
                    it.size == 1
                }
                .get(index = 0)

            return Complex(
                params.activeResistance * f.length,
                params.inductiveResistance * f.length
            )
        }
    }

}

/**
 * Сопротивление внешней сети
 * @param skz Sкз, МВ*А
 * @param ks коэффициент Ks (в обозначениях ГОСТ-а)
 * @param uNom номинальное напряжение сети, В
 */
fun zExternalNetwork(skz: Double, ks: Double, uNom: Double = 27500.0 /*[В]*/): Complex {
    require(!(skz eq 0.0)) { "Деление на ноль при расчете сопротивления внешней сети." }
    val magn = ks * uNom.pow(2) / (1e6 * skz)
    val re = cos(atan(1.6)) * magn
    val im = sin(atan(1.6)) * magn
    return Complex(re, im)
}

/**
 * Сопротивление обмотки трансформатора
 * @param transformer паспортные данные трансформатора
 * @param nt количество трансформаторов
 * @param ks коэффициент Ks (в обозначениях ГОСТ-а)
 * @param uNom номинальное напряжение сети, В
 */
fun zTrans(transformer: TransformerDto, nt: Int, ks: Double, uNom: Double = 27500.0 /*[В]*/): Complex {
    val uk = transformer.voltageShortCircuit // [%]
    val st = transformer.power.toDouble() * 1e3 // [Вт]
    val pk = transformer.power_loss_short_circuit * 1e3 // [Вт]

    val magn = ks * (0.01 * uk * uNom.pow(2)) / (st * nt)
    val re = ks * ((pk * uNom.pow(2)) / st.pow(2)) / nt
    val im = sqrt(magn.pow(2) - re.pow(2))
    return Complex(re, im)
}

/** Экспоненциальное представление комплексного числа
 * @param magnPrecision кол. десятичных знаков в представлении амплитуды
 * @param anglePrecision десятичных знаков в представлении угла (в градусах)
 * */
fun Complex.expRepr(magnPrecision: Int = 0, anglePrecision: Int = 1): String =
    "(${this.abs().toFixed(magnPrecision)}, ${this.argument.toDeg().toFixed(anglePrecision)}deg)"

/** Создать комплексное число с заданной амплитудой и фазой в градусах */
fun complex(magn: Double, angleInDegrees: Double) =
    Complex(magn * cos(angleInDegrees.toRad()), magn * sin(angleInDegrees.toRad()))

/** Повернуть комплексное число на угол
 * @param angle угол поворота в радианах
 * @return z = a * exp(b * j) => z.rotate(c) = a * exp((b + c) * j)
 * */
fun Complex.rotate(angle: Double): Complex {
    val ampl = this.abs()
    val arg = this.argument
    val re = ampl * cos(arg + angle)
    val im = ampl * sin(arg + angle)
    return Complex(re, im)
}
