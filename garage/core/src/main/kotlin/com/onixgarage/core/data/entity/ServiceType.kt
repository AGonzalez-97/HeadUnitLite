package com.onixgarage.core.data.entity

enum class ServiceType(val dbValue: String) {
    BELT("BELT"),
    OIL("OIL"),
    OIL_FILTER("OIL_FILTER"),
    AIR_FILTER("AIR_FILTER"),
    FUEL_FILTER("FUEL_FILTER"),
    SPARK_PLUGS("SPARK_PLUGS"),
    BRAKE_PADS("BRAKE_PADS"),
    BRAKE_FLUID("BRAKE_FLUID"),
    COOLANT("COOLANT"),
    ALIGNMENT_BALANCE("ALIGNMENT_BALANCE");

    fun toDbValue(): String = dbValue

    companion object {
        fun fromString(value: String): ServiceType =
            entries.firstOrNull { it.dbValue == value } ?: OIL
    }
}
