package ru.vniizht.currentspreading.dao

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.asuterkortes.counter.tractive.TractiveStop
import ru.vniizht.currentspreading.dto.TractionCountRequestDto
import ru.vniizht.currentspreading.dao.jsonb.TractiveCalculateResult
import java.time.LocalDateTime
import javax.persistence.*


@Entity
@TypeDefs(
        TypeDef(name = "json", typeClass = JsonStringType::class),
        TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Table(name = "asu_ter_k_main_tractive_calculate")
class TractiveCalculate(

    @field:Id
    @field:Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "track_id", referencedColumnName = "id", updatable = false)
    var track: Track,

    @field:ManyToOne(fetch = FetchType.EAGER)
    @field:JoinColumn(name = "category_id", referencedColumnName = "id", updatable = false)
    var category: TrackCategory,

    @field:ManyToOne(fetch = FetchType.EAGER)
    @field:JoinColumn(name = "single_track_id", referencedColumnName = "id", updatable = false)
    val singleTrack: SingleTrack,

    @field:Column(name = "direction", updatable = false, nullable = false)
    val direction: Boolean, //нечет

    @field:Column(name = "active", nullable = false)
    var active: Boolean = true,

    @field:Column(name = "change_time", nullable = false)
    var changeTime: LocalDateTime = LocalDateTime.now(),

    @field:Column(name = "description", nullable = false)
    var description: String = "",

    @field:Column(name = "weight", nullable = false)
    val weight: Int,

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "locomotive_id", nullable = false)
    val locomotive: Locomotive,

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "train_id", nullable = false)
    val train: Train,

    @field:Type(type = "jsonb")
    @field:Column(name = "result", nullable = false, columnDefinition = "jsonb")
    var result: TractiveCalculateResult,

    @field:Type(type = "jsonb")
    @field:Column(name = "stop_coordinates", nullable = false, columnDefinition = "jsonb")
    val stops: List<TractiveStop>,

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as TractiveCalculate

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 328789091

}
