package ru.vniizht.asuterkortes.dto

import ru.vniizht.currentspreading.dao.Car
import ru.vniizht.currentspreading.dao.enums.getNumberOfAxlesByNumber
import ru.vniizht.asuterkortes.dao.model.jsonb.ResistanceToMotion
import ru.vniizht.currentspreading.dto.ReferenceFullDto


data class CarFullDto(
        override val id: Long?,
        val changeTime: String?,
        override val name: String,
        val numberOfAxles: Int,
        val weight: Double,
        val length: Double,
        val resistanceToMotion: ResistanceToMotion
) : ReferenceFullDto() {

    override fun toEntity() = Car(
            id = id,
            weight = weight,
            length = length,
            name = name,
            numberOfAxles = getNumberOfAxlesByNumber(numberOfAxles),
            resistanceToMotion = resistanceToMotion
    )
}