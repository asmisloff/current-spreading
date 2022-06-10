package ru.vniizht.asuterkortes.dto


import ru.vniizht.currentspreading.dao.AlternateNetwork
import ru.vniizht.currentspreading.dto.NetworkShortDto
import ru.vniizht.currentspreading.dto.WireAndRailDto
import ru.vniizht.currentspreading.util.round

data class AlternateNetworkShortDto(
        override val id: Long,
        override val name: String,
        val wiresActiveResistance: String,
        val wiresInductiveResistance: String,
        val limitAmperage: String,
        val parameters: List<AlternateParameters>
) : NetworkShortDto()

data class AlternateNetworkFullDto(
        val id: Long,
        val description: String = "",
        val trackCount: Int,
        val earthResistance: Double,
        val wireAndRailList: List<AlternateWireAndRail>,
        val parameters: List<AlternateParameters>
)

data class AlternateWireAndRail(
    val type: String,
    val trackNumber: Int,
    val brand: WireAndRailDto,
    val heightPosition: Double,
    val horizontalPosition: Double
)

data class AlternateParameters(
    val trackName: String,
    val wiresName: String,
    val activeResistance: Double,
    val inductiveResistance: Double,
    val limitAmperage: Int?,
    val limitTemperature: Int?,
    val thermalConstant: Double?,
    val currentFraction: List<Pair<Double, Double>>,
    val limitWire: WireAndRailDto?
)

fun AlternateNetwork.toShortDto() = AlternateNetworkShortDto(
    id = id!!,
    name = ("$trackCount-$id $description" +
        "\n${parameters.filter { isAcWire(it) }.map { it.wiresName + "\n" }}")
        .convertStringListToStrings() +
        "\n${parameters.filter { isAcdContactWire(it) }.map { it.trackName + " " + it.wiresName + "\n" }}"
            .convertStringListToStrings() +
        "\n${parameters.filter { isAcdSupplyWire(it) }.map { it.trackName + " " + it.wiresName + "\n" }}"
            .convertStringListToStrings(),
    limitAmperage = "\n${parameters.filter { isAcWire(it) }.map { "${it.limitAmperage}\n" }}"
        .convertStringListToStrings() +
        "\n${parameters.filter { isAcdContactWire(it) }.map { "${it.limitAmperage}\n" }}"
            .convertStringListToStrings() +
        "\n${parameters.filter { isAcdSupplyWire(it) }.map { "${it.limitAmperage}\n" }}"
            .convertStringListToStrings(),
    wiresInductiveResistance = "\n${parameters.filter { isAcWire(it) }.map { "${it.inductiveResistance.round(4)}\n" }}"
        .convertStringListToStrings() +
        "\n${parameters.filter { isAcdContactWire(it) }.map { "${it.inductiveResistance.round(4)}\n" }}"
            .convertStringListToStrings() +
        "\n${parameters.filter { isAcdSupplyWire(it) }.map { "${it.inductiveResistance.round(4)}\n" }}"
            .convertStringListToStrings(),
    wiresActiveResistance = "\n${parameters.filter { isAcWire(it) }.map { "${it.activeResistance.round(4)}\n" }}"
        .convertStringListToStrings() +
        "\n${parameters.filter { isAcdContactWire(it) }.map { "${it.activeResistance.round(4)}\n" }}"
            .convertStringListToStrings() +
        "\n${parameters.filter { isAcdSupplyWire(it) }.map { "${it.activeResistance.round(4)}\n" }}"
            .convertStringListToStrings(),
    parameters = parameters
)

private fun String.convertStringListToStrings() =
    this.replace("[", "").replace("\n]", "").replace(", ", "")

private fun isAcdSupplyWire(it: AlternateParameters) = it.trackName.matches(Regex("П\\d"))

private fun isAcdContactWire(it: AlternateParameters) = it.trackName.matches(Regex("К\\d"))

private fun isAcWire(it: AlternateParameters) = it.trackName.contains("-й")

fun AlternateNetwork.toFullDto() = AlternateNetworkFullDto(
    id = id!!,
    description = description,
    earthResistance = earthResistance,
    trackCount = trackCount,
    parameters = parameters,
    wireAndRailList = wireAndRailList.map {
        AlternateWireAndRail(
            type = it.type.russianName,
            trackNumber = it.trackNumber,
            brand = it.wire!!.toFullDto(),
            heightPosition = it.heightPosition,
            horizontalPosition = it.horizontalPosition
        )
    }
)

fun getNullAlternateNetwork() = AlternateNetworkShortDto(
    id = -1,
    limitAmperage = "",
    name = "Нулевой",
    wiresActiveResistance = "",
    wiresInductiveResistance = "",
    parameters = listOf(
        AlternateParameters(
            trackName = "1-й",
            wiresName = "нулевой",
            activeResistance = 0.0000000001,
            inductiveResistance = 0.0000000001,
            limitAmperage = 9999999,
            limitTemperature = 999,
            thermalConstant = 10.0,
            currentFraction = listOf(),
            limitWire = null
        )
    )
)

