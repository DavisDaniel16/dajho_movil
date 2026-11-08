package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class ProductoAdapter(
    private var listaVariantes: List<VarianteDto> = emptyList(),
    private val onItemClick: (VarianteDto) -> Unit
) : RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder>() {

    private var cartQuantities: Map<Long?, Int> = emptyMap()

    class ProductoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProductoNombre: TextView = view.findViewById(R.id.tvProductoNombre)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecio)
        val tvVarianteInfo: TextView = view.findViewById(R.id.tvVarianteInfo)
        val tvStock: TextView = view.findViewById(R.id.tvStock)
        val tvSku: TextView = view.findViewById(R.id.tvSku)
        val tvInCart: TextView = view.findViewById(R.id.tvInCart)
        val tvProductInitial: TextView = view.findViewById(R.id.tvProductInitial)
        val btnAgregar: MaterialButton = view.findViewById(R.id.btnAgregar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ProductoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_producto, parent, false))

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        val item = listaVariantes[position]
        val context = holder.itemView.context
        val name = item.saleName(context)
        holder.tvProductoNombre.text = name
        holder.tvProductInitial.text = name.take(1).uppercase()
        holder.tvPrecio.text = saleMoney(item.precio_venta ?: 0.0)
        holder.tvVarianteInfo.text = item.saleDetails(context)
        holder.tvVarianteInfo.visibility = if (holder.tvVarianteInfo.text.isEmpty()) View.GONE else View.VISIBLE
        holder.tvSku.text = item.sku?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.sale_sku, it) }
        holder.tvSku.visibility = if (holder.tvSku.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        val stock = item.stock_actual ?: 0
        val quantity = cartQuantities[item.id] ?: 0
        holder.tvStock.text = if (stock > 0) context.getString(R.string.sale_stock, stock)
            else context.getString(R.string.sale_out_of_stock)
        holder.tvStock.setTextColor(androidx.core.content.ContextCompat.getColor(
            context, if (stock <= 0) R.color.feedback_error_fg else R.color.text_secondary
        ))
        holder.tvInCart.text = context.getString(R.string.sale_in_cart, quantity)
        holder.tvInCart.visibility = if (quantity > 0) View.VISIBLE else View.GONE
        holder.btnAgregar.isEnabled = stock > quantity
        holder.btnAgregar.contentDescription = context.getString(R.string.sale_add_item, name)
        holder.btnAgregar.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = listaVariantes.size

    fun updateList(newList: List<VarianteDto>) {
        listaVariantes = newList
        notifyDataSetChanged()
    }

    fun updateCart(items: List<ItemCarritoDto>) {
        cartQuantities = items.associate { it.variante.id to it.cantidad }
        notifyDataSetChanged()
    }
}
