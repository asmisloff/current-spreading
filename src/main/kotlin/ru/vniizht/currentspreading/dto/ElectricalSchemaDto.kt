package ru.vniizht.currentspreading.dto

import com.fasterxml.jackson.annotation.*
import ru.vniizht.currentspreading.dao.BranchList
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.currentspreading.core.acnew.BlockAC
import ru.vniizht.currentspreading.core.acnew.BlockJumperAC
import ru.vniizht.currentspreading.core.acnew.BlockJumperAcd
import ru.vniizht.currentspreading.core.acnew.BlockSplitterAc
import ru.vniizht.currentspreading.core.dcnew.BlockDC
import ru.vniizht.currentspreading.core.dcnew.BlockJumperDC
import ru.vniizht.currentspreading.core.dcnew.BlockSplitterDc
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.util.toDateTime
import kotlin.math.pow

abstract class ElectricalSchemaDto {
    abstract var id: Long
    abstract val name: String
    abstract val description: String
    abstract val changeTime: String?
    abstract val length: String
    abstract val coordinates: String
    abstract val trackCount: Int
    abstract val mainSchema: MainSchemaDto
    abstract val branches: BranchList<out BranchDto>
    abstract val type: String
}

data class ElectricalSchemaShortDto(
    var id: Long,
    val name: String,
    val description: String,
    val changeTime: String?,
    val length: String,
    val coordinates: String,
    val trackCount: Int,
    val type: String
)

data class SchemaParametersDto(
    val toolsDurability: Double,
    val networkDurability: Double,
    val stationDurability: Double,
    val trackCount: Int,
    val trackName: String,
    val schemaName: String
)

open class FidersDto

abstract class FiderDto {
    abstract val length: Double
    abstract var coordinate: Double
    abstract var branchIndex: Int
}

open class StationParametersDto

var INFINITY_NETWORK = 10.0.pow(9)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = DCMainSchemaDto::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DCMainSchemaDto::class, name = "DC"),
    JsonSubTypes.Type(value = ACMainSchemaDto::class, name = "AC"),
    JsonSubTypes.Type(value = ACDMainSchemaDto::class, name = "ACD")
)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class MainSchemaDto {
    abstract val objects: MutableList<ObjectDto>
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DCBranchDto::class, name = "Ветвь - DC"),
    JsonSubTypes.Type(value = ACBranchDto::class, name = "Ветвь - AC")
)
abstract class BranchDto(
    open val trackQty: Int,
    override var coordinate: Double,
    override val name: String,
    override var branchIndex: Int
) : ObjectDto() {
    abstract fun checkAndAmendIndices()
}

abstract class NetworkDto {
    abstract val endSection: Double
    abstract val network: NetworkShortDto
    abstract var branchIndex: Int
}

abstract class NetworkShortDto {
    abstract val id: Long
    abstract val name: String
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DCStationDto::class, name = "ЭЧЭ - DC"),
    JsonSubTypes.Type(value = DCSectionDto::class, name = "ПС - DC"),
    JsonSubTypes.Type(value = ACStationDto::class, name = "ЭЧЭ - AC"),
    JsonSubTypes.Type(value = ACSectionDto::class, name = "ПС - AC"),
    JsonSubTypes.Type(value = ACDStationDto::class, name = "ЭЧЭ - ACD"),
    JsonSubTypes.Type(value = ACDSectionDto::class, name = "ПС - ACD"),
    JsonSubTypes.Type(value = ACDSectionWithAtpDto::class, name = "ПСА - ACD"),
    JsonSubTypes.Type(value = AcdAtpDto::class, name = "АТП - ACD"),
    JsonSubTypes.Type(value = ConnectorDto::class, name = "ППС"),
    JsonSubTypes.Type(value = DCStationDto::class, name = "ЭЧЭ - Тяговая подстанция"),
    JsonSubTypes.Type(value = ConnectorDto::class, name = "ППС пункт паралл.соедин."),
    JsonSubTypes.Type(value = DCSectionDto::class, name = "ПС - пост секционирования"),
    JsonSubTypes.Type(value = DisconnectorDto::class, name = "ПРС - продольный разъедин."),
    JsonSubTypes.Type(value = BranchPointDto::class, name = "ОТВ - ответвление"),
    JsonSubTypes.Type(value = AcdBranchPointDto::class, name = "ОТВ - ответвление (2х25)"),
    JsonSubTypes.Type(value = DCBranchDto::class, name = "Ветвь - DC"),
    JsonSubTypes.Type(value = ACBranchDto::class, name = "Ветвь - AC")
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class ObjectDto {
    abstract val coordinate: Double
    abstract val name: String
    abstract val fiders: FidersDto?
    abstract var branchIndex: Int
}

data class ConnectorDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: Connector?,
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockAc, ConvertableToBlockDc {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockAC {
        requireNotNull(fiders) { "Для ППС-$index ($name, $coordinate км) не заданы номера путей" }

        return when (supplyQty) {
            null -> BlockJumperAC(coordinate, "ППС-$index", fiders.secondLine, fiders.firstLine, trackQty, branchIndex)
            else -> BlockJumperAcd("ППС-$index", coordinate, fiders.secondLine, fiders.firstLine, trackQty, supplyQty)
        }.also { it.description = name }
    }

    override fun toBlock(index: Int, trackQty: Int): BlockDC {
        return BlockJumperDC(
            axisCoordinate = coordinate,
            blockLabel = "J$index",
            track1Number = fiders!!.firstLine,
            track2Number = fiders.secondLine,
            branchIndex = branchIndex
        ).also { it.description = name }
    }

}

data class DisconnectorDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: StationFiders? = null,
    override var branchIndex: Int = 0
) : ObjectDto()

data class Connection<T>(
    @JsonAlias("first") val firstTrackNumber: T,
    @JsonAlias("second") val secondTrackNumber: T,
    val firstConnectionPoint: Double? = null,
    val secondConnectionPoint: Double? = null
) {
    @JsonIgnore fun getTrackPair() = Pair(firstTrackNumber, secondTrackNumber)
}

data class BranchPointDto(
    override val coordinate: Double,
    override val name: String,
    val connectedBranchIndex: Int,
    val wiringLayout: List<Connection<Int>>,
    override var branchIndex: Int = 0,
    override val fiders: StationFiders? = null // не используется, всегда null
) : ObjectDto(), ConvertableToBlockAc, ConvertableToBlockDc {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockSplitterAc {
        return BlockSplitterAc(
            axisCoordinate = coordinate,
            blockLabel = "ОТВ-$index",
            connectedBranchIndex = connectedBranchIndex,
            wiringLayout = wiringLayout,
            branchIndex = branchIndex
        ).also { it.description = name }
    }

    override fun toBlock(index: Int, trackQty: Int): BlockDC {
        return BlockSplitterDc(
            axisCoordinate = coordinate,
            blockLabel = "ОТВ$index",
            connectedBranchIndex = connectedBranchIndex,
            wiringLayout = wiringLayout,
            branchIndex = branchIndex
        )
    }


}

data class Connector(
    val firstLine: Int,
    val secondLine: Int
) : FidersDto()

abstract class StationFiders : FidersDto() {
    abstract val sucking: FiderDto
    abstract val trackFiders: List<TrackFiderDto>
}

abstract class SectionFiders : FidersDto() {
    abstract val trackFiders: List<TrackFiderDto>
}

abstract class TrackFiderDto {
    abstract val leftFider: FiderDto
    abstract val rightFider: FiderDto
}

fun ElectricalSchema.toShortDto() = ElectricalSchemaShortDto(
    id = id!!,
    description = description,
    changeTime = changeTime.toDateTime(),
    coordinates = coordinates,
    length = length,
    name = name,
    trackCount = trackCount,
    type = type.russianName
)

fun ElectricalSchema.toDto(): ElectricalSchemaDto {
    return when (type) {
        SchemaType.DC -> toDcDto()
        SchemaType.AC -> toAcDto()
        SchemaType.ACD -> toAcdDto()
    }
}

