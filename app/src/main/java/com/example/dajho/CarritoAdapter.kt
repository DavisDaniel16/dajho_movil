package com.example.dajho

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

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
        val tvUnitPrice: TextView = view.findViewById(R.id.tvUnitPrice)
        val tvProductInitial: TextView = view.findViewById(R.id.tvProductInitial)
        val btnMenos: MaterialButton = view.findViewById(R.id.btnMenos)
        val btnMas: MaterialButton = view.findViewById(R.id.btnMas)
        val ivEliminar: ImageView = view.findViewById(R.id.ivEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CarritoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_carrito, parent, false))

    override fun onBindViewHolder(holder: CarritoViewHolder, position: Int) {
        val item = listaItems[position]
        val context = holder.itemView.context
        val name = item.variante.saleName(context)
        holder.tvProductoNombre.text = name
        holder.tvProductInitial.text = name.take(1).uppercase()
        holder.tvVarianteInfo.text = item.variante.saleDetails(context)
        holder.tvVarianteInfo.visibility = if (holder.tvVarianteInfo.text.isEmpty()) View.GONE else View.VISIBLE
        holder.tvCantidad.text = item.cantidad.toString()
        holder.tvUnitPrice.text = context.getString(R.string.sale_unit_price, saleMoney(item.variante.precio_venta ?: 0.0))
        holder.tvSubtotal.text = context.getString(R.string.sale_line_total, "$" + Money.total(listOf(item)).toPlainString())
        holder.btnMas.contentDescription = context.getString(R.string.sale_increase_item, name)
        holder.btnMenos.contentDescription = context.getString(
            if (item.cantidad == 1) R.string.sale_remove_item else R.string.sale_decrease_item, name
        )
        holder.ivEliminar.contentDescription = context.getString(R.string.sale_remove_item, name)
        holder.btnMas.isEnabled = item.cantidad < (item.variante.stock_actual ?: 0)
        holder.btnMas.setOnClickListener { onCantidadChanged(item, item.cantidad + 1) }
        holder.btnMenos.setOnClickListener {
            if (item.cantidad > 1) onCantidadChanged(item, item.cantidad - 1) else onItemRemoved(item)
        }
        holder.ivEliminar.setOnClickListener { onItemRemoved(item) }
    }

    override fun getItemCount() = listaItems.size

    fun updateList(newList: List<ItemCarritoDto>) {
        listaItems = newList
        notifyDataSetChanged()
    }
}

internal fun VarianteDto.saleName(context: Context): String =
    producto?.nombre?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sale_product_fallback)

internal fun VarianteDto.saleDetails(context: Context): String = listOfNotNull(
    talla?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.sale_size, it) },
    color?.takeIf { it.isNotBlank() }
).joinToString(" · ")

internal fun saleMoney(value: Double): String = "$" + Money.decimal(value).toPlainString()
