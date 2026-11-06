package com.example.dajho.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "producto")
data class ProductoEntity(
    @PrimaryKey val id: Long,
    val negocio_id: Long,
    val nombre: String,
    val marca: String? = null,
    val categoria_id: Long? = null,
    val temporada_id: Long? = null,
    val activo: Boolean = true
)
