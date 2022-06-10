package ru.vniizht.currentspreading.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.vniizht.currentspreading.dao.Track

interface TrackRepository : JpaRepository<Track, Long> {

    @Query("""select track from Track track
                        where track.active = true
            and (coalesce(:searchText, '') = '' 
            or lower(track.name) like concat('%', lower(:searchText), '%')
            )""")
    fun findWithFilter(searchText: String, pageable: Pageable): Page<Track>

    fun findAllByActiveTrue(pageable: Pageable): Page<Track>

    fun findAllByActiveTrue(sort: Sort = Sort.by("name")): List<Track>
}