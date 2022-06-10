package ru.vniizht.currentspreading.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.vniizht.currentspreading.dao.TractiveCalculate

interface TractiveCalculateRepository : JpaRepository<TractiveCalculate, Long> {

    @Query(
        """select calculate from TractiveCalculate calculate
                        where calculate.active = true
            and (coalesce(:searchText, '') = '' 
            or lower(calculate.description) like concat('%', lower(:searchText), '%')
            )"""
    )
    fun findWithFilter(searchText: String, pageable: Pageable): Page<TractiveCalculate>


    fun findAllByActiveTrue(pageable: Pageable): Page<TractiveCalculate>

    fun findAllByActiveTrue(sort: Sort = Sort.by("changeTime")): List<TractiveCalculate>

    fun findAllByActiveTrueAndTrack_Id(trackId: Long, pageable: Pageable): Page<TractiveCalculate>

    fun findAllByActiveTrueAndTrack_Id(trackId: Long, sort: Sort = Sort.by("changeTime")): List<TractiveCalculate>

    @Query(
        value = """
            SELECT * FROM asu_ter.asu_ter_k_main_tractive_calculate tc
            WHERE locomotive_id IN (
                SELECT id FROM asu_ter.asu_ter_k_main_locomotive loc WHERE loc.current = :locomotiveCurrent
            )
            AND tc.track_id = :trackId
            AND tc.active = true;
        """,
        nativeQuery = true
    )
    fun findAllByActiveTrueAndTrackIdAndLocomotiveCurrent(
        trackId: Long,
        locomotiveCurrent: String
    ): List<TractiveCalculate>

    @Query(
        value = "SELECT COUNT(*) FROM asu_ter.asu_ter_k_main_tractive_calculate\n" +
                "WHERE track_id = :id AND active = true;",
        nativeQuery = true
    )
    fun countActiveWithTrackId(id: Long): Int

}