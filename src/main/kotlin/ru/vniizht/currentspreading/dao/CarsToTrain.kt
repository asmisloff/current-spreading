package ru.vniizht.currentspreading.dao

import org.hibernate.Hibernate
import ru.vniizht.currentspreading.dto.CarsToTrainDto
import java.io.Serializable
import javax.persistence.*


@Entity
@Table(name = "asu_ter_k_main_cars_to_train")
class CarsToTrain(

    @field:EmbeddedId
    val id: CarsToTrainId,

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "train_id", referencedColumnName = "id", insertable = false, updatable = false)
    val train: Train? = null,

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "car_id", referencedColumnName = "id", insertable = false, updatable = false)
    val car: Car? = null,

    @field:Column(name = "count", nullable = false)
    val count: Int

) {
    fun toDto() = CarsToTrainDto(
        id = car!!.id!!,
        numberOfCars = count
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as CarsToTrain

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
class CarsToTrainId(
    @field:Column(name = "car_id", updatable = false, nullable = false)
    val carId: Long,

    @field:Column(name = "train_id", updatable = false, nullable = false)
    val trainId: Long
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CarsToTrainId

        if (carId != other.carId) return false
        if (trainId != other.trainId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = carId.hashCode()
        result = 31 * result + (trainId.hashCode())
        return result
    }
}