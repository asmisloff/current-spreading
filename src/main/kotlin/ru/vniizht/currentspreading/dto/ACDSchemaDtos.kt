package ru.vniizht.currentspreading.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.apache.commons.math3.complex.Complex
import ru.vniizht.asuterkortes.counter.circuit.BranchFeederInfo
import ru.vniizht.currentspreading.dao.BranchList
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.core.acnew.*
import ru.vniizht.currentspreading.dao.TransformerType
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.util.check
import ru.vniizht.currentspreading.util.checkNotNull
import ru.vniizht.currentspreading.util.round
import ru.vniizht.currentspreading.util.toDateTime
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.pow

data class ACDSchemaShortDto(
    val id: Long,
    val name: String,
    val description: String,
    val coordinates: String,
    val length: String,
    val trackCount: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AcdSchemaFullDto(
    override var id: Long,
    override val name: String,
    override val description: String,
    override val changeTime: String?,
    override var length: String,
    override var coordinates: String,
    override val trackCount: Int,
    val branchCount: Int = 0,
    override val mainSchema: ACDMainSchemaDto,
    override val branches: BranchList<ACBranchDto> = BranchList.empty(),
    override val type: String = "2x25 кВ"
) : ElectricalSchemaDto()

data class ACDMainSchemaDto(
    override val objects: MutableList<ObjectDto>,
    val network: MutableList<ACNetworkDto>
) : MainSchemaDto()

sealed interface ConvertableToBlockAc {
    fun toBlock(index: Int, trackQty: Int, supplyQty: Int? = null): BlockAC
}

interface AutoTransformerPoint {
    val transformer: TransformerDto?
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ACDStationDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: ACDStationFiders,
    var stationParameters: AcdStationParametersDto?,
    val branchFeeders: List<AcdBranchFeeders> = emptyList(),
    override var branchIndex: Int = 0,
) : ObjectDto(), ConvertableToBlockAc {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockSSAcdDuplex {
        val stp = requireNotNull(stationParameters) { "Не заданы параметры ТП $name ($coordinate км)" }
        require(fiders.trackFiders.size == trackQty) {
            "Кол. путей в параметрах схемы не совпадает с кол. фидеров ТП-$index ($name, $coordinate км)"
        }
        requireNotNull(supplyQty) { "При преобразовании ТП-$index ($name, $coordinate км) в блок мгн. схемы не задано кол. питающих проводов" }

        val skz = stp.skz.checkNotNull { "Для ТП $name ($coordinate км) не задан параметр Sкз" }
        val leftTransformer = requireNotNull(stp.transformerL) {
            "В левом плече ТП  $name ($coordinate км) не задан трансформатор"
        }
        require(stp.transformerP != null || TransformerType.infer(leftTransformer.name) == TransformerType.THREE_PHASE) {
            "В правом плече ТП  $name ($coordinate км) не задан трансформатор"
        }
        val rightTransformer = stp.transformerP ?: leftTransformer
        val leftTransformerQty =
            if (stp.transformerCountL == null || stp.transformerCountL == 0) 1 else stp.transformerCountL!!
        val rightTransformerQty =
            if (stp.transformerCountP == null || stp.transformerCountP == 0) 1 else stp.transformerCountP!!

        val feederParameters = AcFeederParameters.fromAcTrackDtos(fiders.trackFiders, name)
        require(feederParameters.leftSupplyCoords.size == supplyQty) {
            "Кол. питающих проводов в параметрах схемы не совпадает с кол. питающих фидеров ТП-$index ($name, $coordinate км)"
        }

        return BlockSSAcdDuplex(
            axisCoordinate = coordinate,
            suckingCoordinate = coordinate,
            zssLeft = zss(leftTransformer, skz, leftTransformerQty),
            zssRight = zss(rightTransformer, skz, rightTransformerQty),
            xCompXInv = countXupk(stp.upkc),
            xCompXLeft = countXupk(stp.upkKsL),
            xCompXRight = countXupk(stp.upkKsP),
            zTkLeft = zTk(leftTransformer, leftTransformerQty),
            zTkRight = zTk(rightTransformer, rightTransformerQty),
            zAtLeft = zAt(leftTransformer, leftTransformerQty),
            zAtRight = zAt(rightTransformer, rightTransformerQty),
            xCompYLeft = countXku(stp.kuL),
            xCompYRight = countXku(stp.kuP),
            xCompSupplyLeft = countXupk(stp.upkPpL),
            xCompSupplyRight = countXupk(stp.upkPpP),
            vOut = Complex(27500.0, 0.0),
            blockLabel = "ЭЧЭ-$index",
            leftCnFeederCoords = feederParameters.leftCnCoords,
            leftCnFeederResistances = feederParameters.leftCnResistances,
            leftSupplyFeederCoords = feederParameters.leftSupplyCoords,
            leftSupplyFeederResistances = feederParameters.leftSupplyResistances,
            rightCnFeederCoords = feederParameters.rightCnCoords,
            rightCnFeederResistances = feederParameters.rightCnResistances,
            rightSupplyFeederCoords = feederParameters.rightSupplyCoords,
            rightSupplyFeederResistances = feederParameters.rightSupplyResistances,
            leftShoulderPhaseOrder = PhaseOrder.leftShoulderOrderFromStringRepr(
                stp.phase.checkNotNull { "Некорректно задан порядок фаз в плечах ТП $name" }
            ),
            branchIndex = branchIndex,
            branchFeederInfoList = branchFeeders.toFeederInfoList("ЭЧЭ-$index ($name)")
        ).also { it.description = name }
    }

    private fun zAt(transformer: TransformerDto, transformerQty: Int): Complex {
        return when (val tType = TransformerType.infer(transformer.name)) {
            TransformerType.THREE_PHASE -> INF
            TransformerType.BRANCH -> zTrans(transformer, transformerQty, 2.0)
            TransformerType.AUTO -> zTrans(transformer, transformerQty, 4.0)
            else -> throw IllegalStateException("Неверный тип трансформатора: ${tType.strRepr}")
        }
    }

    private fun zTk(transformer: TransformerDto, transformerQty: Int): Complex {
        return when (val tType = TransformerType.infer(transformer.name)) {
            TransformerType.THREE_PHASE -> ZERO
            TransformerType.BRANCH -> zTrans(transformer, transformerQty, 2.0)
            TransformerType.AUTO -> ZERO
            else -> throw IllegalStateException("Неверный тип трансформатора: ${tType.strRepr}")
        }
    }

    private fun zss(transformer: TransformerDto, skz /*[MВА]*/: Double, transformerQty: Int): Complex {
        return when (TransformerType.infer(transformer.name)) {
            TransformerType.THREE_PHASE -> zExternalNetwork(skz, 2.0) + zTrans(transformer, transformerQty, 2.0)
            TransformerType.BRANCH -> zExternalNetwork(skz, 2.0)
            else -> throw IllegalArgumentException("Параметр Zтп не имеет смысла для автотрансформатора")
        }
    }

}

data class AcdBranchFeeders(
    val cnFeeders: List<ACFiderDto>,
    val supplyFeeders: List<ACFiderDto>,
    val branchIndex: Int
) {
    init {
        cnFeeders.forEach { it.branchIndex = branchIndex }
        supplyFeeders.forEach { it.branchIndex = branchIndex }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ACDSectionWithAtpDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: ACDSectionFiders,
    override val transformer: TransformerDto?,
    val branchFeeders: List<AcdBranchFeeders> = emptyList(),
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockAc, AutoTransformerPoint {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockSpaAcd {
        requireNotNull(transformer) { "На ПСА \"$name\" не задан автотрансформатор" }
        require(fiders.trackFiders.size == trackQty) {
            "Кол. путей в параметрах схемы не совпадает с кол. фидеров ПСА-$index ($name, $coordinate км)"
        }
        requireNotNull(supplyQty) { "При преобразовании ПСА-$index ($name, $coordinate км) в блок мгн. схемы не задано кол. питающих проводов" }
        TransformerType
            .infer(transformer.name)
            .check("Трансформатор, установленный на ПСА ($name), должен быть автотрансформатором") {
                it == TransformerType.AUTO
            }

        val feederParameters = AcFeederParameters.fromAcTrackDtos(fiders.trackFiders, name)
        require(feederParameters.leftSupplyCoords.size == supplyQty) {
            "Кол. питающих проводов в параметрах схемы не совпадает с кол. питающих фидеров ПСА-$index ($name, $coordinate км)"
        }
        val zAt = zTrans(transformer, 1, 4.0)

        return BlockSpaAcd(
            blockLabel = "ПСА-$index",
            axisCoordinate = coordinate,
            leftCnFeedersCoords = feederParameters.leftCnCoords,
            leftCnFeederResistances = feederParameters.leftCnResistances,
            rightCnFeedersCoords = feederParameters.rightCnCoords,
            rightCnFeederResistances = feederParameters.rightCnResistances,
            leftSupplyFeederCoords = feederParameters.leftSupplyCoords,
            leftSupplyFeederResistances = feederParameters.leftSupplyResistances,
            rightSupplyFeederCoords = feederParameters.rightSupplyCoords,
            rightSupplyFeederResistances = feederParameters.rightSupplyResistances,
            leftAtResistance = zAt,
            rightAtResistance = zAt,
            branchIndex = branchIndex,
            branchFeederInfoList = branchFeeders.toFeederInfoList("ПСА-$index ($name)")
        ).also { it.description = name }
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ACDSectionDto(
    override val coordinate: Double,
    override val name: String,
    override val fiders: ACDSectionFiders,
    val branchFeeders: List<AcdBranchFeeders> = emptyList(),
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockAc {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockAC {
        require(fiders.trackFiders.size == trackQty) {
            "Кол. путей в параметрах схемы не совпадает с кол. фидеров ПСА-$index ($name, $coordinate км)"
        }
        val feederParameters = AcFeederParameters.fromAcTrackDtos(fiders.trackFiders, name)

        return BlockSpAcd(
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
            supplyQty = trackQty,
            branchIndex = branchIndex,
            branchFeederInfoList = branchFeeders.toFeederInfoList("ПС-$index ($name)")
        ).also { it.description = name }
    }

}

data class AcdAtpDto(
    override val coordinate: Double,
    override val name: String,
    override val transformer: TransformerDto?,
    override var branchIndex: Int = 0
) : ObjectDto(), ConvertableToBlockAc, AutoTransformerPoint {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockAtpAcd {
        requireNotNull(transformer) { "На АТП \"$name\" не задан автотрансформатор" }
        requireNotNull(supplyQty) {
            "При преобразовании ATП-$index ($name, $coordinate км) в блок мгн. схемы не задано кол. питающих проводов"
        }
        TransformerType
            .infer(transformer.name)
            .check("Трансформатор, установленный на АТП ($name), должен быть автотрансформатором") {
                it == TransformerType.AUTO
            }
        return BlockAtpAcd(
            blockLabel = "АТП-$index",
            axisCoordinate = coordinate,
            atResistances = List(supplyQty) { zTrans(transformer, 1, 4.0) },
            trackQty = trackQty,
            branchIndex = branchIndex
        ).also { it.description = name }
    }

    override val fiders = null

}

data class AcdBranchPointDto(
    override val coordinate: Double,
    override val name: String,
    val connectedBranchIndex: Int,
    val wiringLayout: List<Connection<String>>,
    override var branchIndex: Int = 0,
    override val fiders: FidersDto? = null // не используется
) : ObjectDto(), ConvertableToBlockAc {

    override fun toBlock(index: Int, trackQty: Int, supplyQty: Int?): BlockAC {
        return BlockSplitterAc(
            axisCoordinate = coordinate,
            blockLabel = "ОТВ$index",
            connectedBranchIndex = connectedBranchIndex,
            wiringLayout = wiringLayout.parseTrackLabels(),
            branchIndex = branchIndex
        )
    }

    private fun List<Connection<String>>.parseTrackLabels(): List<Connection<Int>> {
        return this.map { conn ->
            val track1 = conn.firstTrackNumber
            val track2 = conn.secondTrackNumber
            val track1Number = when {
                track1.isCnLabel() -> track1[1] - '0'
                track1.isSpLabel() -> (track1[1] - '0') + 1000
                else -> throw IllegalStateException("Неверный формат метки пути: $track1")
            }
            val track2Number = when {
                track2.isCnLabel() -> track2[1] - '0'
                track2.isSpLabel() -> (track2[1] - '0') + 1000
                else -> throw IllegalStateException("Неверный формат метки пути: $track2")
            }
            check(abs(track1Number - track2Number) < 6) {
                "Неверная карта соединений в объекте ОТВ ($name): {$track1 -> $track2}. Нельзя соединить КС с ПП."
            }

            Connection(track1Number, track2Number, conn.firstConnectionPoint, conn.secondConnectionPoint)
        }
    }

    private fun String.isCnLabel() =
        length == 2 && this[0] == 'К' && this[1].isDigit() && (this[1] - '0' in 1..6)

    private fun String.isSpLabel() =
        length == 2 && this[0] == 'П' && this[1].isDigit() && (this[1] - '0' in 1..6)

}

data class AcdStationParametersDto(
    var skz: Double?,
    var phase: String?,
    var transformerL: TransformerDto?,
    var transformerP: TransformerDto?,
    var transformerCountL: Int?,
    var transformerCountP: Int?,
    var uhhL: Int?,
    var uhhP: Int?,
    var kuL: UkCompensationDeviceDto?,
    var kuP: UkCompensationDeviceDto?,
    var upkKsL: UpkCompensationDeviceDto?,
    var upkKsP: UpkCompensationDeviceDto?,
    var upkPpL: UpkCompensationDeviceDto?,
    var upkPpP: UpkCompensationDeviceDto?,
    var upkc: UpkCompensationDeviceDto?,
    var sp: Double?,
    var sn: Double?,
    var energoSystem: String?
) : StationParametersDto()

data class ACDStationFiders(
    override val sucking: ACFiderDto,
    override val trackFiders: List<ACDTrackFiderDto>
) : StationFiders()

data class ACDSectionFiders(
    override val trackFiders: List<ACDTrackFiderDto>
) : SectionFiders()

class ACDTrackFiderDto(
    leftFider: ACFiderDto,
    val leftSupplyFider: ACFiderDto,
    rightFider: ACFiderDto,
    val rightSupplyFider: ACFiderDto
) : ACTrackFiderDto(leftFider, rightFider)

fun AcdSchemaFullDto.toAcdEntity() = ElectricalSchema(
    id = id,
    name = name,
    changeTime = LocalDateTime.now(),
    type = SchemaType.ACD,
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
fun ElectricalSchema.toAcdDto() = AcdSchemaFullDto(
    id = id!!,
    branches = branches as BranchList<ACBranchDto>,
    description = description,
    changeTime = changeTime.toDateTime(),
    mainSchema = mainSchema as ACDMainSchemaDto,
    branchCount = branchCount,
    coordinates = coordinates,
    length = length,
    name = name,
    trackCount = trackCount
)

internal fun List<AcdBranchFeeders>.toFeederInfoList(hostBlockLabel: String): List<BranchFeederInfo<Complex>> {
    val result = mutableListOf<BranchFeederInfo<Complex>>()
    for (bfs in this) {
        bfs.cnFeeders.forEachIndexed { trackIndex, f ->
            result.add(
                BranchFeederInfo(
                    branchIndex = bfs.branchIndex,
                    trackNumber = trackIndex + 1,
                    connectionPoint = f.coordinate,
                    feederResistance = AcFeederParameters.getFeederResistance(f, hostBlockLabel)
                )
            )
        }
        bfs.supplyFeeders.forEachIndexed { trackIndex, f ->
            result.add(
                BranchFeederInfo(
                    branchIndex = bfs.branchIndex,
                    trackNumber = (trackIndex + 1) + 1000,
                    connectionPoint = f.coordinate,
                    feederResistance = AcFeederParameters.getFeederResistance(f, hostBlockLabel)
                )
            )
        }
    }
    return result
}

fun countXupk(upk: UpkCompensationDeviceDto?): Complex {
    return if (upk != null) {
        Complex(0.0, -1000000 * (upk.nominalPower / 1000) / upk.nominalAmperage.pow(2))
    } else {
        return ZERO
    }
}

fun countXupkAcd(upk: UpkCompensationDeviceDto?): Complex {
    return if (upk != null) {
        Complex(0.0, -1000000 * (upk.nominalPower / 1000) / upk.nominalAmperage.pow(2))
    } else {
        return ZERO
    }
}

fun countXku(ku: UkCompensationDeviceDto?): Complex {
    return if (ku != null) {
        Complex(0.0, -(1e3 * 27.5.pow(2) / ku.usefulPower))
    } else INF
}