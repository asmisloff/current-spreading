package ru.vniizht.currentspreading.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import ru.vniizht.asuterkortes.counter.dcnew.*
import ru.vniizht.currentspreading.dao.BranchList
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.dcnew.*
import ru.vniizht.currentspreading.core.schedule.toOrderedList
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.util.eq
import ru.vniizht.currentspreading.util.round
import ru.vniizht.currentspreading.util.toDateTime
import java.time.LocalDateTime.now

data class DCSchemaShortDto(
    val id: Long,
    val name: String,
    val description: String,
    val coordinates: String,
    val length: String,
    val trackCount: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DCSchemaFullDto(
    override var id: Long,
    override val name: String,
    override val description: String,
    override val changeTime: String?,
    override var length: String,
    override var coordinates: String,
    override val trackCount: Int,
    val branchCount: Int = 0,
    override val mainSchema: DCMainSchemaDto,
    override val branches: BranchList<DCBranchDto> = BranchList.empty(),
    override val type: String = "3 кВ",
) : ElectricalSchemaDto()

data class DCBranchDto(
    override val trackQty: Int,
    override var coordinate: Double,
    override val name: String,
    override var branchIndex: Int,
    val objects: List<ObjectDto>,
    val network: List<MutableList<DCNetworkDto>>,
    override val fiders: FidersDto? = null // не используется
) : BranchDto(trackQty, coordinate, name, branchIndex), ConvertableToBlockDc {

    override fun checkAndAmendIndices() {
        for (o in objects) {
            o.branchIndex = this.branchIndex
            when (o) {
                //todo: убрать дублирование
                is DCStationDto -> {
                    o.fiders.sucking.branchIndex = this.branchIndex
                    for (f in o.fiders.trackFiders) {
                        f.leftFider.branchIndex = this.branchIndex
                        f.rightFider.branchIndex = this.branchIndex
                    }
                }
                is DCSectionDto -> {
                    for (f in o.fiders.trackFiders) {
                        f.leftFider.branchIndex = this.branchIndex
                        f.rightFider.branchIndex = this.branchIndex
                    }
                }
            }
        }
        for (list in network) {
            for (n in list) {
                n.branchIndex = this.branchIndex
            }
        }
    }

    override fun toBlock(index: Int, trackQty: Int): BlockDC {
        val f = BlockDCFactory()
        return BlockBranchDc(
            axisCoordinate = coordinate,
            branchIndex = branchIndex,
            blocks = objects.mapIndexed { i, obj ->
                check(obj is ConvertableToBlockDc) { "Для объекта ${obj.name} не определено преобразование в блок мгновенной схемы" }
                f.fromDto(obj as ConvertableToBlockDc, this.trackQty)
            }.toOrderedList(Comparator.comparingDouble { it.axisCoordinate }),
            network = network
        )
    }

}

data class DCMainSchemaDto(
    override val objects: MutableList<ObjectDto>,
    val network: List<MutableList<DCNetworkDto>>,
    val presavedTrsf: List<TransformerDto>? = null,
    val presavedNtwk: List<DirectNetworkShortDto>? = null
) : MainSchemaDto()

data class DCNetworkDto(
    override val endSection: Double,
    override val network: DirectNetworkShortDto,
    override var branchIndex: Int = 0
) : NetworkDto()

sealed interface ConvertableToBlockDc {
    fun toBlock(index: Int, trackQty: Int): BlockDC
}

data class DCStationDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: DCStationFiders,
    var stationParameters: DcStationParametersDto?,
    val branchFiders: List<DCFiderDto> = emptyList(),
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockDc {

    override fun toBlock(index: Int, trackQty: Int): BlockDC {
        val ssCardinalParameters = ssCardinalParameters(this, ICSolutionAttempt.FIRST)
        val ss = BlockSSDC(
            axisCoordinate = coordinate,
            suckingCoordinate = fiders.sucking.coordinate,
            zSS = ssCardinalParameters.zSS,
            xCompXInv = (fiders.sucking.type?.wiresResistance ?: 0.0) * fiders.sucking.length,
            vOut = ssCardinalParameters.emf,
            blockLabel = "SS${index}",
            branchIndex = branchIndex
        )
        ss.description = name

        val leftCoordinates = DoubleArray(fiders.trackFiders.size)
        val rightCoordinates = DoubleArray(fiders.trackFiders.size)
        val leftZF = DoubleArray(fiders.trackFiders.size)
        val rightZF = DoubleArray(fiders.trackFiders.size)
        fiders.trackFiders.forEachIndexed { i, trackFiderDto ->
            val left = trackFiderDto.leftFider
            val right = trackFiderDto.rightFider
            val leftFeederLength = if (left.length eq 0.0) ZERO else left.length
            val leftResistance = leftFeederLength * (left.type?.wiresResistance ?: ZERO)
            val rightFeederLength = if (right.length eq 0.0) ZERO else right.length
            val rightResistance = rightFeederLength * (right.type?.wiresResistance ?: ZERO)
            leftCoordinates[i] = left.coordinate
            rightCoordinates[i] = right.coordinate
            leftZF[i] = leftResistance
            rightZF[i] = rightResistance
        }
        ss.addLeftFeeder(leftCoordinates, leftZF)
        ss.addRightFeeder(rightCoordinates, rightZF)
        if (branchFiders.isNotEmpty()) {
            ss.addBranchFeeders(branchFiders.toBranchFeederInfoList(ss.blockLabel))
        }

        return ss
    }

}

data class DCSectionDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: DCSectionFiders,
    val branchFiders: List<DCFiderDto> = emptyList(),
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockDc {

    override fun toBlock(index: Int, trackQty: Int): BlockDC {
        require(trackQty == fiders.trackFiders.size) {
            "Разное количество путей в определении ПС $name и в свойствах схемы"
        }
        val spFeeders = fiders.trackFiders
        val coordinates = DoubleArray(trackQty * 2)
        val zB = DoubleArray(trackQty * 2)
        spFeeders.forEachIndexed { i, trackFiderDto ->
            val left = trackFiderDto.leftFider
            val right = trackFiderDto.rightFider
            coordinates[i] = left.coordinate
            coordinates[i + trackQty] = right.coordinate
            val leftFeederLength = if (left.length eq 0.0) ZERO else left.length
            zB[i] = leftFeederLength * (left.type?.wiresResistance ?: ZERO)
            val rightFeederLength = if (right.length eq 0.0) ZERO else right.length
            zB[i + trackQty] = rightFeederLength * (right.type?.wiresResistance ?: ZERO)
        }
        val sp = BlockSPDC(
            axisCoordinate = coordinate,
            coordinates = coordinates,
            zB = zB,
            blockLabel = "SP$index",
            branchIndex = branchIndex
        )
        sp.description = name

        if (branchFiders.isNotEmpty()) {
            sp.addBranchFeeders(branchFiders.toBranchFeederInfoList(sp.blockLabel))
        }

        return sp
    }

}

data class DcStationParametersDto(
    var skz: Int?, //need check
    var spt: TransformerDto?,
    var sptCount: Int?, //need check
    var sptReserve: TransformerDto?,
    var sptReserveCount: Int?,
    var stt: TransformerDto?, //need check
    var sttCount: Int?, //need check
    var sttReserve: TransformerDto?,
    var sttReserveCount: Int?,
    var directUhh: Int?, //need check
    var directRo: Double?, //need check
    var directAmperage: Int?, //need check
    var invertUhh: Int?,
    var invertRo: Double?,
    var invertOnVoltage: Int?,
    var invertAmperage: Int?,
    var vduVoltage: Int?,
    var vduUhh: Int?,
    var srn: Double?, //need check
    var snn: Double?,
    var energoSystem: String?
    //TODO добавить ВДУ и внешнюю сеть
) : StationParametersDto()

data class DCStationFiders(
    override val sucking: DCFiderDto,
    override val trackFiders: List<DCTrackFiderDto>
) : StationFiders()

data class DCSectionFiders(
    override val trackFiders: List<DCTrackFiderDto>
) : SectionFiders()

data class DCTrackFiderDto(
    override val leftFider: DCFiderDto,
    override val rightFider: DCFiderDto
) : TrackFiderDto()

data class DCFiderDto(
    override val length: Double,
    override var coordinate: Double = 0.0,
    var type: DirectNetworkShortDto?,
    override var branchIndex: Int = 0
) : FiderDto()

fun DCSchemaFullDto.toDcEntity() = ElectricalSchema(
    id = id,
    name = name,
    changeTime = now(),
    type = SchemaType.DC,
    length = mainSchema.objects.sortedBy { it.coordinate }.let {
        (it.last().coordinate - it.first().coordinate).round(1).toString()
    },
    coordinates = mainSchema.objects.sortedBy { it.coordinate }.let {
        "${it.first().coordinate.round(1)} - ${it.last().coordinate.round(1)}"
    },
    branchCount = branchCount,
    mainSchema = mainSchema,
    description = description,
    active = true,
    branches = BranchList(branches.toMutableList()),
    trackCount = trackCount
)

@Suppress("UNCHECKED_CAST")
fun ElectricalSchema.toDcDto() = DCSchemaFullDto(
    id = id!!,
    branches = branches as BranchList<DCBranchDto>,
    description = description,
    changeTime = changeTime.toDateTime(),
    mainSchema = mainSchema as DCMainSchemaDto,
    branchCount = branchCount,
    coordinates = coordinates,
    length = length,
    name = name,
    trackCount = trackCount
)
