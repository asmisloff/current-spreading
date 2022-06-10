package ru.vniizht.currentspreading.dao

import org.hibernate.Hibernate
import ru.vniizht.asuterkortes.dto.UkCompensationDeviceDto
import ru.vniizht.currentspreading.dao.enums.CompensationDeviceType
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "asu_ter_m_compensation_device_uk")
class UkCompensationDevice(

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

        @field:Column(name = "regulation_type", nullable = false)
        @field:Enumerated(EnumType.STRING)
        val regulationType: CompensationDeviceType,

        @field:Column(name = "regulation_diapason")
        val regulationDiapason: Double? = null,

        @field:Column(name = "nominal_power", nullable = false)
        val nominalPower: Double,

        @field:Column(name = "useful_power", nullable = false)
        val usefulPower: Double,

        @field:Column(name = "resistance", nullable = false)
        val resistance: Double

): ReferenceEntity<UkCompensationDeviceDto>() {

        override fun toFullDto() = UkCompensationDeviceDto(
                id = id!!,
                changeTime = changeTime,
                name = name,
                usefulPower = usefulPower,
                resistance = resistance,
                regulationType = regulationType.russianName,
                regulationDiapason = regulationDiapason,
                nominalPower = nominalPower
        )

        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
                other as UkCompensationDevice

                return id != null && id == other.id
        }

        override fun hashCode(): Int = 818172998
}
