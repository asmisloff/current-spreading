package ru.vniizht.currentspreading.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.currentspreading.dao.enums.SchemaType

interface ElectricalSchemaRepository : JpaRepository<ElectricalSchema, Long> {
    fun findAllByType(type: SchemaType): List<ElectricalSchema>
}