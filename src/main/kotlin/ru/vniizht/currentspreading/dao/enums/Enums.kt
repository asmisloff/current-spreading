package ru.vniizht.currentspreading.dao.enums

enum class LocomotiveType(val russianName: String, val brakeForce: Int) {
    FREIGHT_LOCOMOTIVE("Электровоз грузовой", 260),
    PASSENGER_LOCOMOTIVE("Электровоз пассажирский", 450),
    ELECTRIC_TRAIN("Электропоезд", 600)
}

val russianNameToLocomotiveType = LocomotiveType.values().associateBy { it.russianName }

fun getLocomotiveTypeByRussianName(russianName: String): LocomotiveType =
    russianNameToLocomotiveType[russianName]
        ?: throw IllegalArgumentException("Unknown LocomotiveType with russian name: $russianName")


enum class LocomotiveCurrent(val russianName: String, val nominal: Int) {
    DIRECT_CURRENT("Постоянный ток", 3000),
    ALTERNATING_CURRENT("Переменный ток", 25000)
}

val russianNameToLocomotiveCurrent = LocomotiveCurrent.values().associateBy { it.russianName }

fun getLocomotiveCurrentByRussianName(russianName: String): LocomotiveCurrent =
    russianNameToLocomotiveCurrent[russianName]
        ?: throw IllegalArgumentException("Unknown LocomotiveCurrent with russian name: $russianName")


enum class NumberOfAxles(val number: Int) {
    FOUR_AXLES(4),
    SIX_AXLES(6),
    EIGHT_AXLES(8)
}

val numberToNumberOfAxles = NumberOfAxles.values().associateBy { it.number }

fun getNumberOfAxlesByNumber(number: Int): NumberOfAxles =
    numberToNumberOfAxles[number]
        ?: throw IllegalArgumentException("Unknown NumberOfAxles with number: $number")


enum class TrackType(val russianName: String) {

    CONTINUOUS("бесстыковой"),
    COMPONENT("звеньевой");

    companion object {

        fun fromRussianName(rn: String): TrackType {
            return when (rn.lowercase()) {
                "бесстыковой" -> CONTINUOUS
                "звеньевой" -> COMPONENT
                else -> throw IllegalArgumentException("Не удалось идентифицировать тип пути: $rn")
            }
        }

    }

}

val russianNamesToTrackType = TrackType.values().associateBy { it.russianName }

fun getTrackTypeOfRussianNames(russianName: String): TrackType =
    russianNamesToTrackType[russianName]
        ?: throw IllegalArgumentException("Unknown TrackType with russianName: $russianName")


enum class BrakeType(val russianName: String) {
    IRON("чугунный"),
    COMPOSITE("композитный")
}

val russianNamesToBrakeType = BrakeType.values().associateBy { it.russianName }

fun getBrakeTypeOfRussianNames(russianName: String): BrakeType =
    russianNamesToBrakeType[russianName]
        ?: throw IllegalArgumentException("Unknown BrakeType with russianName: $russianName")



enum class CompensationDeviceType(val russianName: String) {
    ABSENT(""),
    THROTTLING("плавное"),
    STEPPED("ступенчатое")
}

val russianNamesToCompensationDeviceType = CompensationDeviceType.values().associateBy { it.russianName }

fun getCompensationDeviceTypeOfRussianNames(russianName: String): CompensationDeviceType =
    russianNamesToCompensationDeviceType[russianName]
        ?: throw IllegalArgumentException("Unknown CompensationDeviceType with russianName: $russianName")



enum class WireAndRailType(val russianName: String) {
    FIDER("Фидер/Несущий"),
    CONTACT("Контактный"),
    POWER("Усиливающий"),
    SHIELD("Экранирующий"),
    SUPPLY("Питающий"),
    RAIL("Рельс")
}

val russianNamesToWireAndRailType = WireAndRailType.values().associateBy { it.russianName }

fun getWireAndRailTypeOfRussianNames(russianName: String): WireAndRailType =
    russianNamesToWireAndRailType[russianName]
        ?: throw IllegalArgumentException("Unknown WireAndRailType with russianName: $russianName")


enum class SchemaType(val russianName: String) {
    DC("3 кВ"),
    AC("25 кВ"),
    ACD("2x25 кВ")
}

enum class CapacityType(val russianName: String) {
    DAILY("тип А"),
    INTERVAL("тип Б"),
    PARALLEL_SCHEDULE("параллельный график")
}
