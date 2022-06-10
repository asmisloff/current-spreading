package ru.vniizht.asuterkortes.dto

import com.fasterxml.jackson.annotation.*
import org.apache.commons.math3.complex.Complex
import ru.vniizht.asuterkortes.counter.circuit.BranchFeederInfo
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.dto.ACNetworkDto
import ru.vniizht.currentspreading.dto.Connection
import ru.vniizht.currentspreading.dto.DCNetworkDto

data class InstantCircuitDcDto(
    val id: Long?,
    val electricalSchemaId: Long,
    val electricalSchemaName: String,
    val electricalSchemaDescription: String,
    val name: String,
    val description: String,
    val changeTime: String,
    val type: SchemaType = SchemaType.DC,
    val blocks: MutableList<BlockDCDto>,
    val solution: MutableList<InstantCircuitDCSolutionDataEntry>
)

data class InstantCircuitAcDto(
    val id: Long?,
    val electricalSchemaId: Long,
    val electricalSchemaName: String,
    val electricalSchemaDescription: String,
    val name: String,
    val description: String,
    val changeTime: String?,
    val type: SchemaType,
    val blocks: MutableList<BlockACDto>,
    val solution: MutableList<InstantCircuitSolutionDataEntry>
)

data class InstantCircuitShortDto(
    val id: Long?,
    val electricalSchemaId: Long,
    val electricalSchemaName: String,
    val electricalSchemaDescription: String,
    val name: String,
    val description: String,
    val changeTime: String
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "schemaType")
@JsonSubTypes(
    JsonSubTypes.Type(value = InstantCircuitDCSolutionDataEntry::class, name = "DC"),
    JsonSubTypes.Type(value = InstantCircuitACSolutionDataEntry::class, name = "AC"),
    JsonSubTypes.Type(value = InstantCircuitBranchACSolutionDataEntry::class, name = "BranchAC"),
    JsonSubTypes.Type(value = InstantCircuitBranchDCSolutionDataEntry::class, name = "BranchDC"),
    JsonSubTypes.Type(value = InstantCircuitSplitterACSolutionDataEntry::class, name = "SplitterAC"),
    JsonSubTypes.Type(value = InstantCircuitSplitterDCSolutionDataEntry::class, name = "SplitterDC"),
    JsonSubTypes.Type(value = InstantCircuitAcdSolutionDataEntry::class, name = "ACD"),
)
@JsonInclude(JsonInclude.Include.NON_NULL)
interface InstantCircuitSolutionDataEntry {
    val coordinate: Double
    val objectName: String
    val description: String
}

interface IPayloadSolution {
    fun axisCoordinate(): Double
    fun isPayload(): Boolean
    fun trackNumber(): Int?
    fun routeIndex(): Int?
    fun pantographVoltage(): Double?
}

@JsonIgnoreProperties(ignoreUnknown = true)
open class InstantCircuitDCSolutionDataEntry(
    override val coordinate: Double,
    override val objectName: String = "",
    override val description: String,
    val particularAttributes: MutableMap<String, String> = mutableMapOf(),
    val amperages: MutableList<Double> = mutableListOf(),
    var voltages: MutableList<Double> = mutableListOf()
) : InstantCircuitSolutionDataEntry, IPayloadSolution {
    @JsonIgnore
    override fun axisCoordinate() = coordinate

    @JsonIgnore
    override fun isPayload() = objectName.contains("Нагрузка")

    @JsonIgnore
    override fun trackNumber() = particularAttributes["trackNumber"]?.toInt()

    @JsonIgnore
    override fun routeIndex() = particularAttributes["routeIndex"]?.toInt()

    @JsonIgnore
    override fun pantographVoltage(): Double? {
        return when {
            isPayload() -> voltages[0]
            else -> null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstantCircuitBranchDCSolutionDataEntry(
    override val coordinate: Double,
    override val objectName: String = "",
    override val description: String,
    val branchSchemaSolutions: List<InstantCircuitDCSolutionDataEntry>
) : InstantCircuitDCSolutionDataEntry(coordinate, objectName, description)

data class InstantCircuitSplitterDCSolutionDataEntry(
    override val coordinate: Double,
    override val objectName: String = "",
    override val description: String,
    val wiringAmperages: List<SplitterWiringDcSolution>,
    val wiringVoltages: List<SplitterWiringDcSolution>
) : InstantCircuitDCSolutionDataEntry(coordinate, objectName, description)

data class SplitterWiringDcSolution(val connectedTrackNumbers: Pair<Int, Int>, val value: Double)

@JsonIgnoreProperties(ignoreUnknown = true)
open class InstantCircuitACSolutionDataEntry(
    override val coordinate: Double,
    override val objectName: String = "",
    override val description: String,
    val particularAttributes: MutableMap<String, String>? = null,
    val amperages: MutableList<Complex> = mutableListOf(),
    var voltages: MutableList<Complex> = mutableListOf(),
    val trackNumber: Int? = particularAttributes?.get("trackNumber")?.toInt(),
    val routeIndex: Int? = particularAttributes?.get("routeIndex")?.toInt()
) : InstantCircuitSolutionDataEntry, IPayloadSolution {
    @JsonIgnore
    override fun axisCoordinate() = coordinate

    @JsonIgnore
    override fun isPayload() = objectName.contains("Нагрузка")

    @JsonIgnore
    override fun routeIndex() = particularAttributes?.get("trackNumber")?.toInt()

    @JsonIgnore
    override fun trackNumber() = particularAttributes?.get("trackNumber")?.toInt()

    @JsonIgnore
    override fun pantographVoltage(): Double? {
        return when {
            isPayload() -> voltages[0].abs()
            else -> null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class InstantCircuitBranchACSolutionDataEntry(
    override val coordinate: Double,
    override val objectName: String = "",
    override val description: String,
    val branchSchemaSolutions: List<InstantCircuitSolutionDataEntry>
) : InstantCircuitACSolutionDataEntry(coordinate, objectName, description)

data class InstantCircuitSplitterACSolutionDataEntry(
    override val coordinate: Double,
    override val objectName: String = "",
    override val description: String,
    val wiringAmperages: List<SplitterWiringAcSolution>,
    val wiringVoltages: List<SplitterWiringAcSolution>
) : InstantCircuitACSolutionDataEntry(coordinate, objectName, description)

data class SplitterWiringAcSolution(val connectedTrackNumbers: Pair<Int, Int>, val value: Complex)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InstantCircuitAcdSolutionDataEntry(
    override val coordinate: Double,
    override val objectName: String,
    override val description: String,
    val trackQty: Int,
    val cnAmperages: List<Complex>,
    val cnVoltages: List<Complex>,
    val supplyAmperages: List<Complex>,
    val supplyVoltages: List<Complex>,
    val trackNumber: Int? = null,
    val routeIndex: Int? = null
) : InstantCircuitSolutionDataEntry, IPayloadSolution {
    @JsonIgnore
    override fun axisCoordinate() = coordinate

    @JsonIgnore
    override fun isPayload() = routeIndex != null

    @JsonIgnore
    override fun trackNumber() = trackNumber

    @JsonIgnore
    override fun routeIndex() = routeIndex

    @JsonIgnore
    override fun pantographVoltage(): Double? {
        return when {
            isPayload() -> cnVoltages[0].abs()
            else -> null
        }
    }
}

interface BlockDto


@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "schemaType"
)
@JsonSubTypes(
    JsonSubTypes.Type(BlockSSDCDto::class, name = "SS"),
    JsonSubTypes.Type(BlockSPDCDto::class, name = "SP"),
    JsonSubTypes.Type(BlockPayloadDCDto::class, name = "PL"),
    JsonSubTypes.Type(BlockJumperDCDto::class, name = "J"),
    JsonSubTypes.Type(BlockBranchDcDto::class, name = "BranchDc"),
    JsonSubTypes.Type(BlockSplitterDcDto::class, name = "SplitterDc"),
    JsonSubTypes.Type(BlockShortCircuitPointDcDto::class, name = "SCP"),
)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class BlockDCDto : BlockDto {
    abstract val axisCoordinate: Double
    abstract val blockLabel: String
    abstract val description: String
    abstract val branchIndex: Int
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BlockSSDCDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    val suckingCoordinate: Double,
    /* Здесь нужны специальные аннотации, иначе Jackson переведет первые 3 символа в нижний регистр */
    @get:JsonProperty("zSS") val zSS: Double,
    @get:JsonProperty("xCompXInv") val xCompXInv: Double,
    @get:JsonProperty("vOut") val vOut: Double = 0.0,
    val leftFeederResistances: List<Double> = listOf(),
    val rightFeederResistances: List<Double> = listOf(),
    val leftFeederConnectionPoints: List<Double> = listOf(),
    val rightFeederConnectionPoints: List<Double> = listOf(),
    val mainSwitchState: Boolean = true,
    val leftSwitchesState: List<Boolean> = List(leftFeederResistances.size) { true },
    val rightSwitchesState: List<Boolean> = List(rightFeederResistances.size) { true },
    val branchSwitchesState: List<BranchFeederInfo<Double>> = emptyList(),
    override val branchIndex: Int = 0
) : BlockDCDto()

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "schemaType"
)
@JsonSubTypes(
    JsonSubTypes.Type(BlockSSACDto::class, name = "SS"),
    JsonSubTypes.Type(BlockSSAcdDto::class, name = "SSd"),
    JsonSubTypes.Type(BlockSpAcDto::class, name = "SP"),
    JsonSubTypes.Type(BlockSpaAcdDto::class, name = "SPAd"),
    JsonSubTypes.Type(BlockSpAcdDto::class, name = "SPd"),
    JsonSubTypes.Type(BlockPayloadACDto::class, name = "PL"),
    JsonSubTypes.Type(BlockJumperACDto::class, name = "J"),
    JsonSubTypes.Type(BlockAtpAcdDto::class, name = "ATPd"),
    JsonSubTypes.Type(BlockSplitterAcDto::class, name = "SplitterAc"),
    JsonSubTypes.Type(BlockBranchAcDto::class, name = "BranchAc")
)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class BlockACDto : BlockDto {
    abstract val axisCoordinate: Double
    abstract val blockLabel: String
    abstract val description: String
    abstract val branchIndex: Int
}

@JsonInclude(JsonInclude.Include.NON_NULL)
open class BlockSSACDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    @get:JsonProperty("vOut") open val vOut: Complex,
    @get:JsonProperty("zSS") open val zSS: Complex,
    open val mainSwitchState: Boolean = true,

    @JsonAlias("leftFeederResistances")
    open val leftCnFeederResistances: List<Complex> = listOf(),
    @JsonAlias("rightFeederResistances")
    open val rightCnFeederResistances: List<Complex> = listOf(),
    @JsonAlias("leftFeederConnectionPoints")
    open val leftCnFeederConnectionPoints: List<Double> = listOf(),
    @JsonAlias("rightFeederConnectionPoints")
    open val rightCnFeederConnectionPoints: List<Double> = listOf(),
    @JsonAlias("leftSwitchesState")
    open val leftCnSwitchesState: List<Boolean> = List(leftCnFeederResistances.size) { true },
    @JsonAlias("rightSwitchesState")
    open val rightCnSwitchesState: List<Boolean> = List(rightCnFeederResistances.size) { true },

    val branchFeederSwitchesState: List<BranchFeederInfo<Complex>> = emptyList(),
    override val branchIndex: Int = 0
) : BlockACDto()

@JsonInclude(JsonInclude.Include.NON_NULL)
class BlockSSAcdDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    @get:JsonProperty("vOut") override val vOut: Complex,
    @get:JsonProperty("zSS") override val zSS: Complex,
    override val mainSwitchState: Boolean = true,

    override val leftCnFeederResistances: List<Complex>,
    override val rightCnFeederResistances: List<Complex>,
    override val leftCnFeederConnectionPoints: List<Double>,
    override val rightCnFeederConnectionPoints: List<Double>,
    override val leftCnSwitchesState: List<Boolean>,
    override val rightCnSwitchesState: List<Boolean>,

    val leftSupplyFeederResistances: List<Complex>,
    val rightSupplyFeederResistances: List<Complex>,
    val leftSupplyFeederConnectionPoints: List<Double>,
    val rightSupplyFeederConnectionPoints: List<Double>,
    val leftSupplySwitchesState: List<Boolean>,
    val rightSupplySwitchesState: List<Boolean>,
    branchFeederSwitchesState: List<BranchFeederInfo<Complex>> = emptyList(),
    branchIndex: Int = 0
) : BlockSSACDto(
    axisCoordinate,
    blockLabel,
    description,
    vOut,
    zSS,
    mainSwitchState,
    leftCnFeederResistances,
    rightCnFeederResistances,
    branchIndex = branchIndex,
    branchFeederSwitchesState = branchFeederSwitchesState
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BlockPayloadACDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    val amperage: Complex,
    val trackNumber: Int,
    val trackQty: Int,
    val supplyQty: Int? = null,
    override val branchIndex: Int = 0
) : BlockACDto()

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BlockJumperACDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    val track1Number: Int,
    val track2Number: Int,
    val switchState: Boolean,
    val trackQty: Int? = null,
    override val branchIndex: Int = 0
//    val supplyQty: Int? = null
) : BlockACDto()

@JsonInclude(JsonInclude.Include.NON_NULL)
open class BlockSpAcDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    open val mainSwitchState: Boolean,
    open val medianSwitchState: Boolean,

    @JsonAlias("leftSwitchesState")
    open val leftCnSwitchesState: List<Boolean>,
    @JsonAlias("rightSwitchesState")
    open val rightCnSwitchesState: List<Boolean>,
    @JsonAlias("leftFeederResistances")
    open val leftCnFeederResistances: List<Complex>,
    @JsonAlias("rightFeederResistances")
    open val rightCnFeederResistances: List<Complex>,
    @JsonAlias("leftFeederConnectionPoints")
    open val leftCnFeederConnectionPoints: List<Double>,
    @JsonAlias("rightFeederConnectionPoints")
    open val rightCnFeederConnectionPoints: List<Double>,

    open val branchFeederSwitchesState: List<BranchFeederInfo<Complex>> = emptyList(),
    override val branchIndex: Int = 0
) : BlockACDto() {

    override fun toString(): String {
        return "BlockSpAcDto(axisCoordinate=$axisCoordinate, blockLabel='$blockLabel', description='$description', mainSwitchState=$mainSwitchState, medianSwitchState=$medianSwitchState, leftCnSwitchesState=$leftCnSwitchesState, rightCnSwitchesState=$rightCnSwitchesState, leftCnFeederResistances=$leftCnFeederResistances, rightCnFeederResistances=$rightCnFeederResistances, leftCnFeederConnectionPoints=$leftCnFeederConnectionPoints, rightCnFeederConnectionPoints=$rightCnFeederConnectionPoints)"
    }

}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BlockSpAcdDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",

    override val mainSwitchState: Boolean,
    override val medianSwitchState: Boolean,
    override val leftCnSwitchesState: List<Boolean>,
    override val rightCnSwitchesState: List<Boolean>,

    override val leftCnFeederResistances: List<Complex>,
    override val rightCnFeederResistances: List<Complex>,
    override val leftCnFeederConnectionPoints: List<Double>,
    override val rightCnFeederConnectionPoints: List<Double>,
    val leftSupplyFeederResistances: List<Complex>,

    val rightSupplyFeederResistances: List<Complex>,
    val leftSupplyFeederConnectionPoints: List<Double>,
    val rightSupplyFeederConnectionPoints: List<Double>,
    override val branchFeederSwitchesState: List<BranchFeederInfo<Complex>> = emptyList(),
    override val branchIndex: Int = 0
) : BlockSpAcDto(
    axisCoordinate,
    blockLabel,
    description,
    mainSwitchState,
    medianSwitchState,
    leftCnSwitchesState,
    rightCnSwitchesState,
    leftCnFeederResistances,
    rightCnFeederResistances,
    leftCnFeederConnectionPoints,
    rightCnFeederConnectionPoints,
    branchFeederSwitchesState,
    branchIndex
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BlockSpaAcdDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",

    override val mainSwitchState: Boolean,
    override val medianSwitchState: Boolean,

    override val leftCnSwitchesState: List<Boolean>,
    override val rightCnSwitchesState: List<Boolean>,
    override val leftCnFeederResistances: List<Complex>,
    override val rightCnFeederResistances: List<Complex>,
    override val leftCnFeederConnectionPoints: List<Double>,
    override val rightCnFeederConnectionPoints: List<Double>,

    val leftSupplyFeederResistances: List<Complex>,
    val rightSupplyFeederResistances: List<Complex>,
    val leftSupplyFeederConnectionPoints: List<Double>,
    val rightSupplyFeederConnectionPoints: List<Double>,
    val leftSupplySwitchesState: List<Boolean>,
    val rightSupplySwitchesState: List<Boolean>,
    override val branchFeederSwitchesState: List<BranchFeederInfo<Complex>> = emptyList(),
    override val branchIndex: Int = 0
) : BlockSpAcDto(
    axisCoordinate,
    blockLabel,
    description,
    mainSwitchState,
    medianSwitchState,
    leftCnSwitchesState,
    rightCnSwitchesState,
    leftCnFeederResistances,
    rightCnFeederResistances,
    leftCnFeederConnectionPoints,
    rightCnFeederConnectionPoints,
    branchFeederSwitchesState,
    branchIndex
)

/** Автотрансформатор */
data class BlockAtpAcdDto(
    override val axisCoordinate: Double,
    override val blockLabel: String,
    override val description: String,
    val atResistances: List<Complex>,
    val atSwitchesState: List<Boolean>,
    val trackQty: Int,
    val supplyQty: Int,
    override val branchIndex: Int = 0
) : BlockACDto()

/**
 * DTO пост секционирования
 *
 * @param mainSwitchState
 * состояние главного переключателя;
 * @param leftSwitchesState
 * список состояний выключателей на левых фидерах;
 * @param rightSwitchesState
 * список состояний выключателей на правых фидерах;
 *
 * Во всех случаях true означает, что выключатель замкнут.
 */
data class BlockSPDCDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    val mainSwitchState: Boolean,
    val medianSwitchState: Boolean,
    val leftSwitchesState: List<Boolean>,
    val rightSwitchesState: List<Boolean>,
    val leftFeederResistances: List<Double>,
    val rightFeederResistances: List<Double>,
    val leftFeederConnectionPoints: List<Double>,
    val rightFeederConnectionPoints: List<Double>,
    val branchSwitchesState: List<BranchFeederInfo<Double>> = emptyList(),
    override val branchIndex: Int = 0
) : BlockDCDto()

data class BlockPayloadDCDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    val amperage: Double,
    val trackNumber: Int,
    override val branchIndex: Int = 0
) : BlockDCDto()

open class BlockJumperDCDto(
    override val axisCoordinate: Double,
    override val blockLabel: String = "",
    override val description: String = "",
    val track1Number: Int,
    val track2Number: Int,
    val switchState: Boolean,
    override val branchIndex: Int = 0
) : BlockDCDto()

data class BlockBranchAcDto(
    override val axisCoordinate: Double,
    override val blockLabel: String,
    override val description: String,
    override val branchIndex: Int,
    val blocks: List<BlockACDto>,
    val network: List<ACNetworkDto>
) : BlockACDto()

data class BlockBranchDcDto(
    override val axisCoordinate: Double,
    override val blockLabel: String,
    override val description: String,
    val blocks: List<BlockDCDto>,
    val network: List<List<DCNetworkDto>>,
    override val branchIndex: Int
) : BlockDCDto()

data class BlockSplitterAcDto(
    override val axisCoordinate: Double,
    override val blockLabel: String,
    override val description: String,
    val connectedBranchIndex: Int,
    val state: List<SplitterWiringState<Int>>,
    override val branchIndex: Int = 0
) : BlockACDto()

data class BlockSplitterDcDto(
    override val axisCoordinate: Double,
    override val blockLabel: String,
    override val description: String,
    val connectedBranchIndex: Int,
    val state: List<SplitterWiringState<Int>>,
    override val branchIndex: Int = 0
) : BlockDCDto()

data class SplitterWiringState<T>(
    val connection: Connection<T>,
    val switchedOn: Boolean
)

data class BlockShortCircuitPointDcDto(
    override val axisCoordinate: Double,
    override val blockLabel: String,
    override val description: String,
    val resistance: Double,
    val trackNumber: Int,
    override val branchIndex: Int = 0
) : BlockDCDto()