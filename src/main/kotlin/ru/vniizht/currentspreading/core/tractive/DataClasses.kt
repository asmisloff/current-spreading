package ru.vniizht.asuterkortes.counter.tractive

import ru.vniizht.currentspreading.util.round

data class SpeedLimit(
    val coordinate: Double,
    val limit: Int
)

data class ProfileElement(
    val startCoordinate: Double,
    val length: Double,
    val i: Double, // Уклон, [per mille]
    val ikr: Double = 0.0, // эквивалентный уклон (от кривой), [per mille]
    val wir: Double = (g * (i + ikr)).round(5) // Дополнительное удельное сопротивление движению от уклона и от кривой, Н/т
)

data class PositionCharacteristic(
    val speed: Double,
    val motorAmperage: Double,
    val force: Double,
    val activeAmperage: Double,
    val fullAmperage: Double? = null
)

data class Position(
    val name: String,
    val characteristics: List<PositionCharacteristic>
)

data class RetardationElement(
    val speed: Double,
    val coordinate: Double
) {
    override fun toString(): String {
        return "$speed\t$coordinate"
    }
}

data class AverageElement(
    val s: Double,
    val c: Double,
    val actA: Double,
    val fullA: Double? = null,
    val rgm: Int,
    val t: Double,
    val ma: Double
)

data class TractiveResistanceToMotion(
    val motoringResistance: DoubleArray,
    val idleResistance: DoubleArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TractiveResistanceToMotion

        if (!motoringResistance.contentEquals(other.motoringResistance)) return false
        if (!idleResistance.contentEquals(other.idleResistance)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = motoringResistance.contentHashCode()
        result = 31 * result + idleResistance.contentHashCode()
        return result
    }
}

data class TractiveStop(
    val coordinate: Double,
    val time: Int
)

data class TractionRate(
    val coordinate: Double,
    val rate: Double
)

data class BetweenStationsData(
    val time: Double,
    val motoringTime: Double,
    val activePower: Double
)
