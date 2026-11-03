package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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

        holder.tvProductoNombre.text = item.producto?.nombre ?: "Producto"
        holder.tvPrecio.text = "$${String.format("%.2f", item.precio_venta ?: 0.0)}"

        val talla = item.talla ?: "-"
        val color = item.color ?: "-"
        holder.tvVarianteInfo.text = "Talla: $talla | Color: $color"
        holder.tvStock.text = "Stock: ${item.stock_actual ?: 0}"
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
