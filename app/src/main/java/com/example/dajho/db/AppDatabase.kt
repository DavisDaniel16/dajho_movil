package com.example.dajho.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.dajho.db.dao.ClienteDao
import com.example.dajho.db.dao.InventarioDao
import com.example.dajho.db.dao.VentaPendienteDao
import com.example.dajho.db.entities.ClienteEntity
import com.example.dajho.db.entities.ProductoEntity
import com.example.dajho.db.entities.VarianteEntity
import com.example.dajho.db.entities.VentaPendienteEntity

@Database(
    entities = [
        ProductoEntity::class,
        VarianteEntity::class,
        ClienteEntity::class,
        VentaPendienteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun inventarioDao(): InventarioDao
    abstract fun clienteDao(): ClienteDao
    abstract fun ventaPendienteDao(): VentaPendienteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dajho_local_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
