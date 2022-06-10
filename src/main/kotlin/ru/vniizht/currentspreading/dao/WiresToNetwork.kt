package ru.vniizht.currentspreading.dao

import ru.vniizht.currentspreading.dao.enums.WireAndRailType
import javax.persistence.*


@Entity
@Table(name = "asu_ter_m_alternate_network_to_wires")
class WiresToNetwork(

    @field:Id
        @field:Column(name = "id")
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long? = null,

    @field:ManyToOne
        @field:JoinColumn(name = "wire_id")
        val wire: WireAndRail? = null,

    @field:ManyToOne
        @field:JoinColumn(name = "network_id")
        val network: AlternateNetwork? = null,

    @field:Column(name = "type", nullable = false)
        @field:Enumerated(EnumType.STRING)
        var type: WireAndRailType,

    @field:Column(name = "track_number", nullable = false)
        val trackNumber: Int,

    @field:Column(name = "height_position", nullable = false)
        val heightPosition: Double,

    @field:Column(name = "horizontal_position", nullable = false)
        val horizontalPosition: Double

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WiresToNetwork

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "WiresToNetwork(id=$id, wire=$wire, network=$network, type=$type, trackNumber=$trackNumber, heightPosition=$heightPosition, horizontalPosition=$horizontalPosition)"
    }
}
