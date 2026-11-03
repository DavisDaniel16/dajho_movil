package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class DeudaDetalleAdapter(
    private val listaDeudas: List<DeudaDto>
) : RecyclerView.Adapter<DeudaDetalleAdapter.DeudaDetalleViewHolder>() {

    class DeudaDetalleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFechaCompra: TextView = view.findViewById(R.id.tvFechaCompra)
        val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        val tvMonto: TextView = view.findViewById(R.id.tvMonto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeudaDetalleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deuda_detalle, parent, false)
        return DeudaDetalleViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeudaDetalleViewHolder, position: Int) {
        val item = listaDeudas[position]

        // Extraer los nombres de productos y cantidades compradas
        val detalles = item.venta?.detalle_venta ?: emptyList()
        val resumenProductos = if (detalles.isNotEmpty()) {
            detalles.joinToString(", ") { detalle ->
                val nombreProducto = detalle.variante?.producto?.nombre ?: "Producto"
                val cant = detalle.cantidad ?: 1
                "$nombreProducto (x$cant)"
            }
        } else {
            "Compra de productos"
        }

        val fechaRaw = item.venta?.fecha_dispositivo
        val fechaStr = if (!fechaRaw.isNullOrEmpty()) fechaRaw.take(10) else ""

        holder.tvFechaCompra.text = if (fechaStr.isNotEmpty()) "$resumenProductos • $fechaStr" else resumenProductos
        holder.tvEstado.text = "Estado: ${item.estado?.replaceFirstChar { it.uppercase() } ?: "Pendiente"}"

        val saldo = item.saldo ?: item.monto_original ?: 0.0
        holder.tvMonto.text = "$${String.format(Locale.US, "%.2f", saldo)}"
    }

    override fun getItemCount(): Int = listaDeudas.size
}
