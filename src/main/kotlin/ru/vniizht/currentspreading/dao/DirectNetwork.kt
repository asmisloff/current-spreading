package ru.vniizht.asuterkortes.dao.model

import ru.vniizht.currentspreading.dao.WireAndRail
import javax.persistence.*

@Entity
@Table(name = "asu_ter_m_direct_network")
class DirectNetwork(

        @field:Id
        @field:Column(name = "id")
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long? = null,

        @field:Column(name = "name", nullable = false)
        var name: String,

        @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "fider_id", referencedColumnName = "id")
        val fider: WireAndRail?,

        @field:Column(name = "fider_count")
        var fiderCount: Int?,

        @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "contact_wire_id", referencedColumnName = "id")
        val contactWire: WireAndRail?,

        @field:Column(name = "contact_wire_count")
        var contactWireCount: Int?,

        @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "power_wire_id", referencedColumnName = "id")
        val powerWire: WireAndRail?,

        @field:Column(name = "power_wire_count")
        var powerWireCount: Int?,

        @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "rail_id", referencedColumnName = "id")
        val rail: WireAndRail?,

        @field:Column(name = "rail_count")
        var railCount: Int?,

        @field:Column(name = "wires_resistance", nullable = false)
        var wiresResistance: Double,

        @field:Column(name = "rails_resistance")
        var railsResistance: Double?,

        @field:Column(name = "limit_amperage", nullable = false)
        var limitAmperage: Double,

        @field:Column(name = "limit_temperature", nullable = false)
        var limitTemperature: Int,

        @field:Column(name = "thermal_constant", nullable = false)
        var thermalConstant: Double,

        @field:Column(name = "a_limit", nullable = false)
        var aLimit: Double,

        @field:Column(name = "b_limit", nullable = false)
        var bLimit: Double,

        @field:Column(name = "r_v_limit", nullable = false)
        var rVLimit: Double,

        @field:Column(name = "contact_amperage")
        var contactAmperage: Double?

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DirectNetwork

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

