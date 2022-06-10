package ru.vniizht.currentspreading.dao

import ru.vniizht.asuterkortes.dto.UpkCompensationDeviceDto
import ru.vniizht.currentspreading.dao.enums.CompensationDeviceType
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "asu_ter_m_compensation_device_upk")
data class UpkCompensationDevice(

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

        @field:Column(name = "nominal_amperage", nullable = false)
        val nominalAmperage: Double,

        @field:Column(name = "resistance", nullable = false)
        val resistance: Double

): ReferenceEntity<UpkCompensationDeviceDto>() {

        override fun toFullDto() = UpkCompensationDeviceDto(
                id = id!!,
                changeTime = changeTime,
                name = name,
                nominalAmperage = nominalAmperage,
                resistance = resistance,
                regulationType = regulationType.russianName,
                regulationDiapason = regulationDiapason,
                nominalPower = nominalPower
        )
}
