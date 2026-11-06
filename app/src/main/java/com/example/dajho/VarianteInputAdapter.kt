package com.example.dajho

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText

data class VarianteInputDto(
    var talla: String = "",
    var color: String = "",
    var sku: String = "",
    var codigoBarras: String = "",
    var precioCosto: String = "",
    var precioVenta: String = "",
    var precioMinimo: String = "",
    var stock: String = "",
    var stockMinimo: String = ""
)

class VarianteInputAdapter(
    private val listaVariantes: MutableList<VarianteInputDto>,
    private val getNombreProducto: () -> String = { "" },
    private val onDeleteClicked: (Int) -> Unit
) : RecyclerView.Adapter<VarianteInputAdapter.VarianteInputViewHolder>() {

    class VarianteInputViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvVarianteTitulo: TextView = view.findViewById(R.id.tvVarianteTitulo)
        val ivEliminarVariante: ImageView = view.findViewById(R.id.ivEliminarVariante)
        val etTalla: TextInputEditText = view.findViewById(R.id.etTalla)
        val etColor: TextInputEditText = view.findViewById(R.id.etColor)
        val tilSku: com.google.android.material.textfield.TextInputLayout = view.findViewById(R.id.tilSku)
        val etSku: TextInputEditText = view.findViewById(R.id.etSku)
        val etCodigoBarras: TextInputEditText = view.findViewById(R.id.etCodigoBarras)
        val etPrecioCosto: TextInputEditText = view.findViewById(R.id.etPrecioCosto)
        val etPrecioVenta: TextInputEditText = view.findViewById(R.id.etPrecioVenta)
        val etPrecioMinimo: TextInputEditText = view.findViewById(R.id.etPrecioMinimo)
        val etStock: TextInputEditText = view.findViewById(R.id.etStock)
        val etStockMinimo: TextInputEditText = view.findViewById(R.id.etStockMinimo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VarianteInputViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_variante_form, parent, false)
        return VarianteInputViewHolder(view)
    }

    override fun onBindViewHolder(holder: VarianteInputViewHolder, position: Int) {
        val variante = listaVariantes[position]
        holder.tvVarianteTitulo.text = "Variante #${position + 1}"

        holder.etTalla.setText(variante.talla)
        holder.etColor.setText(variante.color)
        holder.etSku.setText(variante.sku)
        holder.etCodigoBarras.setText(variante.codigoBarras)
        holder.etPrecioCosto.setText(variante.precioCosto)
        holder.etPrecioVenta.setText(variante.precioVenta)
        holder.etPrecioMinimo.setText(variante.precioMinimo)
        holder.etStock.setText(variante.stock)
        holder.etStockMinimo.setText(variante.stockMinimo)

        holder.etTalla.doAfterTextChanged { variante.talla = it.toString() }
        holder.etColor.doAfterTextChanged { variante.color = it.toString() }
        holder.etSku.doAfterTextChanged { variante.sku = it.toString() }
        holder.etCodigoBarras.doAfterTextChanged { variante.codigoBarras = it.toString() }
        holder.etPrecioCosto.doAfterTextChanged { variante.precioCosto = it.toString() }
        holder.etPrecioVenta.doAfterTextChanged { variante.precioVenta = it.toString() }
        holder.etPrecioMinimo.doAfterTextChanged { variante.precioMinimo = it.toString() }
        holder.etStock.doAfterTextChanged { variante.stock = it.toString() }
        holder.etStockMinimo.doAfterTextChanged { variante.stockMinimo = it.toString() }

        holder.tilSku.setEndIconOnClickListener {
            val generated = SkuGenerator.generate(getNombreProducto(), variante.talla, variante.color)
            variante.sku = generated
            holder.etSku.setText(generated)
        }

        if (listaVariantes.size > 1) {
            holder.ivEliminarVariante.visibility = View.VISIBLE
            holder.ivEliminarVariante.setOnClickListener {
                onDeleteClicked(holder.bindingAdapterPosition)
            }
        } else {
            holder.ivEliminarVariante.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = listaVariantes.size
}
