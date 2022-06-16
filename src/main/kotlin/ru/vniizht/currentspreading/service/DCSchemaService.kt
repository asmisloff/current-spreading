package ru.vniizht.currentspreading.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.vniizht.asuterkortes.dto.getNullDirectNetwork
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.currentspreading.dao.enums.SchemaType
import ru.vniizht.currentspreading.dto.*
import ru.vniizht.currentspreading.repository.ElectricalSchemaRepository
import ru.vniizht.currentspreading.util.check

interface DCSchemaService {
    fun getAll(): List<ElectricalSchemaDto>
    fun getById(id: Long): ElectricalSchemaDto
    fun deleteById(id: Long): List<ElectricalSchemaDto>
    fun save(dto: DCSchemaFullDto): DCSchemaFullDto
    fun saveCk3(electricalSchema: ElectricalSchema)
}

@Service
class DCSchemaServiceImpl(
    private val repository: ElectricalSchemaRepository
) : DCSchemaService {

    @Transactional(readOnly = true)
    override fun getAll(): List<ElectricalSchemaDto> {
        return repository.findAll().filter { it.active && it.type == SchemaType.DC }.map { it.toDcDto() }
    }

    @Transactional(readOnly = true)
    override fun getById(id: Long): ElectricalSchemaDto {
        return repository.findById(id).get().toDcDto()
    }

    @Transactional
    override fun deleteById(id: Long): List<ElectricalSchemaDto> {
        repository.deleteById(id)
        return repository.findAll().filter { it.active && it.type == SchemaType.DC }.map { it.toDcDto() }
    }

    @Transactional
    override fun save(dto: DCSchemaFullDto): DCSchemaFullDto {
        dto.mainSchema.objects.sortBy { it.coordinate }
        dto.mainSchema.network.forEach { it.sortBy { wire -> wire.endSection } }
        dto.mainSchema.objects.forEach {
            when (it) {
                is DCStationDto -> {
                    if (it.stationParameters == null) {
                        throw IllegalStateException("Не заданы параметры подстанции ${it.name}")
                    }
                    if (it.fiders.sucking.type == null) {
                        it.fiders.sucking.type = getNullDirectNetwork()
                    }
                    if (it.fiders.sucking.coordinate == 0.0) {
                        it.fiders.sucking.coordinate = it.coordinate
                    }
                    setDefaultFiders(it.fiders.trackFiders, it.coordinate)
                }
                is DCSectionDto -> {
                    setDefaultFiders(it.fiders.trackFiders, it.coordinate)
                }
            }
        }
        validate(dto)

        val entity = repository.save(dto.toDcEntity())
        return entity.toDcDto()
    }

    private fun setDefaultFiders(
        trackFiders: List<DCTrackFiderDto>,
        coordinate: Double
    ) {
        trackFiders.forEach { trackFider ->
            if (trackFider.leftFider.length == 0.0 && trackFider.leftFider.type == null) {
                trackFider.leftFider.type = getNullDirectNetwork()
            }
            if (trackFider.leftFider.coordinate == 0.0) {
                trackFider.leftFider.coordinate = coordinate
            }
            if (trackFider.rightFider.length == 0.0 && trackFider.rightFider.type == null) {
                trackFider.rightFider.type = getNullDirectNetwork()
            }
            if (trackFider.rightFider.coordinate == 0.0) {
                trackFider.rightFider.coordinate = coordinate
            }
        }
    }

    private fun validate(dto: DCSchemaFullDto) {
        require(dto.mainSchema.objects.isNotEmpty()) { "В схеме нет объектов" }
        dto.mainSchema.objects.sortBy { it.coordinate }
        checkExistingStations(dto.mainSchema.objects)
        for (i in dto.mainSchema.objects.indices) {
            val it = dto.mainSchema.objects[i]
            val left = if (i == 0) it.coordinate - 1000 else getRightBlockCoordinate(dto.mainSchema.objects[i - 1])
            val right =
                if (i == dto.mainSchema.objects.lastIndex) it.coordinate + 1000 else getLeftBlockCoordinate(dto.mainSchema.objects[i + 1])
            checkCoordinates(it)
            checkNames(it)
            checkFiders(it, left, right)
            if (it is DCStationDto) {
                checkAllParametersExist(it)
                it.stationParameters?.directRo?.check("Значения параметра Rо.в должны лежать в диапазоне [0.01, 0.9] (ЭЧЭ ${it.name}, ${it.coordinate} км).") { r -> r in 0.01..0.9 }
            }
        }
        checkNetwork(dto)
        checkName(dto)
    }

    private fun getRightBlockCoordinate(obj: ObjectDto): Double {
        var rightBlock = obj.coordinate - 1000
        if (obj.fiders is DCStationFiders) {
            val fiders = obj.fiders as DCStationFiders
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
        if (obj.fiders is DCStationFiders) {
            val fiders = obj.fiders as DCStationFiders
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

    private fun checkName(dto: DCSchemaFullDto) {
        if (dto.name == "") {
            throw IllegalStateException("Необходимо заполнить название схемы")
        }
    }

    private fun checkNetwork(dto: DCSchemaFullDto) {
        val network = dto.mainSchema.network
        if (network.size < dto.trackCount) {
            throw IllegalStateException("Необходимо заполнить все тяговые сети")
        }
        for (i in network.indices) {
            val net = network[i]
            if (net.size == 0) {
                throw IllegalStateException("Путь ${i + 1} - тяговые сети должны быть заполнены")
            }
            if (net.last().endSection < dto.mainSchema.objects.last().coordinate) {
                throw IllegalStateException("Путь ${i + 1} - тяговые сети должны покрывать всю схему")
            }
            if (net.any { it.endSection <= 0 }) {
                throw IllegalStateException("Путь ${i + 1} - координаты конца секции тяговой сети должны быть больше нуля")
            }
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

    private fun checkExistingStations(objects: MutableList<ObjectDto>) {
        if (objects.count { it is DCStationDto } < 2) {
            throw IllegalStateException("На схеме должно быть как минимум две ЭЧЭ")
        }
        if (objects.first() !is DCStationDto) {
            throw IllegalStateException("Схема должна начинаться ЭЧЭ")
        }
        if (objects.last() !is DCStationDto) {
            throw IllegalStateException("Схема должна заканчиваться ЭЧЭ")
        }
    }

    private fun checkFiders(dcObject: ObjectDto, leftBlockCoordinate: Double, rightBlockCoordinate: Double) {
        dcObject.fiders?.let { fiders ->
            if (fiders is DCStationFiders) {
                if (fiders.trackFiders.any {
                        it.leftFider.coordinate > it.rightFider.coordinate
                    }
                ) {
                    throw IllegalStateException("ЭЧЭ <${dcObject.name}> - питающие линии не должны пересекаться")
                }
                if (fiders.sucking.length < 0 ||
                    fiders.trackFiders.any {
                        it.leftFider.length < 0 || it.rightFider.length < 0
                    }
                ) {
                    throw IllegalStateException("ЭЧЭ <${dcObject.name}> - длина питающих линий не может быть меньше нуля")
                }
                if ((fiders.sucking.type == null && fiders.sucking.length > 0) ||
                    fiders.trackFiders.any {
                        (it.leftFider.type == null && it.leftFider.length > 0) ||
                                (it.rightFider.type == null && it.rightFider.length > 0)
                    }
                ) {
                    throw IllegalStateException("Питающие линии не установлены у ЭЧЭ <${dcObject.name}>")
                }
                if (fiders.sucking.coordinate < leftBlockCoordinate ||
                    fiders.sucking.coordinate > rightBlockCoordinate ||
                    fiders.trackFiders.any {
                        it.leftFider.coordinate < leftBlockCoordinate || it.rightFider.coordinate < leftBlockCoordinate ||
                                it.leftFider.coordinate > rightBlockCoordinate || it.rightFider.coordinate > rightBlockCoordinate
                    }
                ) {
                    throw IllegalStateException("ЭЧЭ <${dcObject.name}> - координата питающих линий не может пересекаться с соседними объектами")
                }
            }
            if (fiders is DCSectionFiders) {
                if (fiders.trackFiders.any {
                        it.leftFider.coordinate > it.rightFider.coordinate
                    }
                ) {
                    throw IllegalStateException("ПС <${dcObject.name}> - питающие линии не должны пересекаться")
                }
                if (fiders.trackFiders.any {
                        it.leftFider.length < 0 || it.rightFider.length < 0
                    }) {
                    throw IllegalStateException("ПС <${dcObject.name}> - длина питающих линий не может быть меньше нуля")
                }
                if (fiders.trackFiders.any {
                        (it.leftFider.type == null && it.leftFider.length > 0) ||
                                (it.rightFider.type == null && it.rightFider.length > 0)
                    }) {
                    throw IllegalStateException("Питающие линии не установлены у ПС <${dcObject.name}>")
                }
                if (fiders.trackFiders.any {
                        it.leftFider.coordinate < leftBlockCoordinate || it.rightFider.coordinate < leftBlockCoordinate ||
                                it.leftFider.coordinate > rightBlockCoordinate || it.rightFider.coordinate > rightBlockCoordinate
                    }) {
                    throw IllegalStateException("ПС <${dcObject.name}> - координата питающих линий не может пересекаться с соседними объектами")
                }
            }

            if (fiders is Connector && fiders.firstLine == fiders.secondLine) {
                throw IllegalStateException("Неверно установлены точки присоединения у ППС <${dcObject.name}>")
            }
        }
    }

    private fun checkAllParametersExist(station: DCStationDto) {
        val stationParameters = station.stationParameters!!

        if (stationParameters.skz == null || stationParameters.skz!! <= 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное значение Sкз")
        }
        if (stationParameters.stt == null) {
            throw IllegalStateException("У станции <${station.name}> не выбран Преобраз. тр-р")
        }
        if (stationParameters.sttCount == null || stationParameters.sttCount!! <= 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное количество Преобраз. тр-ров")
        }
        if (stationParameters.directUhh == null || stationParameters.directUhh!! <= 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное значение Uхх.в")
        }
        if (stationParameters.directRo == null || stationParameters.directRo!! <= 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное значение Rо.в")
        }
        if (stationParameters.directAmperage == null || stationParameters.directAmperage!! <= 0) {
            throw IllegalStateException("У станции <${station.name}> некорректное значение Iном.в")
        }
        if (stationParameters.spt != null &&
            (stationParameters.sptCount == null || stationParameters.sptCount!! <= 0)
        ) {
            throw IllegalStateException("У станции <${station.name}> некорректное количество Пониж. тр-ров")
        }
        if (stationParameters.srn == null) {
            stationParameters.srn = 0.0
        }
    }

    @Transactional
    override fun saveCk3(electricalSchema: ElectricalSchema) {
        repository.save(electricalSchema)
    }

}
