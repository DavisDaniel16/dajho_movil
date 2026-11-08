package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ProductoAdapter(
    private var listaVariantes: List<VarianteDto> = emptyList(),
    private val onItemClick: (VarianteDto) -> Unit
) : RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder>() {

    class ProductoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProductoNombre: TextView = view.findViewById(R.id.tvProductoNombre)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecio)
        val tvVarianteInfo: TextView = view.findViewById(R.id.tvVarianteInfo)
        val tvStock: TextView = view.findViewById(R.id.tvStock)
        val tvSku: TextView = view.findViewById(R.id.tvSku)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto, parent, false)
        return ProductoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
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

        holder.tvVarianteInfo.text = "Talla: ${talla.ifEmpty { "-" }} | Color: ${color.ifEmpty { "-" }}"
        val stock = item.stock_actual ?: 0
        val stockMinimo = item.stock_minimo ?: 5
        holder.tvStock.text = "Stock: $stock"
        if (stock <= stockMinimo) {
            holder.tvStock.setTextColor(android.graphics.Color.parseColor("#EF4444")) // Rojo (Alerta stock bajo)
        } else {
            holder.tvStock.setTextColor(android.graphics.Color.parseColor("#10B981")) // Verde
        }
        holder.tvSku.text = "SKU: ${item.sku ?: "Sin SKU"}"

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
