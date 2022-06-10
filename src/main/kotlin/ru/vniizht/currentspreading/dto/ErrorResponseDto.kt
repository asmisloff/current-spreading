package ru.vniizht.asuterkortes.dto

import java.util.*

data class ErrorResponseDto(
        val message: String,
        var id: UUID? = UUID.randomUUID(),
        val code: String? = null,
        val details: String? = null
)