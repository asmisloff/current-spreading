package ru.vniizht.currentspreading.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.vniizht.currentspreading.dao.ElectricalSchema

interface ElectricalSchemaRepository : JpaRepository<ElectricalSchema, Long>