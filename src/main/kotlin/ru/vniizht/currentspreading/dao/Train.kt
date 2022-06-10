package ru.vniizht.currentspreading.dao

import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import ru.vniizht.asuterkortes.dao.model.jsonb.ResistanceToMotion
import ru.vniizht.currentspreading.dto.TrainFullDto
import ru.vniizht.currentspreading.util.toDateTime
import ru.vniizht.currentspreading.dao.enums.BrakeType
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "asu_ter_k_main_train")
class Train(

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

        @field:Column(name = "brake_block_type", nullable = false)
        @field:Enumerated(EnumType.STRING)
        var brakeBlockType: BrakeType, // Тип тормозных колодок

        @field:Column(name = "brake_force")
        var brakeForce: Double?, // Расчетная сила нажатия колодок на ось Kp, т

        @field:Column(name = "undercar_generator_power")
        var undercarGeneratorPower: Double?, // Мощность подвагонного генератора P`пг \n (для пассажирских вагонов)

        @field:Column(name = "weight")
        var weight: Double, // Масса, т

        @field:Column(name = "length")
        var length: Double? = null, // Длина, м

        /**
        * Коэффициенты формулы основного удельного сопротивления движению (Н/т)
        * [B, C, D] для звеньевого и стыкового путей
        */
        @field:Type(type = "jsonb")
        @field:Column(name = "resistance_to_motion", columnDefinition = "jsonb")
        var resistanceToMotion: ResistanceToMotion? = null,

        @field:OneToMany(mappedBy = "train", fetch = FetchType.LAZY)
        var carsToTrain: List<CarsToTrain> = mutableListOf()

) : ReferenceEntity<TrainFullDto>() {
    override fun toFullDto() = TrainFullDto(
            id = id!!,
            cars = carsToTrain.map { it.toDto() },
            brakeForce = brakeForce,
            name = name,
            weight = weight,
            resistanceToMotion = resistanceToMotion!!,
            brakeType = brakeBlockType.russianName,
            generatorPower = undercarGeneratorPower,
            numberOfCars = carsToTrain.sumOf { it.count },
            length = carsToTrain.sumOf { it.count * it.car!!.length },
            numberOfAxles = carsToTrain.sumOf { it.count * it.car!!.numberOfAxles.number },
            changeTime = changeTime.toDateTime()
    )

        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
                other as Train

                return id != null && id == other.id
        }

        override fun hashCode(): Int = 776844613
}