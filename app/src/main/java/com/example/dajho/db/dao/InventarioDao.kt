package com.example.dajho.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dajho.db.entities.ProductoEntity
import com.example.dajho.db.entities.VarianteEntity

@Dao
interface InventarioDao {
    @Query("SELECT * FROM variante WHERE negocio_id = :negocioId ORDER BY id DESC")
    fun getVariantesByNegocio(negocioId: Long): List<VarianteEntity>

    @Query("""
        SELECT * FROM variante 
        WHERE negocio_id = :negocioId 
        AND (
            producto_nombre LIKE '%' || :query || '%' OR 
            sku LIKE '%' || :query || '%' OR 
            codigo_barras LIKE '%' || :query || '%' OR 
            talla LIKE '%' || :query || '%' OR 
            color LIKE '%' || :query || '%'
        )
        ORDER BY id DESC
    """)
    fun buscarVariantes(negocioId: Long, query: String): List<VarianteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateVariantes(variantes: List<VarianteEntity>)

    @Query("DELETE FROM variante WHERE negocio_id = :negocioId")
    fun deleteVariantesByNegocio(negocioId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateProductos(productos: List<ProductoEntity>)
}
