package ru.vniizht.currentspreading.controller

import mu.KotlinLogging
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ExceptionController {

    private val logger = KotlinLogging.logger { }

    @ExceptionHandler(Exception::class)
    fun handleIllegalStateException(e: IllegalStateException): ErrorResponseDto {
        logger.error(e.stackTraceToString())
        return ErrorResponseDto(e.message ?: "Неизвестная ошибка")
    }

}

data class ErrorResponseDto(val msg: String)