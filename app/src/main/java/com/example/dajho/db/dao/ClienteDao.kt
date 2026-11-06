package com.example.dajho.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dajho.db.entities.ClienteEntity

@Dao
interface ClienteDao {
    @Query("SELECT * FROM cliente WHERE negocio_id = :negocioId ORDER BY id DESC")
    fun getClientesByNegocio(negocioId: Long): List<ClienteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateClientes(clientes: List<ClienteEntity>)

    @Query("DELETE FROM cliente WHERE negocio_id = :negocioId")
    fun deleteClientesByNegocio(negocioId: Long)
}
