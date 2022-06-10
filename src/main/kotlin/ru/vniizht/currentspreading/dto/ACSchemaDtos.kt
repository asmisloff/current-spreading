package ru.vniizht.currentspreading.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.apache.commons.math3.complex.Complex
import ru.vniizht.asuterkortes.counter.acnew.*
import ru.vniizht.asuterkortes.counter.circuit.BranchFeederInfo
import ru.vniizht.currentspreading.dao.BranchList
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.util.round
import ru.vniizht.currentspreading.util.toDateTime
import ru.vniizht.currentspreading.core.acnew.*
import ru.vniizht.currentspreading.core.schedule.toOrderedList
import ru.vniizht.currentspreading.util.checkNotNull
import java.time.LocalDateTime

data class ACSchemaShortDto(
    val id: Long,
    val name: String,
    val description: String,
    val coordinates: String,
    val length: String,
    val trackCount: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ACSchemaFullDto(
    override var id: Long,
    override val name: String,
    override val description: String,
    override val changeTime: String?,
    override var length: String,
    override var coordinates: String,
    override val trackCount: Int,
    val branchCount: Int = 0,
    override val mainSchema: ACMainSchemaDto,
    override val branches: BranchList<ACBranchDto> = BranchList.empty(),
    override val type: String = "25 кВ"
) : ElectricalSchemaDto()

data class ACBranchDto(
    override val trackQty: Int,
    override var coordinate: Double,
    override val name: String,
    val objects: List<ObjectDto>,
    override var branchIndex: Int,
    val network: List<ACNetworkDto>,
    override val fiders: FidersDto? = null // не используется
) : BranchDto(trackQty, coordinate, name, branchIndex), ConvertableToBlockAc {

    override fun checkAndAmendIndices() {
        for (o in objects.sortedBy { it.coordinate }) {
            o.branchIndex = this.branchIndex
            //todo: убрать дублирование
            if (o is ACStationDto) {
                o.fiders.sucking.branchIndex = this.branchIndex
                for (f in o.fiders.trackFiders) {
                    f.leftFider.branchIndex = this.branchIndex
                    f.rightFider.branchIndex = this.branchIndex
                }
            }
            if (o is ACSectionDto) {
                for (f in o.fiders.trackFiders) {
                    f.leftFider.branchIndex = this.branchIndex
                    f.rightFider.branchIndex = this.branchIndex
                }
            }
            if (o is ACDSectionDto) {
                for (f in o.fiders.trackFiders) {
                    f.leftFider.branchIndex = this.branchIndex
                    f.rightFider.branchIndex = this.branchIndex
                }
            }
            if (o is ACDSectionWithAtpDto) {
                for (f in o.fiders.trackFiders) {
                    f.leftFider.branchIndex = this.branchIndex
                    f.rightFider.branchIndex = this.branchIndex
                }
            }
        }
        for (n in network) {
            n.branchIndex = this.branchIndex
        }
    }

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockBranchAc {
        val f = BlockACFactory()
        return BlockBranchAc(
            axisCoordinate = coordinate,
            branchIndex = branchIndex,
            blocks = objects.mapIndexed { i, obj ->
                check(obj is ConvertableToBlockAc) { "Для объекта ${obj.name} не определено преобразование в блок мгновенной схемы" }
                f.fromDto(obj as ConvertableToBlockAc, trackQty, supplyQty)
            }.toOrderedList(Comparator.comparingDouble { it.axisCoordinate }),
            network = network,
            blockLabel = "Ветвь-$index",
        ).also { it.description = name }
    }

}

data class ACMainSchemaDto(
    override val objects: MutableList<ObjectDto>,
    val network: MutableList<ACNetworkDto>
) : MainSchemaDto()

data class ACNetworkDto(
    override val endSection: Double,
    override val network: AlternateNetworkShortDto,
    override var branchIndex: Int = 0
) : NetworkDto()

data class ACStationDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: ACStationFiders,
    var stationParameters: AcStationParametersDto?,
    val branchFiders: List<ACFiderDto> = emptyList(),
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockAc {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockAC {
        require(fiders.trackFiders.size == trackQty) { "Кол. путей в параметрах схемы не совпадает с кол. фидеров ТП-$index ($name, $coordinate км)" }
        val sp = requireNotNull(stationParameters) { "Не заданы параметры ТП-$index ($name, $coordinate км)" }
        val skz = sp.skz.checkNotNull { "Для ТП-$index ($name, $coordinate км) не задан параметр Sкз" }
        val transformer = sp.transformer.checkNotNull { "Для ТП-$index ($name, $coordinate км) не задан трансформатор" }
        val transformerQty = sp.transformerCount ?: 1

        val fp = AcFeederParameters.fromAcTrackDtos(fiders.trackFiders, name)

        return BlockSSAcDuplex(
            axisCoordinate = coordinate,
            suckingCoordinate = coordinate,
            zSS = zExternalNetwork(skz, 2.0) + zTrans(transformer, transformerQty, 2.0),
            xCompXInv = countXupk(sp.upkc),
            xCompXLeft = countXupk(sp.upkl),
            xCompXRight = countXupk(sp.upkp),
            xCompYLeft = countXku(sp.kul),
            xCompYRight = countXku(sp.kup),
            vOut = Complex(27500.0, 1e-9),
            blockLabel = "ЭЧЭ-$index",
            leftShoulderPhaseOrder = PhaseOrder.leftShoulderOrderFromStringRepr(
                sp.phase.checkNotNull { "Некорректно задан порядок фаз в плечах ТП-$index ($name, $coordinate км)" }
            ),
            feederCoordinates = fp.leftCnCoords + fp.rightCnCoords,
            feederResistances = fp.leftCnResistances + fp.rightCnResistances,
            branchIndex = branchIndex,
            branchFeederInfoList = branchFiders.toBranchFeederInfoList("ЭЧЭ-$index")
        ).also { it.description = name }
    }
}

data class ACSectionDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: ACSectionFiders,
    val branchFiders: List<ACFiderDto> = emptyList(),
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockAc {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockAC {
        require(fiders.trackFiders.size == trackQty) { "Кол. путей в параметрах схемы не совпадает с кол. фидеров ПС-$index ($name, $coordinate км)" }
        val feederParameters = AcFeederParameters.fromAcTrackDtos(fiders.trackFiders, name)
        return when (supplyQty) {
            null -> BlockSpAc(
                axisCoordinate = coordinate,
                coordinates = feederParameters.leftCnCoords + feederParameters.rightCnCoords,
                zB = feederParameters.leftCnResistances + feederParameters.rightCnResistances,
                blockLabel = "ПС-$index",
                branchIndex = branchIndex,
                branchFeederInfoList = branchFiders.toBranchFeederInfoList("ПС-$index")
            ).also { it.description = name }

            else -> BlockSpAcd(
                blockLabel = "ПС-$index",
                axisCoordinate = coordinate,
                leftCnFeedersCoords = feederParameters.leftCnCoords,
                leftCnFeederResistances = feederParameters.leftCnResistances,
                rightCnFeedersCoords = feederParameters.rightCnCoords,
                rightCnFeederResistances = feederParameters.rightCnResistances,
                leftSpFeedersCoords = feederParameters.leftSupplyCoords,
                leftSpFeederResistances = feederParameters.leftSupplyResistances,
                rightSpFeedersCoords = feederParameters.rightSupplyCoords,
                rightSpFeederResistances = feederParameters.rightSupplyResistances,
                supplyQty = supplyQty
            ).also { it.description = name }
        }
    }

}

data class AcStationParametersDto(
    var skz: Double?,
    var phase: String?,
    var transformer: TransformerDto?,
    var transformerCount: Int?,
    var kul: UkCompensationDeviceDto?,
    var kup: UkCompensationDeviceDto?,
    var upkl: UpkCompensationDeviceDto?,
    var upkp: UpkCompensationDeviceDto?,
    var upkc: UpkCompensationDeviceDto?,
    var sp: Double?,
    var sn: Double?,
    var energoSystem: String?
) : StationParametersDto()

data class ACStationFiders(
    override val sucking: ACFiderDto,
    override val trackFiders: List<ACTrackFiderDto>
) : StationFiders()

data class ACSectionFiders(
    override val trackFiders: List<ACTrackFiderDto>
) : SectionFiders()

open class ACTrackFiderDto(
    override val leftFider: ACFiderDto,
    override val rightFider: ACFiderDto
) : TrackFiderDto()

data class ACFiderDto(
    override val length: Double,
    override var coordinate: Double = 0.0,
    var type: AlternateNetworkShortDto?,
    override var branchIndex: Int = 0
) : FiderDto()

fun ACSchemaFullDto.toAcEntity() = ElectricalSchema(
    id = id,
    name = name,
    changeTime = LocalDateTime.now(),
    type = SchemaType.AC,
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

fun ElectricalSchema.toAcDto() = ACSchemaFullDto(
    id = id!!,
    branches = branches as BranchList<ACBranchDto>,
    description = description,
    changeTime = changeTime.toDateTime(),
    mainSchema = mainSchema as ACMainSchemaDto,
    branchCount = branchCount,
    coordinates = coordinates,
    length = length,
    name = name,
    trackCount = trackCount
)

fun <T : NetworkDto> List<T>.byCoordinate(coord: Double): T? {
    for (i in lastIndex downTo 0) {
        if (this[i].endSection >= coord) return this[i]
    }
    return null
}

private fun List<ACFiderDto>.toBranchFeederInfoList(hostBlockLabel: String): List<BranchFeederInfo<Complex>> {
    var trackCnt = 0
    var currentBranchIndex = 0
    return this.map { f ->
        if (f.branchIndex != currentBranchIndex) {
            currentBranchIndex = f.branchIndex
            trackCnt = 0
        }
        BranchFeederInfo(
            branchIndex = f.branchIndex,
            trackNumber = ++trackCnt,
            feederResistance = AcFeederParameters.getFeederResistance(f, hostBlockLabel),
            connectionPoint = f.coordinate
        )
    }
}