package com.example.dajho.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cliente")
data class ClienteEntity(
    @PrimaryKey val id: Long,
    val negocio_id: Long,
    val nombre: String,
    val cedula: String? = null,
    val telefono: String? = null,
    val correo: String? = null
)
