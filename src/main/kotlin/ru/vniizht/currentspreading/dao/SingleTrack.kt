package ru.vniizht.currentspreading.dao

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.asuterkortes.counter.tractive.ProfileElement
import ru.vniizht.currentspreading.dao.enums.TrackType
import javax.persistence.*

@Entity
@TypeDefs(
    TypeDef(name = "json", typeClass = JsonStringType::class),
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Table(name = "asu_ter_k_main_single_track")
class SingleTrack(

    @field:Id
    @field:Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @field:ManyToOne(fetch = FetchType.EAGER)
    @field:JoinColumn(name = "track_id")
    val track: Track? = null,

    @field:Column(name = "track_number", nullable = false)
    val trackNumber: Int,

    @field:Column(name = "type", nullable = false)
    @field:Enumerated(EnumType.STRING)
    var type: TrackType,

    @field:Type(type = "jsonb")
    @field:Column(name = "elements", nullable = true, columnDefinition = "jsonb")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    var profile: MutableList<ProfileElement> = mutableListOf(), // Список должен быть mutable, иначе Hibernate не сможет его обновлять

    @OneToMany(mappedBy = "singleTrack", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    var stations: MutableList<Station> = mutableListOf(),

    @OneToMany(mappedBy = "singleTrack", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var categories: MutableList<TrackCategory> = mutableListOf()
) {

    fun actualStations() = stations

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as SingleTrack

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 212447578
}
