package ru.vniizht.currentspreading.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.vniizht.currentspreading.dto.ACSchemaFullDto
import ru.vniizht.currentspreading.dto.AcdSchemaFullDto
import ru.vniizht.currentspreading.dto.DCSchemaFullDto
import ru.vniizht.currentspreading.service.ACDSchemaService
import ru.vniizht.currentspreading.service.ACSchemaService
import ru.vniizht.currentspreading.service.DCSchemaService
import ru.vniizht.currentspreading.service.ElectricalSchemaService

@RestController
@RequestMapping(path = ["api/schema"], produces = [MediaType.APPLICATION_JSON_VALUE])
class SchemaController(
    private val dcService: DCSchemaService,
    private val acService: ACSchemaService,
    private val acdService: ACDSchemaService,
    private val electricalService: ElectricalSchemaService
) {

    @GetMapping(path = ["/dc"])
    fun getAllDC() =
        ResponseEntity(dcService.getAll(), HttpStatus.OK)

    @GetMapping(path = ["/dc/{id}"])
    fun getDcById(@PathVariable id: Long) =
        ResponseEntity(dcService.getById(id), HttpStatus.OK)

    @GetMapping(path = ["/ac"])
    fun getAllAC() =
        ResponseEntity(acService.getAll(), HttpStatus.OK)

    @GetMapping(path = ["/ac/{id}"])
    fun geAcById(@PathVariable id: Long) =
        ResponseEntity(acService.getById(id), HttpStatus.OK)

    @GetMapping(path = ["/acd"])
    fun getAllACD() =
        ResponseEntity(acdService.getAll(), HttpStatus.OK)

    @GetMapping(path = ["/acd/{id}"])
    fun geAcdById(@PathVariable id: Long) =
        ResponseEntity(acdService.getById(id), HttpStatus.OK)

    @DeleteMapping(path = ["/dc/{id}"])
    fun deleteDcById(@PathVariable id: Long) =
        ResponseEntity(dcService.deleteById(id), HttpStatus.OK)

    @DeleteMapping(path = ["/ac/{id}"])
    fun deleteAcById(@PathVariable id: Long) =
        ResponseEntity(acService.deleteById(id), HttpStatus.OK)

    @DeleteMapping(path = ["/acd/{id}"])
    fun deleteAcdById(@PathVariable id: Long) =
        ResponseEntity(acdService.deleteById(id), HttpStatus.OK)

    @PostMapping(path = ["/dc"])
    fun save(@RequestBody dto: DCSchemaFullDto) =
        ResponseEntity(dcService.save(dto), HttpStatus.OK)

    @PostMapping(path = ["/ac"])
    fun save(@RequestBody dto: ACSchemaFullDto) =
        ResponseEntity(acService.save(dto), HttpStatus.OK)

    @PostMapping(path = ["/acd"])
    fun save(@RequestBody dto: AcdSchemaFullDto) =
        ResponseEntity(acdService.save(dto), HttpStatus.OK)

    @GetMapping(path = ["/by-track"])
    fun getAllByTrackId(@RequestParam(required = true, name = "track-id") trackId: Long) =
        ResponseEntity(electricalService.getAllByTrackId(trackId), HttpStatus.OK)

    @GetMapping(path = ["/number-of-track"])
    fun getNumberOfTracks(
        @RequestParam(required = true, name = "track-id") trackId: Long,
        @RequestParam(required = true, name = "schema-id") schemaId: Long
    ) =
        ResponseEntity(
            electricalService.getNumberOfTracks(trackId, schemaId),
            HttpStatus.OK
        ) //TODO удалить, когда будет не нужно

    @GetMapping(path = ["/schema-parameters"])
    fun getSchemaParameters(
        @RequestParam(required = true, name = "track-id") trackId: Long,
        @RequestParam(required = true, name = "schema-id") schemaId: Long
    ) =
        ResponseEntity(electricalService.getSchemaParameters(trackId, schemaId), HttpStatus.OK)

    @PostMapping(path = ["/{id}"])
    fun copyById(@PathVariable id: Long) =
        electricalService.copyById(id)

}
