package ru.vniizht.currentspreading.dao

import org.apache.commons.math3.complex.Complex
import org.hibernate.Hibernate
import ru.vniizht.currentspreading.core.acnew.ZERO
import ru.vniizht.currentspreading.core.acnew.eq
import ru.vniizht.currentspreading.core.acnew.isInf
import ru.vniizht.currentspreading.core.acnew.isNotInf
import ru.vniizht.asuterkortes.dto.TransformerDto
import java.lang.IllegalStateException
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "asu_ter_m_transformer")
class Transformer(

        @field:Id
        @field:Column(name = "id")
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        override val id: Long? = null,

        @field:Column(name = "active", nullable = false)
        override var active: Boolean = true,

        @field:Column(name = "change_time", nullable = false)
        override var changeTime: LocalDateTime = LocalDateTime.now(),

        @field:Column(name = "name", nullable = false)
        override var name: String,

        @field:Column(name = "type")
        val type: String?,

        @field:Column(name = "power", nullable = false)
        val power: Int,

        @field:Column(name = "voltage", nullable = false)
        val voltage: Double,

        @field:Column(name = "schema")
        val schema: String?,

        @field:Column(name = "voltage_regulation")
        val voltageRegulation: Double?,

        @field:Column(name = "voltage_short_circuit", nullable = false)
        val voltageShortCircuit: Double,

        @field:Column(name = "power_loss_short_circuit", nullable = false)
        val power_loss_short_circuit: Double,

        @field:Column(name = "power_loss_no_load", nullable = false)
        val power_loss_no_load: Double,

        @field:Column(name = "amperage_no_load", nullable = false)
        val amperage_no_load: Double

): ReferenceEntity<TransformerDto>() {

        override fun toFullDto() = TransformerDto(
                id = id!!,
                changeTime = changeTime,
                name = name,
                voltageShortCircuit = voltageShortCircuit,
                voltageRegulation = voltageRegulation,
                schema = schema,
                power_loss_short_circuit = power_loss_short_circuit,
                power_loss_no_load = power_loss_no_load,
                amperage_no_load = amperage_no_load,
                power = power,
                type = type,
                voltage = voltage
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Transformer

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 1517456826
}

/** Тип трансформатора */
enum class TransformerType(val strRepr: String) {

    DC("Для сетей постоянного тока"),
    TWELVE_PHASE("12-фазный преобразователь"),
    THREE_PHASE("Трехфазный"),
    AUTO("Автотрансформатор"),
    BRANCH("С расщепленной обмоткой");

    companion object {

        /**
         * Вывести тип трансформатора из параметров схемы замещения подстанции
         * @param zAt сопротивление Zат
         * @param zTk сопротивление Zтк
         * */
        fun infer(zAt: Complex, zTk: Complex) = when {
            zAt.isInf() && zTk eq ZERO -> THREE_PHASE
            zAt.isNotInf() && zAt eq zTk -> BRANCH
            zAt.isNotInf() && zTk eq ZERO -> AUTO
            else -> throw IllegalArgumentException("Невозможно идентифицировать тип трансформатора")
        }

        /**
         * Вывести тип трансформатора из его наименования
         * */
        fun infer(name: String) = when {
            name.endsWith("**") -> BRANCH
            name.endsWith('*') -> TWELVE_PHASE
            name.startsWith("АО") -> AUTO
            name.substringBefore('-').last() == 'П' -> DC
            name[0] == 'Т' || name[1] == 'Т' -> THREE_PHASE
            else -> throw IllegalStateException("Не удалось идентифицировать тип трансформатора: $name")
        }

    }

}