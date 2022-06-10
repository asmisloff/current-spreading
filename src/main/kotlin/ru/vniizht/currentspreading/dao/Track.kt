package ru.vniizht.currentspreading.dao

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "asu_ter_k_main_track")
@TypeDefs(
    TypeDef(name = "json", typeClass = JsonStringType::class),
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
class Track(

    @field:Id
    @field:Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @field:Column(name = "active", nullable = false)
    var active: Boolean = true,

    @field:Column(name = "change_time", nullable = false)
    var changeTime: LocalDateTime = LocalDateTime.now(),

    @field:Column(name = "road", nullable = false)
    var road: String,

    @field:Column(name = "name", nullable = false)
    var name: String,

    @field:Column(name = "description", nullable = false)
    var description: String,

    @field:Column(name = "number_of_tracks", nullable = false)
    var numberOfTracks: Int,

    @field:Column(name = "different_tracks", nullable = false)
    val differentTracks: Boolean,

    @OneToMany(mappedBy = "track", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    var singleTracks: MutableList<SingleTrack> = mutableListOf()

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Track

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 752605432

}
