package ru.vniizht.currentspreading.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.vniizht.currentspreading.dao.Car

interface CarRepository : JpaRepository<Car, Long> {
    fun findAllByIdAndActiveIsTrue(id: Long): List<Car>
}