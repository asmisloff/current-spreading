package ru.vniizht.currentspreading.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.dto.*
import ru.vniizht.currentspreading.repository.ElectricalSchemaRepository
import ru.vniizht.currentspreading.repository.TrackRepository
import ru.vniizht.currentspreading.util.round
import ru.vniizht.currentspreading.util.within
import kotlin.math.min

interface ElectricalSchemaService {
    fun getAllByTrackId(trackId: Long): List<ElectricalSchemaShortDto>
    fun getNumberOfTracks(trackId: Long, schemaId: Long): Int
    fun getSchemaParameters(trackId: Long, schemaId: Long): SchemaParametersDto
    fun copyById(id: Long): List<ElectricalSchemaDto>
    fun getAnyDcSchema(): DCSchemaFullDto
    fun getAnyAcSchema(): ACSchemaFullDto
}

@Service
class ElectricalSchemaServiceImpl(
    private val trackRepository: TrackRepository,
    private val repository: ElectricalSchemaRepository
) : ElectricalSchemaService {

    @Transactional(readOnly = true)
    override fun getAllByTrackId(trackId: Long): List<ElectricalSchemaShortDto> {
        val track = trackRepository.getById(trackId)
        return repository.findAll().filter { schema ->
            schema.active && schema.spatialBounds() within track.spatialBounds()
        }.map { it.toShortDto() }
    }

    @Transactional(readOnly = true)
    override fun getNumberOfTracks(trackId: Long, schemaId: Long): Int {
        val track = trackRepository.getById(trackId)
        val schema = repository.getById(schemaId)
        return min(track.numberOfTracks, schema.trackCount)
    }

    @Transactional(readOnly = true)
    override fun getSchemaParameters(trackId: Long, schemaId: Long): SchemaParametersDto {
        val track = trackRepository.getById(trackId)
        val schema = repository.getById(schemaId)
        val kps = schema.mainSchema.objects.count { it is DCStationDto || it is ACStationDto }
        val lks = schema.length.toDouble()
        val stationDurability = (1 - 9.93 * 1e-5 * kps).round(3)
        val toolsDurability = (1 - 5.8 * 1e-6 * lks).round(3)
        val networkDurability = (stationDurability + toolsDurability - 1).round(3)
        return SchemaParametersDto(
            trackCount = min(track.numberOfTracks, schema.trackCount),
            stationDurability = stationDurability,
            toolsDurability = toolsDurability,
            networkDurability = networkDurability,
            schemaName = schema.name,
            trackName = track.name
        )
    }

    @Transactional
    override fun copyById(id: Long): List<ElectricalSchemaDto> {
        val schema = repository.getById(id)
        val schemaType = schema.type
        val schemaCopy = schema.getCopy()
        repository.save(schemaCopy)
        return when (schemaType) {
            SchemaType.DC -> {
                repository.findAll().filter { it.active && it.type == SchemaType.DC }.map { it.toDcDto() }
            }
            SchemaType.AC -> {
                repository.findAll().filter { it.active && it.type == SchemaType.AC }.map { it.toAcDto() }
            }
            else -> {
                repository.findAll().filter { it.active && it.type == SchemaType.ACD }.map { it.toAcdDto() }
            }
        }
    }

    @Transactional
    fun deleteById(id: Long) {
        repository.deleteById(id)
    }

    override fun getAnyDcSchema(): DCSchemaFullDto {
        return repository.findAllByType(SchemaType.DC).first().toDcDto()
    }

    override fun getAnyAcSchema(): ACSchemaFullDto {
        return repository.findAllByType(SchemaType.AC).first().toAcDto()
    }

}
