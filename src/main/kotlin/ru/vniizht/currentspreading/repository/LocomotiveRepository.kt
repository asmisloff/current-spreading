package ru.vniizht.currentspreading.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.vniizht.currentspreading.dao.Locomotive
import ru.vniizht.currentspreading.dao.enums.LocomotiveCurrent

interface LocomotiveRepository : JpaRepository<Locomotive, Long> {
    fun findByCurrent(locomotiveCurrent: LocomotiveCurrent): Locomotive
}