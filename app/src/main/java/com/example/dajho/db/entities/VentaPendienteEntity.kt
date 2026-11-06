package com.example.dajho.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "venta_pendiente")
data class VentaPendienteEntity(
    @PrimaryKey(autoGenerate = true) val id_local: Long = 0,
    val negocio_id: Long,
    val payload_json: String,
    val fecha_creacion: String,
    val sincronizado: Boolean = false
)
