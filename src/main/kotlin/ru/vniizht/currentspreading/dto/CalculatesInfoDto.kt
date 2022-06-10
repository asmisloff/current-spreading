package ru.vniizht.asuterkortes.dto

import ru.vniizht.currentspreading.dao.enums.SchemaType

data class CalculatesInfoDto(
    val type: CalculatesInfoTypes,
    val date: String,
    val road: String?,
    val schemaName: String? = null,
    val schemaType: SchemaType? = null,
    val trackName: String?,
    val name: String,
    val description: String,
    val id: Long?,
    val userLogin: String?,
)

data class CalculateInfoWithTotalPagesDto(
    val totalPages: Int,
    val infos: List<CalculatesInfoDto>,
)

enum class CalculatesInfoTypes(val priority: Int) {
    TRACTIVE(1),
    INTERVAL(3),
    CAPACITY(2),
    PARALLEL(4)
}

