package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClienteAdapter(
    private var listaClientes: List<ClienteDto> = emptyList(),
    private val onItemClick: (ClienteDto) -> Unit
) : RecyclerView.Adapter<ClienteAdapter.ClienteViewHolder>() {

    class ClienteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvTelefono: TextView = view.findViewById(R.id.tvTelefono)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cliente, parent, false)
        return ClienteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        val cliente = listaClientes[position]

        holder.tvNombre.text = cliente.nombre ?: "Sin nombre"
        holder.tvTelefono.text = "Teléfono: ${cliente.telefono ?: "Sin teléfono"}"

        holder.itemView.setOnClickListener {
            onItemClick(cliente)
        }
    }

    override fun getItemCount(): Int = listaClientes.size

    fun updateList(newList: List<ClienteDto>) {
        listaClientes = newList
        notifyDataSetChanged()
    }
}
