package ru.vniizht.currentspreading.service

import javassist.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.vniizht.asuterkortes.dto.NamesDto
import ru.vniizht.currentspreading.dao.SingleTrack
import ru.vniizht.currentspreading.dao.Station
import ru.vniizht.currentspreading.dao.Track
import ru.vniizht.currentspreading.dao.TrackCategory
import ru.vniizht.currentspreading.dao.enums.TrackType
import ru.vniizht.currentspreading.dao.jsonb.DbSpeedLimit
import ru.vniizht.currentspreading.dto.*
import ru.vniizht.currentspreading.repository.TrackRepository
import ru.vniizht.currentspreading.repository.TractiveCalculateRepository
import ru.vniizht.currentspreading.util.*
import java.time.LocalDateTime

@Service
class TrackService(
    private val trackRepository: TrackRepository,
    private val tractiveRepository: TractiveCalculateRepository
) {

    @Transactional(readOnly = true)
    fun getAll(): List<TrackShortDto> {
        return trackRepository.findAllByActiveTrue().map { it.toShortDto() }
    }

    @Transactional(readOnly = true)
    fun getAllNames(): List<NamesDto> {
        return trackRepository.findAllByActiveTrue().map { NamesDto(it.id!!, it.name) }
    }

    @Transactional(readOnly = true)
    fun getAll(searchText: String?, page: Int, size: Int): PagedResult<TrackShortDto> {
        val tracks = searchText?.let {
            trackRepository.findWithFilter(
                searchText,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "road", "name"))
            )
        }
            ?: trackRepository.findAllByActiveTrue(
                PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Direction.ASC, "road", "name")
                )
            )

        val dtos = mutableListOf<TrackShortDto>()
        for (track in tracks.content) {
            dtos.add(track.toShortDto())
        }

        return PagedResult(dtos, tracks.totalElements, tracks.totalPages)
    }

    @Transactional
    fun getById(id: Long, version: DataVersion): TrackDto {
        val track = trackRepository.findById(id)
            .orElseThrow {
                NotFoundException("Путь с id=$id не найден в базе данных")
            }
        return track.toDto(version)
    }

    @Transactional
    fun getByIdIfActive(id: Long, version: DataVersion): TrackDto {
        val track = trackRepository.findById(id)
            .orElseThrow {
                NotFoundException("Путь с id=$id не найден в базе данных")
            }
            .check("Путь удален") { it.active }
        return track.toDto(version)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun deleteById(id: Long): PagedResult<TrackDto> {
        trackRepository.deleteById(id)
        return trackRepository.findAllByActiveTrue(PageRequest.of(0, PAGEABLE_DEFAULT_SIZE, Sort.by("name")))
            .map { it.toDto() }.toDto()
    }

    @Transactional
    fun updateTrack(dto: TrackDto): TrackDto {
        /*Кол. активных тяг. расч., привязанных к этому участку*/
        val relatedActiveTcQty = tractiveRepository.countActiveWithTrackId(dto.id)
        require(relatedActiveTcQty == 0) {
            "С данным участком связаны один или несколько тяговых расчетов. " +
                    "Изменение параметров участка приведет к рассогласованию данных и будет порождать ошибки." +
                    "Удалите тяговые расчеты, связанные с участком, или скопируйте участок и измените копию."
        }

        val track = trackRepository.findById(dto.id).orElseThrow {
            NotFoundException("Track id = ${dto.id} not found in database")
        }

        track.description = dto.description
        track.name = dto.name
        track.changeTime = LocalDateTime.now()
        track.road = dto.road
        track.singleTracks.sortBy { it.trackNumber }
        val singleTracks = dto.profile.mapIndexed { trackIndex, profile ->
            val singleTrack = when {
                trackIndex <= track.singleTracks.lastIndex -> track.singleTracks[trackIndex]
                else -> {
                    val st = SingleTrack(
                        id = null,
                        track = track,
                        trackNumber = trackIndex + 1,
                        type = TrackType.fromRussianName(dto.trackTypes[trackIndex]),
                        categories = mutableListOf()
                    )
                    st.categories = dto.categories.mapTo(mutableListOf()) { categoryDto ->
                        TrackCategory(
                            name = categoryDto.name,
                            singleTrack = st,
                            priority = categoryDto.priority
                        )
                    }
                    st
                }
            }
            singleTrack.profile = profile.toMutableList()
            singleTrack.categories.sortBy { it.priority }  // Важно! С фронта данные категорий приходят в виде массива, упорядоченного по полю priority.
            singleTrack.categories.forEachIndexed { i, category ->
                category.speedLimits = dto.speedLimits[trackIndex].mapTo(mutableListOf()) {
                    DbSpeedLimit(it.startCoordinate, it.endCoordinate, it.limits[i])
                }
            }

            dto.stations[trackIndex].forEach { stationDto ->
                singleTrack.stations.add(
                    Station(
                        id = when {
                            stationDto.id == -1L -> null
                            singleTrack.stations.find { it.id == stationDto.id } != null -> null
                            else -> stationDto.id
                        },
                        singleTrack = singleTrack,
                        name = stationDto.name,
                        coordinate = stationDto.coordinate,
                        loopStation = stationDto.loopStation
                    )
                )
            }
            singleTrack.type = TrackType.fromRussianName(dto.trackTypes[trackIndex])
            singleTrack
        }
        track.singleTracks.merge(singleTracks)
        track.numberOfTracks = singleTracks.count { it.profile.isNotEmpty() }

        return trackRepository.save(track).toDto(DataVersion.EDITED)
    }

    private fun MutableList<SingleTrack>.merge(singleTracks: List<SingleTrack>) {
        this.clear()
        this.addAll(singleTracks)
        this.sortBy { it.trackNumber }
    }

    @Transactional
    fun copyById(id: Long): Track {
        val original = trackRepository.getById(id)
        val copy = Track(
            id = null,
            active = true,
            changeTime = LocalDateTime.now(),
            road = original.road,
            name = "${original.name}__копия",
            description = original.description,
            numberOfTracks = original.numberOfTracks,
            differentTracks = original.differentTracks,
            singleTracks = mutableListOf()
        )
        original.singleTracks.forEach { copy.singleTracks.add(it.getCopy(copy)) }

        return trackRepository.save(copy)
    }

    @Transactional
    fun savePfk(track: Track): TrackDto {
        trackRepository.save(track)
        return track.toDto()
    }

    @Transactional
    fun saveOrUpdate(dto: TrackDto): TrackDto {
        if (dto.id != -1L) {
            return updateTrack(dto)
        }
        require(dto.name.isNotBlank()) { "Необходимо ввести наименование участка" }
        require(dto.road.isNotBlank()) { "Необходимо ввести наименование дороги" }
        require(dto.stations.map { it.first().name }.distinct().count() == 1) {
            "Все пути должны начинаться с одной станции"
        }
        require(dto.stations.map { it.last().name }.distinct().count() == 1) {
            "Все пути должны заканчиваться на одной станции"
        }
        require(
            dto.stations
                .mapIndexed { i, s ->
                    require(dto.profile[i].isNotEmpty()) { "Профиль пути №${i + 1} пуст" }
                    dto.profile[i].first().startCoordinate eq s.first().coordinate
                }
                .all { true }
        ) {
            "Профиль пути пути не может начинаться после координаты первого раздельного пункта. Для одного или нескольких путей это требование не выполнено."
        }
        require(
            dto.stations
                .mapIndexed { i, s ->
                    val lastProfElt = dto.profile[i].last()
                    lastProfElt.startCoordinate + lastProfElt.length >= s.last().coordinate
                }
                .all { true }
        ) {
            "Профиль пути не может заканчиваться раньше координаты последнего раздельного пункта. Для одного или нескольких путей это требование не выполнено."
        }


        val track = Track(
            road = dto.road,
            name = dto.name,
            description = dto.description,
            numberOfTracks = dto.numberOfTracks,
            differentTracks = true,
            singleTracks = mutableListOf()
        )

        val singleTracks = (0 until dto.numberOfTracks).mapTo(mutableListOf()) { trackIndex ->
            val singleTrack = SingleTrack(
                track = track,
                trackNumber = trackIndex + 1,
                type = TrackType.fromRussianName(dto.trackTypes[trackIndex]),
                profile = dto.profile[trackIndex]
            )
            singleTrack.stations = dto.stations[trackIndex].mapTo(mutableListOf()) {
                Station(
                    singleTrack = singleTrack,
                    name = it.name,
                    coordinate = it.coordinate,
                    loopStation = it.loopStation
                )
            }
            singleTrack.categories = dto.categories.mapIndexedTo(mutableListOf()) { categoryIndex, categoryDto ->
                TrackCategory(
                    name = categoryDto.name,
                    singleTrack = singleTrack,
                    priority = categoryDto.priority,
                    speedLimits = dto.speedLimits[trackIndex].map {
                        DbSpeedLimit(
                            startCoordinate = it.startCoordinate,
                            endCoordinate = it.endCoordinate,
                            limit = it.limits[categoryIndex]
                        )
                    }
                )
            }

            singleTrack
        }

        track.singleTracks = singleTracks

        return trackRepository.save(track).toDto()
    }

    @Transactional
    fun deleteForeverById(id: Long): PagedResult<TrackDto> {
        trackRepository.deleteById(id)
        return trackRepository
            .findAllByActiveTrue(
                PageRequest.of(0, PAGEABLE_DEFAULT_SIZE, Sort.by("name"))
            )
            .map { it.toDto() }.toDto()
    }
}

private fun SingleTrack.getCopy(track: Track): SingleTrack {
    val copy = SingleTrack(
        id = null,
        track = track,
        trackNumber = this.trackNumber,
        type = this.type,
        profile = this.profile.toMutableList(),
        stations = mutableListOf(),
        categories = mutableListOf()
    )
    copy.stations = this.stations.mapTo(mutableListOf()) { it.getCopy(copy) }
    copy.categories = this.categories.mapTo(mutableListOf()) { it.getCopy(copy) }
    return copy
}

private fun TrackCategory.getCopy(singleTrack: SingleTrack) = TrackCategory(
    id = null,
    name = this.name,
    priority = this.priority,
    speedLimits = this.speedLimits.toMutableList(),
    singleTrack = singleTrack
)

private fun Station.getCopy(singleTrack: SingleTrack) = Station(
    id = null,
    singleTrack = singleTrack,
    name = this.name,
    coordinate = this.coordinate,
    loopStation = this.loopStation
)
