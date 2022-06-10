package ru.vniizht.currentspreading.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.vniizht.currentspreading.dao.CarsToTrain
import ru.vniizht.currentspreading.dao.CarsToTrainId

interface CarsToTrainRepository : JpaRepository<CarsToTrain, CarsToTrainId> {
    fun getByTrainId(id: Long): List<CarsToTrain>
}