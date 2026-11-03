package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class DeudaAdapter(
    private var listaDeudas: List<DeudaClienteAgrupadaDto> = emptyList(),
    private val onItemClick: (DeudaClienteAgrupadaDto) -> Unit = {}
) : RecyclerView.Adapter<DeudaAdapter.DeudaViewHolder>() {

    class DeudaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvClienteNombre: TextView = view.findViewById(R.id.tvClienteNombre)
        val tvClienteTelefono: TextView = view.findViewById(R.id.tvClienteTelefono)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvSaldo: TextView = view.findViewById(R.id.tvSaldo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeudaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deuda, parent, false)
        return DeudaViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeudaViewHolder, position: Int) {
        val item = listaDeudas[position]

        holder.tvClienteNombre.text = item.clienteNombre
        holder.tvClienteTelefono.text = "Tel: ${item.clienteTelefono}"
        holder.tvFecha.text = "${item.cantidadDeudas} fiado(s) pendiente(s)"

        val totalFormat = String.format(Locale.US, "%.2f", item.saldoTotal)
        holder.tvSaldo.text = "$$totalFormat"

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = listaDeudas.size

    fun updateList(newList: List<DeudaClienteAgrupadaDto>) {
        listaDeudas = newList
        notifyDataSetChanged()
    }
}
