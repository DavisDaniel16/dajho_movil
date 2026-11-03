package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.Locale

class CarritoAdapter(
    private var listaItems: List<ItemCarritoDto> = emptyList(),
    private val onCantidadChanged: (ItemCarritoDto, Int) -> Unit,
    private val onItemRemoved: (ItemCarritoDto) -> Unit
) : RecyclerView.Adapter<CarritoAdapter.CarritoViewHolder>() {

    class CarritoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProductoNombre: TextView = view.findViewById(R.id.tvProductoNombre)
        val tvVarianteInfo: TextView = view.findViewById(R.id.tvVarianteInfo)
        val tvCantidad: TextView = view.findViewById(R.id.tvCantidad)
        val tvSubtotal: TextView = view.findViewById(R.id.tvSubtotal)
        val btnMenos: MaterialButton = view.findViewById(R.id.btnMenos)
        val btnMas: MaterialButton = view.findViewById(R.id.btnMas)
        val ivEliminar: ImageView = view.findViewById(R.id.ivEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarritoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carrito, parent, false)
        return CarritoViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarritoViewHolder, position: Int) {
        val item = listaItems[position]

        holder.tvProductoNombre.text = item.variante.producto?.nombre ?: "Producto"
        val talla = item.variante.talla ?: "-"
        val color = item.variante.color ?: "-"
        val precioFormateado = String.format(Locale.US, "%.2f", item.variante.precio_venta ?: 0.0)
        holder.tvVarianteInfo.text = "Talla: $talla | Color: $color ($$precioFormateado c/u)"
        holder.tvCantidad.text = item.cantidad.toString()
        holder.tvSubtotal.text = "$${String.format(Locale.US, "%.2f", item.subtotal)}"

        holder.btnMas.setOnClickListener {
            onCantidadChanged(item, item.cantidad + 1)
        }

        holder.btnMenos.setOnClickListener {
            if (item.cantidad > 1) {
                onCantidadChanged(item, item.cantidad - 1)
            } else {
                onItemRemoved(item)
            }
        }

        holder.ivEliminar.setOnClickListener {
            onItemRemoved(item)
        }
    }

    override fun getItemCount(): Int = listaItems.size

    fun updateList(newList: List<ItemCarritoDto>) {
        listaItems = newList
        notifyDataSetChanged()
    }
}
