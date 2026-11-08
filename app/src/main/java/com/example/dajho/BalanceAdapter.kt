package com.example.dajho

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class BalanceAdapter(
    private var listaMovimientos: List<MovimientoDto> = emptyList(),
    private val onItemClick: (MovimientoDto) -> Unit
) : RecyclerView.Adapter<BalanceAdapter.BalanceViewHolder>() {

    class BalanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvConcepto: TextView = view.findViewById(R.id.tvConcepto)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvMonto: TextView = view.findViewById(R.id.tvMonto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BalanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_movimiento, parent, false)
        return BalanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BalanceViewHolder, position: Int) {
        val item = listaMovimientos[position]

        holder.tvConcepto.text = item.titulo
        holder.tvFecha.text = item.fecha

        val montoFormateado = String.format(Locale.US, "%.2f", item.monto)
        if (item.tipo == "ingreso") {
            holder.tvMonto.text = "+$$montoFormateado"
            holder.tvMonto.setTextColor(Color.parseColor("#10B981")) // Verde
        } else {
            holder.tvMonto.text = "-$$montoFormateado"
            holder.tvMonto.setTextColor(Color.parseColor("#EF4444")) // Rojo
        }

        holder.itemView.setOnClickListener(
            if (item.tipo == "ingreso" && item.venta_id != null) View.OnClickListener {
                onItemClick(item)
            } else null
        )
    }

    override fun getItemCount(): Int = listaMovimientos.size

    fun updateList(newList: List<MovimientoDto>) {
        listaMovimientos = newList
        notifyDataSetChanged()
    }
}
