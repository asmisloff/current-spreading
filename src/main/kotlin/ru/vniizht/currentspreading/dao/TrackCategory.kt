package ru.vniizht.currentspreading.dao

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.currentspreading.dao.jsonb.DbSpeedLimit
import javax.persistence.*

@Entity
@TypeDefs(
    TypeDef(name = "json", typeClass = JsonStringType::class),
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Table(name = "asu_ter_k_main_track_category") //TODO переименовать?
class TrackCategory(

    @field:Id
    @field:Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @field:Column(name = "name", nullable = false)
    val name: String,

    @field:Column(name = "priority", nullable = false)
    var priority: Int,

    @field:Type(type = "jsonb")
    @field:Column(name = "speed_limits", nullable = true, columnDefinition = "jsonb")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    var speedLimits: List<DbSpeedLimit> = listOf(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "single_track_id")
    var singleTrack: SingleTrack?

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as TrackCategory

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 724188677

}