package ru.vniizht.currentspreading.dao

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.currentspreading.dto.ReferenceFullDto
import java.io.Serializable
import java.time.LocalDateTime
import javax.persistence.MappedSuperclass

@TypeDefs(
        TypeDef(name = "json", typeClass = JsonStringType::class),
        TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@MappedSuperclass
abstract class ReferenceEntity<F : ReferenceFullDto> : Serializable {

    abstract val id: Long?
    abstract var active: Boolean
    abstract var changeTime: LocalDateTime
    abstract var name: String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceEntity<*>) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    abstract fun toFullDto(): F
}