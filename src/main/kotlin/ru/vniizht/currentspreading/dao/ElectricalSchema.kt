package ru.vniizht.currentspreading.dao

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.dto.BranchDto
import ru.vniizht.currentspreading.dto.MainSchemaDto
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@TypeDefs(
    TypeDef(name = "json", typeClass = JsonStringType::class),
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Table(name = "asu_ter_m_electrical_schema")
class ElectricalSchema(

    @field:Id
    @field:Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @field:Column(name = "active", nullable = false)
    var active: Boolean = true,

    @field:Column(name = "change_time", nullable = false)
    var changeTime: LocalDateTime = LocalDateTime.now(),

    @field:Column(name = "name", nullable = false)
    var name: String,

    @field:Column(name = "description", nullable = false)
    var description: String,

    @field:Column(name = "type", nullable = false)
    @field:Enumerated(EnumType.STRING)
    var type: SchemaType,

    @field:Column(name = "length", nullable = false)
    var length: String,

    @field:Column(name = "coordinates", nullable = false)
    var coordinates: String,

    @field:Column(name = "branch_count")
    var branchCount: Int = 0,

    @field:Column(name = "track_count")
    var trackCount: Int = 0,

    @field:Type(type = "jsonb")
    @field:Column(name = "main_schema", columnDefinition = "jsonb")
    val mainSchema: MainSchemaDto,

    @field:Type(type = "jsonb")
    @field:Column(name = "branches", columnDefinition = "jsonb")
    var branches: BranchList<out BranchDto> = BranchList(mutableListOf())

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ElectricalSchema

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun getCopy() = ElectricalSchema(
        id = -1,
        name = "${name}_копия",
        changeTime = LocalDateTime.now(),
        active = true,
        description = description,
        branchCount = branchCount,
        branches = branches,
        coordinates = coordinates,
        length = length,
        mainSchema = mainSchema,
        trackCount = trackCount,
        type = type
    )

    fun spatialBounds(): Pair<Double, Double> {
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        for (o in mainSchema.objects) {
            when {
                o.coordinate < min -> min = o.coordinate
                o.coordinate > max -> max = o.coordinate
            }
        }
        return Pair(min, max)
    }
}

class BranchList<T : BranchDto>(private val branches: MutableList<T>) : List<T> by branches {

    init {
        for (branch in branches) {
            branch.checkAndAmendIndices()
        }
    }

    companion object {
        fun <T : BranchDto> empty() = BranchList<T>(mutableListOf())
    }

    override fun toString(): String {
        return branches.toString()
    }

}
