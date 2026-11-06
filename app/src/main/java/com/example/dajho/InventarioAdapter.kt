package com.example.dajho

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class InventarioAdapter(
    private var listaVariantes: List<VarianteDto> = emptyList(),
    private val onItemClick: (VarianteDto) -> Unit = {}
) : RecyclerView.Adapter<InventarioAdapter.InventarioViewHolder>() {

    class InventarioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProductoNombre: TextView = view.findViewById(R.id.tvProductoNombre)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecio)
        val tvVarianteInfo: TextView = view.findViewById(R.id.tvVarianteInfo)
        val tvStock: TextView = view.findViewById(R.id.tvStock)
        val tvSku: TextView = view.findViewById(R.id.tvSku)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventario, parent, false)
        return InventarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventarioViewHolder, position: Int) {
        val item = listaVariantes[position]

        val nombreProd = item.producto?.nombre ?: "Producto"
        val talla = item.talla ?: ""
        val color = item.color ?: ""

        val detalleVariante = buildString {
            if (talla.isNotEmpty()) append("Talla $talla")
            if (color.isNotEmpty()) {
                if (isNotEmpty()) append(" / ")
                append(color)
            }
        }

        holder.tvProductoNombre.text = if (detalleVariante.isNotEmpty()) {
            "$nombreProd ($detalleVariante)"
        } else {
            nombreProd
        }

        holder.tvPrecio.text = "$${String.format(Locale.US, "%.2f", item.precio_venta ?: 0.0)}"

        holder.tvVarianteInfo.text =
            "Talla: ${item.talla?.takeIf { it.isNotBlank() } ?: "Sin talla"} | " +
                "Color: ${item.color?.takeIf { it.isNotBlank() } ?: "Sin color"}"
        val skuText = "SKU: ${item.sku ?: "Sin SKU"}"
        val barcodeText = if (!item.codigo_barras.isNullOrBlank()) "  ·  CB: ${item.codigo_barras}" else ""
        holder.tvSku.text = "$skuText$barcodeText"

        val stock = item.stock_actual ?: 0
        val stockMinimo = item.stock_minimo ?: 5
        holder.tvStock.text = "Stock: $stock"

        // Alerta de stock bajo (rojo si es menor o igual al stock mínimo configurado)
        if (stock <= stockMinimo) {
            holder.tvStock.setTextColor(Color.parseColor("#EF4444")) // Rojo
        } else {
            holder.tvStock.setTextColor(Color.parseColor("#10B981")) // Verde
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = listaVariantes.size

    fun updateList(newList: List<VarianteDto>) {
        listaVariantes = newList
        notifyDataSetChanged()
    }
}
