package ru.vniizht.currentspreading.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.vniizht.asuterkortes.dto.*
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.dto.*
import ru.vniizht.currentspreading.repository.ElectricalSchemaRepository

interface ACSchemaService {
    fun getAll(): List<ElectricalSchemaDto>
    fun getById(id: Long): ElectricalSchemaDto
    fun deleteById(id: Long): List<ElectricalSchemaDto>
    fun save(dto: ACSchemaFullDto): ACSchemaFullDto
}

@Service
class ACSchemaServiceImpl(
    private val repository: ElectricalSchemaRepository
) : ACSchemaService {

    @Transactional(readOnly = true)
    override fun getAll(): List<ElectricalSchemaDto> {
        return repository.findAll().filter { it.active && it.type == SchemaType.AC }.map { it.toAcDto() }
    }

    @Transactional(readOnly = true)
    override fun getById(id: Long): ElectricalSchemaDto {
        return repository.findById(id).get().toAcDto()
    }

    @Transactional
    override fun deleteById(id: Long): List<ElectricalSchemaDto> {
        repository.deleteById(id)
        return repository.findAll().filter { it.active && it.type == SchemaType.AC }.map { it.toAcDto() }
    }

    @Transactional
    override fun save(dto: ACSchemaFullDto): ACSchemaFullDto {
        dto.mainSchema.objects.sortBy { it.coordinate }
        dto.mainSchema.network.sortBy { wire -> wire.endSection }
        dto.mainSchema.objects.forEach {
            if (it is ACStationDto) {
                if (it.stationParameters == null) {
                    throw IllegalStateException("Не заданы параметры подстанции ${it.name}")
                }
                if (it.fiders.sucking.type == null) {
                    it.fiders.sucking.type = getNullAlternateNetwork()
                }
                if (it.fiders.sucking.coordinate == 0.0) {
                    it.fiders.sucking.coordinate = it.coordinate
                }
                setDefaultFiders(it.fiders.trackFiders, it.coordinate)
            } else if (it is ACSectionDto) {
                setDefaultFiders(it.fiders.trackFiders, it.coordinate)
            }
        }
        validate(dto)

        val entity = repository.save(dto.toAcEntity())
        return entity.toAcDto()
    }

    private fun setDefaultFiders(trackFiders: List<ACTrackFiderDto>, coordinate: Double) {
        trackFiders.forEach { trackFider ->
            if (trackFider.leftFider.length == 0.0 && trackFider.leftFider.type == null) {
                trackFider.leftFider.type = getNullAlternateNetwork()
            }
            if (trackFider.leftFider.coordinate == 0.0) {
                trackFider.leftFider.coordinate = coordinate
            }
            if (trackFider.rightFider.length == 0.0 && trackFider.rightFider.type == null) {
                trackFider.rightFider.type = getNullAlternateNetwork()
            }
            if (trackFider.rightFider.coordinate == 0.0) {
                trackFider.rightFider.coordinate = coordinate
            }
        }
    }

    private fun validate(dto: ACSchemaFullDto) {
        checkExistingStations(dto.mainSchema.objects)
        for (i in dto.mainSchema.objects.indices) {
            val it = dto.mainSchema.objects[i]
            val left = if (i == 0) it.coordinate - 1000 else getRightBlockCoordinate(dto.mainSchema.objects[i - 1])
            val right =
                if (i == dto.mainSchema.objects.lastIndex) it.coordinate + 1000 else getLeftBlockCoordinate(dto.mainSchema.objects[i + 1])
            checkCoordinates(it)
            checkNames(it)
            checkFiders(it, left, right)
            if (it is ACStationDto) {
                checkAllParametersExist(it)
            }
        }
        checkNetwork(dto)
        checkName(dto)
    }

    private fun checkExistingStations(objects: MutableList<ObjectDto>) {
        if (objects.count { it is ACStationDto } < 2) {
            throw IllegalStateException("На схеме должно быть как минимум две ЭЧЭ")
        }
        if (objects.first() !is ACStationDto) {
            throw IllegalStateException("Схема должна начинаться ЭЧЭ")
        }
        if (objects.last() !is ACStationDto) {
            throw IllegalStateException("Схема должна заканчиваться ЭЧЭ")
        }
    }

    private fun checkCoordinates(it: ObjectDto) {
        if (it.coordinate < 0) {
            throw IllegalStateException("<${it.name}> - координата не должна быть меньше нуля")
        }
    }

    private fun checkNames(it: ObjectDto) {
        if (it.name == "") {
            throw IllegalStateException("У всех объектов должно быть название")
        }
    }

    private fun checkName(dto: ElectricalSchemaDto) {
        if (dto.name == "") {
            throw IllegalStateException("Необходимо заполнить название схемы")
        }
    }

    private fun checkNetwork(dto: ACSchemaFullDto) {
        val network = dto.mainSchema.network
        if (network.size == 0) {
            throw IllegalStateException("Тяговые сети должны быть заполнены")
        }
        if (network.last().endSection < dto.mainSchema.objects.last().coordinate) {
            throw IllegalStateException("Тяговые сети должны покрывать всю схему")
        }
        if (network.any { it.endSection <= 0 }) {
            throw IllegalStateException("Координаты конца секции тяговой сети должны быть больше нуля")
        }
        if (network.any { it.network.name.first().toString().toInt() != dto.trackCount }) {
            throw IllegalStateException("Сечение тяговой сети не подходит для данного количества путей")
        }
    }

    private fun checkAllParametersExist(station: ACStationDto) {
        val stationParameters = station.stationParameters!!

        if (stationParameters.phase == null || stationParameters.phase!! == "") {
            throw IllegalStateException("У станции <${station.name}> не указана фаза")
        }
        if (stationParameters.skz == null || stationParameters.skz!! <= 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное значение Sкз")
        }
        if (stationParameters.transformer == null) {
            throw IllegalStateException("У станции <${station.name}> не выбран трансформатор")
        }
        if (stationParameters.transformerCount == null || stationParameters.transformerCount!! <= 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное количество трансформаторов")
        }
        if (stationParameters.sp == null) {
            stationParameters.sp = 0.0
        } else if (stationParameters.sp!! < 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное значение Sp")
        }
        if (stationParameters.sn == null) {
            stationParameters.sn = 0.0
        } else if (stationParameters.sn!! < 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное значение Sн")
        }
    }

    private fun checkFiders(acObject: ObjectDto, leftBlockCoordinate: Double, rightBlockCoordinate: Double) {
        acObject.fiders?.let { fiders ->
            if (fiders is ACStationFiders) {
                if (fiders.trackFiders.any {
                        it.leftFider.coordinate > it.rightFider.coordinate
                    }
                ) {
                    throw IllegalStateException("ЭЧЭ <${acObject.name}> - питающие линии не должны пересекаться")
                }
                if (fiders.sucking.length < 0 ||
                    fiders.trackFiders.any {
                        it.leftFider.length < 0 || it.rightFider.length < 0
                    }
                ) {
                    throw IllegalStateException("ЭЧЭ <${acObject.name}> - длина питающих линий не может быть меньше нуля")
                }
                if ((fiders.sucking.type == null && fiders.sucking.length > 0) ||
                    fiders.trackFiders.any {
                        (it.leftFider.type == null && it.leftFider.length > 0) ||
                                (it.rightFider.type == null && it.rightFider.length > 0)
                    }
                ) {
                    throw IllegalStateException("Питающие линии не установлены у ЭЧЭ <${acObject.name}>")
                }
                if (fiders.sucking.coordinate < leftBlockCoordinate ||
                    fiders.sucking.coordinate > rightBlockCoordinate ||
                    fiders.trackFiders.any {
                        it.leftFider.coordinate < leftBlockCoordinate || it.rightFider.coordinate < leftBlockCoordinate ||
                                it.leftFider.coordinate > rightBlockCoordinate || it.rightFider.coordinate > rightBlockCoordinate
                    }
                ) {
                    throw IllegalStateException("ЭЧЭ <${acObject.name}> - координата питающих линий не может пересекаться с соседними объектами")
                }
            }
            if (fiders is ACSectionFiders) {
                if (fiders.trackFiders.any {
                        it.leftFider.coordinate > it.rightFider.coordinate
                    }
                ) {
                    throw IllegalStateException("ПС <${acObject.name}> - питающие линии не должны пересекаться")
                }
                if (fiders.trackFiders.any {
                        it.leftFider.length < 0 || it.rightFider.length < 0
                    }) {
                    throw IllegalStateException("ПС <${acObject.name}> - длина питающих линий не может быть меньше нуля")
                }
                if (fiders.trackFiders.any {
                        (it.leftFider.type == null && it.leftFider.length > 0) ||
                                (it.rightFider.type == null && it.rightFider.length > 0)
                    }) {
                    throw IllegalStateException("Питающие линии не установлены у ПС <${acObject.name}>")
                }
                if (fiders.trackFiders.any {
                        it.leftFider.coordinate < leftBlockCoordinate || it.rightFider.coordinate < leftBlockCoordinate ||
                                it.leftFider.coordinate > rightBlockCoordinate || it.rightFider.coordinate > rightBlockCoordinate
                    }) {
                    throw IllegalStateException("ПС <${acObject.name}> - координата питающих линий не может пересекаться с соседними объектами")
                }
            }

            if (fiders is Connector && fiders.firstLine == fiders.secondLine) {
                throw IllegalStateException("Неверно установлены точки присоединения у ППС <${acObject.name}>")
            }
        }
    }

    private fun getRightBlockCoordinate(obj: ObjectDto): Double {
        var rightBlock = obj.coordinate - 1000
        if (obj.fiders is ACStationFiders) {
            val fiders = obj.fiders as ACStationFiders
            if (fiders.sucking.coordinate > rightBlock) {
                rightBlock = fiders.sucking.coordinate
            }
            fiders.trackFiders.forEach {
                if (it.rightFider.coordinate > rightBlock) {
                    rightBlock = it.rightFider.coordinate
                }
            }
        }
        return rightBlock
    }

    private fun getLeftBlockCoordinate(obj: ObjectDto): Double {
        var leftBlock = obj.coordinate + 1000
        if (obj.fiders is ACStationFiders) {
            val fiders = obj.fiders as ACStationFiders
            if (fiders.sucking.coordinate < leftBlock) {
                leftBlock = fiders.sucking.coordinate
            }
            fiders.trackFiders.forEach {
                if (it.leftFider.coordinate < leftBlock) {
                    leftBlock = it.rightFider.coordinate
                }
            }
        }
        return leftBlock
    }

}
