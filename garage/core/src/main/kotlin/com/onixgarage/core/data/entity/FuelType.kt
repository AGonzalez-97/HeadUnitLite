package com.onixgarage.core.data.entity

enum class FuelType(val dbValue: String) {
    SUPER("SUPER"),
    PREMIUM("PREMIUM"),
    INFINIA("INFINIA");

    fun toDbValue(): String = dbValue

    companion object {
        fun fromString(value: String): FuelType =
            entries.firstOrNull { it.dbValue == value } ?: SUPER
    }
}
