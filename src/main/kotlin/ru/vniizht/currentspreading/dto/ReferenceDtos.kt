package ru.vniizht.currentspreading.dto

import ru.vniizht.currentspreading.dao.ReferenceEntity


abstract class ReferenceFullDto {
    abstract val id: Long?
    abstract val name: String

    abstract fun toEntity(): ReferenceEntity<out ReferenceFullDto>
}