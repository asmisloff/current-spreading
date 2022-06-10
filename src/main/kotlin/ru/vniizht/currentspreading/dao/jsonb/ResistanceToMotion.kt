package ru.vniizht.asuterkortes.dao.model.jsonb


data class LocomotiveResistanceToMotion(
        var motoringResistanceCoefficients: ResistanceToMotion,
        var idleResistanceCoefficients: ResistanceToMotion
)

data class ResistanceToMotion(
    val componentRail: Array<Double?>,
    val continuousRail: Array<Double?>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResistanceToMotion

        if (!continuousRail.contentEquals(other.continuousRail)) return false
        if (!componentRail.contentEquals(other.componentRail)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = continuousRail.contentHashCode()
        result = 31 * result + componentRail.contentHashCode()
        return result
    }
}
