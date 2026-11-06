package com.example.dajho.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dajho.db.entities.VentaPendienteEntity

@Dao
interface VentaPendienteDao {
    @Query("SELECT * FROM venta_pendiente WHERE negocio_id = :negocioId AND sincronizado = 0 ORDER BY id_local ASC")
    fun getVentasPendientes(negocioId: Long): List<VentaPendienteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertVentaPendiente(venta: VentaPendienteEntity): Long

    @Query("UPDATE venta_pendiente SET sincronizado = 1 WHERE id_local = :idLocal")
    fun marcarComoSincronizado(idLocal: Long)

    @Query("DELETE FROM venta_pendiente WHERE id_local = :idLocal")
    fun deleteVentaPendiente(idLocal: Long)
}
