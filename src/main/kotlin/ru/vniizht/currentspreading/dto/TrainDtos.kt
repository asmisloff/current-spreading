package ru.vniizht.currentspreading.dto

import ru.vniizht.currentspreading.dao.Train
import ru.vniizht.currentspreading.dao.enums.getBrakeTypeOfRussianNames
import ru.vniizht.asuterkortes.dao.model.jsonb.ResistanceToMotion


data class TrainFullDto(
    override val id: Long?,
    val changeTime: String?,
    override val name: String,
    val weight: Double,
    val brakeType: String,
    val brakeForce: Double? = null,
    val generatorPower: Double? = null,
    val resistanceToMotion: ResistanceToMotion? = null,
    val cars: List<CarsToTrainDto>,
    val numberOfCars: Int,
    val numberOfAxles: Int,
    val length: Double
) : ReferenceFullDto() {
    override fun toEntity() = Train(
        id = id,
        name = name,
        brakeBlockType = getBrakeTypeOfRussianNames(brakeType),
        brakeForce = brakeForce,
        undercarGeneratorPower = generatorPower,
        weight = weight,
        resistanceToMotion = resistanceToMotion
    )
}

data class CarsToTrainDto(
    val id: Long,
    val numberOfCars: Int
)