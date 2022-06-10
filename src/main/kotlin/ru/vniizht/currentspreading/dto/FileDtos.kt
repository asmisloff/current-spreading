package ru.vniizht.asuterkortes.dto

import ru.vniizht.currentspreading.dao.enums.TrackType


data class FileTrackDto(
        var fileName: String,
        var fileCountProfile: Int,
        var data: Map<Int, FileDtos>,
        var roadName: String? = null,
        var trainLength: Int? = null
)

data class FileDtos(
        var listStation: List<FileStation> = listOf(),
        var listProfile: List<FileProfile> = listOf(),
        var listSpeedLimit: List<FileSpeedLimit> = listOf(),
        var listPlan: List<FilePlan> = listOf(),
        var trackType: TrackType = TrackType.CONTINUOUS
)

data class FileStation(
        val name: String,
        val coordinate: Double,
        val loopStation: Boolean
)

data class FileProfile(
        val startCoordinate: Double,
        val finishCoordinate: Double,
        val attribute: String,
        val value: Double
)

data class FileSpeedLimit(
        val startCoordinate: Double,
        val finishCoordinate: Double,
        val passenger: Int,
        val freight: Int,
        val suburban: Int,
        val empty: Int
)

data class FilePlan(
        val startCoordinate: Double,
        val finishCoordinate: Double,
        val radius: Double
)

