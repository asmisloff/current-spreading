package ru.vniizht.currentspreading.dto

import ru.vniizht.asuterkortes.counter.tractive.ProfileElement
import ru.vniizht.currentspreading.dao.*
import ru.vniizht.currentspreading.util.round
import ru.vniizht.currentspreading.util.toDateTime

data class TrackDto(
    val id: Long,
    val changeTime: String,
    val road: String,
    val name: String,
    val description: String,
    val coordinates: String,
    val length: Double,
    val firstStation: String,
    val numberOfTracks: Int,
    val trackTypes: List<String>,
    val stations: List<List<StationDto>>,
    val categories: List<TrackCategoryDto>,
    val speedLimits: List<List<SpeedLimitsDto>>,
    val profile: List<List<ProfileElement>>,
    val version: DataVersion,
    val oddDirectionFromFirstStation: Boolean
)

data class TrackShortDto(
    val id: Long,
    val changeTime: String,
    val road: String,
    val name: String,
    val description: String,
    val coordinates: String,
    val length: Double,
    val firstStation: String,
    val numberOfTracks: Int,
)

data class StationDto(
    val id: Long,
    val name: String,
    val coordinate: Double,
    val loopStation: Boolean
)

data class SpeedLimitsDto(
    val startCoordinate: Double,
    val endCoordinate: Double,
    val limits: List<Int>
)

data class TrackCategoryDto(
    val id: Long,
    val name: String,
    val priority: Int
)

fun Track.spatialBounds(): Pair<Double, Double> {
    val activeSingleTracks = this.singleTracks
    val firstSingleTrack = activeSingleTracks[0].stations.sortedBy { it.coordinate }
    var minCoord = firstSingleTrack.first().coordinate
    var maxCoord = firstSingleTrack.last().coordinate

    for (i in 1 until activeSingleTracks.size) {
        val thisSingleTrackStations = activeSingleTracks[i].stations.sortedBy { it.coordinate }
        val thisTrackMinCoord = thisSingleTrackStations.first().coordinate
        val thisTrackMaxCoord = thisSingleTrackStations.last().coordinate
        if (thisTrackMinCoord < minCoord) {
            minCoord = thisTrackMinCoord
        }
        if (thisTrackMaxCoord > maxCoord) {
            maxCoord = thisTrackMaxCoord
        }
    }
    return Pair(minCoord, maxCoord)
}

fun Track.toShortDto(): TrackShortDto {
    val activeSingleTracks = this.singleTracks
    val singleTrackStations = activeSingleTracks[0].stations.sortedBy { it.coordinate }
    val (xMin, xMax) = this.spatialBounds()

    return TrackShortDto(
        id = this.id!!,
        changeTime = this.changeTime.toDateTime(),
        road = this.road,
        name = this.name,
        description = this.description,
        coordinates = "${xMin.round(3)} - ${xMax.round(3)}",
        length = (xMax - xMin).round(3),
        firstStation = singleTrackStations.first().name,
        numberOfTracks = numberOfTracks,
    )
}

enum class DataVersion { SOURCE, EDITED, VIRGIN }

fun Track.toDto(version: DataVersion = DataVersion.EDITED): TrackDto {
    val sortedSingleTracks = when (version) {
        DataVersion.EDITED -> singleTracks
        else -> singleTracks
    }.sortedBy { it.trackNumber }

    val firstTrackStations = sortedSingleTracks[0].stations.sortedBy { it.coordinate }
    val isVirgin = sortedSingleTracks[0].profile.isEmpty()

    val (xMin, xMax) = this.spatialBounds()

    fun profileRound(profile: List<ProfileElement>): List<ProfileElement> {
        val profileRounded = mutableListOf<ProfileElement>()
        for (prof in profile) {
            profileRounded.add(
                ProfileElement(
                    prof.startCoordinate.round(3),
                    prof.length.round(3),
                    prof.i.round(3),
                    prof.ikr.round(3),
                    prof.wir
                )
            )
        }
        return profileRounded
    }

    return TrackDto(
        id = id!!,
        changeTime = changeTime.toDateTime(),
        road = road,
        name = name,
        description = description,
        coordinates = "${xMin.round(3)} - ${xMax.round(3)}",
        length = (xMax - xMin).round(3),
        firstStation = firstTrackStations.first().name,
        numberOfTracks = sortedSingleTracks.size,
        stations = sortedSingleTracks.map { track ->
            track.stations
                .sortedBy { it.coordinate }
                .map { it.toDto() }
        },
        categories = sortedSingleTracks[0].categories
            .sortedBy { it.priority }
            .reversed()
            .map { it.toDto() },

        profile = sortedSingleTracks
            .map { track -> profileRound(track.profile) },
        trackTypes = sortedSingleTracks.map { it.type.russianName },
        speedLimits = sortedSingleTracks
            .map { track -> track.categories.sortedBy { it.priority }.reversed() }
            .map { it.toLimitsDto() },
        version = if (isVirgin) DataVersion.VIRGIN else version,
        oddDirectionFromFirstStation = true
    )
}

private fun List<TrackCategory>.toLimitsDto(): List<SpeedLimitsDto> {
    val result = mutableListOf<SpeedLimitsDto>()
    val speedLimits0 = this[0].speedLimits
    for (i in speedLimits0.indices) {
        result.add(SpeedLimitsDto(
            startCoordinate = speedLimits0[i].startCoordinate.round(3),
            endCoordinate = speedLimits0[i].endCoordinate.round(3),
            limits = this.map { it.speedLimits[i].limit }
        ))
    }
    return result
}

fun Station.toDto() = StationDto(
    id = id!!,
    name = name,
    coordinate = coordinate.round(3),
    loopStation = loopStation
)

fun TrackCategory.toDto() = TrackCategoryDto(
    id = id!!,
    name = name,
    priority = priority
)
