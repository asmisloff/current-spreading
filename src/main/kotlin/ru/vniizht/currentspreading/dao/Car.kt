package ru.vniizht.currentspreading.dao

import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import ru.vniizht.currentspreading.dao.enums.NumberOfAxles
import ru.vniizht.asuterkortes.dao.model.jsonb.ResistanceToMotion
import ru.vniizht.asuterkortes.dto.CarFullDto
import ru.vniizht.currentspreading.util.toDateTime
import java.time.LocalDateTime
import javax.persistence.*


@Entity
@Table(name = "asu_ter_k_main_car")
class Car(

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

        @field:Column(name = "number_of_axles", nullable = false)
        @field:Enumerated(EnumType.STRING)
        var numberOfAxles: NumberOfAxles,

        @field:Column(name = "weight", nullable = false)
        var weight: Double,

        @field:Column(name = "length", nullable = false)
        var length: Double,

        @field:Type(type = "jsonb")
        @field:Column(name = "resistance_to_motion", nullable = false, columnDefinition = "jsonb")
        var resistanceToMotion: ResistanceToMotion

) : ReferenceEntity<CarFullDto>() {

    override fun toFullDto() = CarFullDto(
            id = id!!,
            changeTime = changeTime.toDateTime(),
            name = name,
            numberOfAxles = numberOfAxles.number,
            length = length,
            weight = weight,
            resistanceToMotion = resistanceToMotion
    )

        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
                other as Car

                return id != null && id == other.id
        }

        override fun hashCode(): Int = 2133657150
}