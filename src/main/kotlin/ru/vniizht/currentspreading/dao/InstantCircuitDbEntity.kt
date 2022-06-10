package ru.vniizht.asuterkortes.dao.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.currentspreading.util.toDateTime
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@TypeDefs(
    TypeDef(name = "json", typeClass = JsonStringType::class),
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Table(name = "asu_ter_m_instant_circuit")
class InstantCircuitDbEntity(

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "electrical_schema_id", nullable = false)
    val electricalSchema: ElectricalSchema,

    @Column(name = "change_time", nullable = false)
    var changeTime: LocalDateTime = LocalDateTime.now(),

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description", nullable = false)
    var description: String,

    @field:Column(name = "type", nullable = false)
    @field:Enumerated(EnumType.STRING)
    var type: SchemaType,

    @Type(type = "jsonb")
    @Column(name = "data", columnDefinition = "jsonb")
    val data: InstantCircuitData<out BlockDto, out InstantCircuitSolutionDataEntry>,

    ) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InstantCircuitDbEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "schemaType",
    defaultImpl = InstantCircuitDataDC::class
)
@JsonSubTypes(
    JsonSubTypes.Type(InstantCircuitDataDC::class, name = "DC"),
    JsonSubTypes.Type(InstantCircuitDataAC::class, name = "AC"),
)
abstract class InstantCircuitData<B : BlockDto, S : InstantCircuitSolutionDataEntry>(
    val blocks: MutableList<B>,
    val solution: MutableList<S>
)

class InstantCircuitDataDC(blocks: MutableList<BlockDCDto>, solution: MutableList<InstantCircuitDCSolutionDataEntry>) :
    InstantCircuitData<BlockDCDto, InstantCircuitDCSolutionDataEntry>(blocks, solution)

class InstantCircuitDataAC(blocks: MutableList<BlockACDto>, solution: MutableList<InstantCircuitSolutionDataEntry>) :
    InstantCircuitData<BlockACDto, InstantCircuitSolutionDataEntry>(blocks, solution)

fun InstantCircuitDbEntity.toShortDto(): InstantCircuitShortDto = InstantCircuitShortDto(
    id = this.id,
    electricalSchemaId = this.electricalSchema.id!!,
    electricalSchemaName = this.electricalSchema.name,
    electricalSchemaDescription = this.electricalSchema.description,
    name = this.name,
    description = this.description,
    changeTime = this.changeTime.toDateTime(),
)