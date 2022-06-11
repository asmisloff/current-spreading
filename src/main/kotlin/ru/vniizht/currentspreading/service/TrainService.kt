package ru.vniizht.currentspreading.service

import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.vniizht.asuterkortes.dao.model.jsonb.ResistanceToMotion
import ru.vniizht.currentspreading.dto.TrainFullDto
import ru.vniizht.currentspreading.dao.CarsToTrain
import ru.vniizht.currentspreading.dao.CarsToTrainId
import ru.vniizht.currentspreading.dao.Train
import ru.vniizht.currentspreading.dao.enums.getBrakeTypeOfRussianNames
import ru.vniizht.currentspreading.repository.CarRepository
import ru.vniizht.currentspreading.repository.CarsToTrainRepository
import ru.vniizht.currentspreading.repository.TrainRepository
import ru.vniizht.currentspreading.util.checkFieldInRange
import ru.vniizht.currentspreading.util.checkPositiveField
import ru.vniizht.currentspreading.util.checkPositiveOrZeroField
import java.time.LocalDateTime

@Service
class TrainService(
    private val repository: TrainRepository,
    private val carRepository: CarRepository,
    private val carsToTrainRepository: CarsToTrainRepository
) {

    private val logger = KotlinLogging.logger { }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(fullDto: TrainFullDto): TrainFullDto {
        validateDto(fullDto)
        val train = repository.findByIdOrNull(fullDto.id) ?: repository.save(fullDto.toEntity())

        carsToTrainRepository.deleteAll(train.carsToTrain)

        fullDto.cars.forEach {
            carsToTrainRepository.save(
                CarsToTrain(
                    id = CarsToTrainId(carId = it.id, trainId = train.id!!),
                    count = it.numberOfCars,
//                    train = train,
                    car = carRepository.getById(it.id)
                )
            )
        }

        val dtoCars: List<CarsToTrain> = getCarsToTrain(train.id!!)
        val dtoResistanceToMotion = getTrainResistanceToMotion(dtoCars)
        val dtoWeight = dtoCars.sumOf { it.car!!.weight * it.count }
        val dtoLength = dtoCars.sumOf { it.car!!.length * it.count }

        train.apply {
            active = true
            brakeBlockType = getBrakeTypeOfRussianNames(fullDto.brakeType)
            brakeForce = fullDto.brakeForce
            changeTime = LocalDateTime.now()
            name = fullDto.name
            undercarGeneratorPower = fullDto.generatorPower
            weight = dtoWeight
            length = dtoLength
            carsToTrain = dtoCars
            resistanceToMotion = dtoResistanceToMotion
        }

        return repository.save(train).apply {
            logger.info { "Train with id ${this.id} was saved" }
        }.toFullDto()
    }

    fun validateDto(fullDto: TrainFullDto) {
        getBrakeTypeOfRussianNames(fullDto.brakeType)
        fullDto.brakeForce?.let { checkPositiveOrZeroField(it, "сила нажатия колодок на ось") }
        fullDto.brakeForce?.let { checkFieldInRange(it, "сила нажатия колодок на ось", 0.0, 9999.0) }
        fullDto.generatorPower?.let { checkPositiveOrZeroField(it, "мощность подвагонного генератора") }
        fullDto.generatorPower?.let { checkFieldInRange(it, "мощность подвагонного генератора", 0.0, 9999.0) }
        checkPositiveField(fullDto.cars.size.toDouble(), "количество вагонов")
        fullDto.cars.forEach {
            if (carRepository.findAllByIdAndActiveIsTrue(it.id).isEmpty()) {
                throw IllegalStateException("Нет вагона с id = '${it.id}'")
            }
            checkPositiveField(it.numberOfCars.toDouble(), "количество вагонов")
            checkFieldInRange(it.numberOfCars.toDouble(), "количество вагонов", 1.0, 999.0)
        }
    }

    fun getTrainResistanceToMotion(dtoCars: List<CarsToTrain>): ResistanceToMotion {
        val totalCars = dtoCars.sumOf { it.count }
        if (totalCars == 0) {
            return ResistanceToMotion(componentRail = arrayOf(0.0, 0.0, 0.0), continuousRail = arrayOf(0.0, 0.0, 0.0))
        }
        val contB = getContinuousResistance(dtoCars, 1, totalCars)
        val contC = getContinuousResistance(dtoCars, 2, totalCars)
        val contD = getContinuousResistance(dtoCars, 3, totalCars)
        val compB = getComponentResistance(dtoCars, 1, totalCars)
        val compC = getComponentResistance(dtoCars, 2, totalCars)
        val compD = getComponentResistance(dtoCars, 3, totalCars)
        return ResistanceToMotion(
            componentRail = arrayOf(compB, compC, compD),
            continuousRail = arrayOf(contB, contC, contD)
        )
    }

    private fun getContinuousResistance(
        dtoCars: List<CarsToTrain>,
        position: Int,
        totalCars: Int
    ): Double {
        return dtoCars.sumOf {
            val axleWeight = it.car!!.weight / it.car.numberOfAxles.number
            getResistance(axleWeight, it.car.resistanceToMotion.continuousRail, position, it.count)
        } / totalCars
    }

    private fun getComponentResistance(
        dtoCars: List<CarsToTrain>,
        position: Int,
        totalCars: Int
    ): Double {
        return dtoCars.sumOf {
            val axleWeight = it.car!!.weight / it.car.numberOfAxles.number
            getResistance(axleWeight, it.car.resistanceToMotion.componentRail, position, it.count)
        } / totalCars
    }

    private fun getResistance(
        axleWeight: Double,
        resistanceArray: Array<Double?>,
        position: Int,
        carsCount: Int
    ): Double {
        return if (axleWeight > 6) {
            if (position == 1) {
                (resistanceArray[0]!! + resistanceArray[1]!! / axleWeight) * carsCount
            } else {
                (resistanceArray[position]!! / axleWeight) * carsCount
            }
        } else {
            resistanceArray[position]!! * carsCount
        }
    }

    private fun getCarsToTrain(trainId: Long): List<CarsToTrain> {
        return carsToTrainRepository.getByTrainId(trainId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun copy(entity: Train): Train {
        val copy = Train(
            id = -1,
            name = "${entity.name}_копия",
            changeTime = LocalDateTime.now(),
            active = true,
            weight = entity.weight,
            length = entity.length,
            resistanceToMotion = entity.resistanceToMotion?.copy(),
            brakeForce = entity.brakeForce,
            undercarGeneratorPower = entity.undercarGeneratorPower,
            brakeBlockType = entity.brakeBlockType,
            carsToTrain = entity.carsToTrain.toList()
        )
        val result = repository.save(copy)
        copy.carsToTrain.forEach {
            carsToTrainRepository.save(
                CarsToTrain(
                    id = CarsToTrainId(carId = it.car!!.id!!, trainId = result.id!!),
                    count = it.count,
                    car = carRepository.getById(it.car.id!!)
                )
            )
        }

        return result
    }

}
