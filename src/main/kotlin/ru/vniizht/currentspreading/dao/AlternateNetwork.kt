package ru.vniizht.currentspreading.dao

import org.hibernate.annotations.Type
import ru.vniizht.asuterkortes.dto.AlternateParameters
import javax.persistence.*


@Entity
@Table(name = "asu_ter_m_alternate_network")
class AlternateNetwork(

    @field:Id
        @field:Column(name = "id")
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long? = null,

    @field:Column(name = "description", nullable = false)
        var description: String,

    @field:Column(name = "trackCount")
        var trackCount: Int,

    @field:Column(name = "earth_resistance")
        var earthResistance: Double,

    @field:Type(type = "jsonb")
        @field:Column(name = "parameters", columnDefinition = "jsonb")
        var parameters: List<AlternateParameters> = mutableListOf(),

    @field:OneToMany(mappedBy = "network", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
        var wireAndRailList: List<WiresToNetwork> = mutableListOf()

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlternateNetwork

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}