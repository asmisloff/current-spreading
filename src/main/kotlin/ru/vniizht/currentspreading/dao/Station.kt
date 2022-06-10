package ru.vniizht.currentspreading.dao

import org.hibernate.Hibernate
import javax.persistence.*

@Entity
@Table(name = "asu_ter_k_main_station")
class Station(

    @field:Id
    @field:Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "single_track_id", referencedColumnName = "id", updatable = false)
    val singleTrack: SingleTrack,

    @field:Column(name = "name", nullable = false)
    val name: String,

    @field:Column(name = "coordinate", nullable = false)
    val coordinate: Double,

    @field:Column(name = "loop_station", nullable = false)
    val loopStation: Boolean,

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Station

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 1719575920

}