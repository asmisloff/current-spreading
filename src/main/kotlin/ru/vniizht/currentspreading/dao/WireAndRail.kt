package ru.vniizht.currentspreading.dao

import org.hibernate.Hibernate
import ru.vniizht.currentspreading.dto.WireAndRailDto
import ru.vniizht.currentspreading.util.toDateTime
import java.time.LocalDateTime
import javax.persistence.*

const val DEFAULT_THERMAL_CAPACITY = 772.0f

@Entity
@Table(name = "asu_ter_m_wire_and_rail")
class WireAndRail(

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

        @field:Column(name = "direct_resistance", nullable = false)
        val directResistance: Double,

        @field:Column(name = "alternate_resistance", nullable = false)
        val alternateResistance: Double,

        @field:Column(name = "radius", nullable = false)
        val radius: Double,

        @field:Column(name = "limit_amperage")
        val limitAmperage: Double? = null,

        @field:Column(name = "limit_temperature")
        val limitTemperature: Int? = null,

        @field:Column(name = "thermal_constant")
        val thermalConstant: Double? = null

): ReferenceEntity<WireAndRailDto>() {

        override fun toFullDto() = WireAndRailDto(
                id = id!!,
                changeTime = changeTime.toDateTime(),
                name = name,
                thermalConstant = thermalConstant,
                radius = radius,
                limitAmperage = limitAmperage,
                directResistance = directResistance,
                alternateResistance = alternateResistance,
                limitTemperature = limitTemperature
        )

        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
                other as WireAndRail

                return id != null && id == other.id
        }

        override fun hashCode(): Int = 1154113917
}
