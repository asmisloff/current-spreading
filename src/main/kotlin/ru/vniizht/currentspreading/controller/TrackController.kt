package ru.vniizht.currentspreading.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.vniizht.currentspreading.dto.DataVersion
import ru.vniizht.currentspreading.dto.TrackDto
import ru.vniizht.currentspreading.dto.TrackShortDto
import ru.vniizht.currentspreading.service.TrackService
import ru.vniizht.currentspreading.util.PAGEABLE_DEFAULT_SIZE
import ru.vniizht.currentspreading.util.PagedResult

@RestController
@RequestMapping(path = ["api/tracks"], produces = [MediaType.APPLICATION_JSON_VALUE])
class TrackController(
    private val service: TrackService
) {

    @GetMapping
    fun getAll(
        @RequestParam(required = false, name = "search-text") searchText: String?,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = PAGEABLE_DEFAULT_SIZE.toString()) size: Int
    ): PagedResult<TrackShortDto> = service.getAll(searchText, page, size)

    @GetMapping(path = ["/names"])
    fun getAllNames() =
        ResponseEntity(service.getAllNames(), HttpStatus.OK)

    @GetMapping(path = ["/{id}"])
    fun getById(@PathVariable id: Long, @RequestParam("version", defaultValue = "EDITED") version: DataVersion) =
        ResponseEntity(service.getById(id, version), HttpStatus.OK)

    @PostMapping("/update")
    fun updateTrack(@RequestBody dto: TrackDto): ResponseEntity<TrackDto> {
        return ResponseEntity<TrackDto>(service.updateTrack(dto), HttpStatus.OK)
    }

    @PostMapping("/copy/{id}")
    fun copyTrack(
        @PathVariable("id") id: Long,
        @RequestParam(required = false, name = "search-text") searchText: String?,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = PAGEABLE_DEFAULT_SIZE.toString()) size: Int
    ): PagedResult<TrackShortDto> {
        service.copyById(id)
        return service.getAll(searchText, page, size)
    }

    @DeleteMapping(path = ["/{id}"])
    fun deleteById(@PathVariable id: Long) = service.deleteById(id)

    @PostMapping("/save")
    fun save(@RequestBody dto: TrackDto) = ResponseEntity(service.saveOrUpdate(dto), HttpStatus.OK)

    @DeleteMapping("/forever/{id}")
    fun deleteForever(@PathVariable id: Long) = service.deleteForeverById(id)

}