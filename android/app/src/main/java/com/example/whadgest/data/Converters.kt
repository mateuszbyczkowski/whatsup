package com.example.whadgest.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converters for Room database
 * Handles conversion between complex types and primitive types that Room can persist
 */
class Converters {

    /**
     * Convert timestamp to Date
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Convert Date to timestamp
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    /**
     * Convert string list to single string (comma-separated)
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(",")
    }

    /**
     * Convert comma-separated string to string list
     */
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split(",")?.filter { it.isNotBlank() }
    }

    /**
     * Convert map to JSON string
     */
    @TypeConverter
    fun fromMap(value: Map<String, String>?): String? {
        return value?.let { map ->
            map.entries.joinToString(";") { "${it.key}:${it.value}" }
        }
    }

    /**
     * Convert JSON string to map
     */
    @TypeConverter
    fun toMap(value: String?): Map<String, String>? {
        return value?.let { str ->
            if (str.isBlank()) return emptyMap()
            str.split(";")
                .filter { it.contains(":") }
                .associate {
                    val parts = it.split(":", limit = 2)
                    parts[0] to parts[1]
                }
        }
    }
}
