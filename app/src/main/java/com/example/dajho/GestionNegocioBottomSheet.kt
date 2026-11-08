package com.example.dajho

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

data class ModuloGestionItem(
    val key: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val bgColorHex: String,
    val soloPropietario: Boolean = false
)

class GestionNegocioBottomSheet() : BottomSheetDialogFragment() {
    constructor(esPropietario: Boolean) : this() {
        arguments = Bundle().apply { putBoolean("es_propietario", esPropietario) }
    }

    companion object {
        const val RESULTADO_MODULO = "gestion_modulo_seleccionado"
        const val CLAVE_MODULO = "modulo"
    }

    private val esPropietario: Boolean
        get() = arguments?.getBoolean("es_propietario", false) ?: false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_gestion_negocio, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = view.findViewById<LinearLayout>(R.id.layoutModulosContainer) ?: return

        val allModules = listOf(
            ModuloGestionItem("caja", "Caja", "Apertura, cierre y control de efectivo", R.drawable.ic_balance, "#DCFCE7"),
            ModuloGestionItem("cliente", "Clientes", "Directorio, contacto y datos de clientes", R.drawable.ic_clients, "#DBEAFE"),
            ModuloGestionItem("planes", "Planes y pagos", "Suscripción activa, límites y comprobantes", R.drawable.ic_shopping_bag, "#E0F2FE"),
            ModuloGestionItem("proveedor", "Proveedores", "Gestión y contactos de proveedores", R.drawable.ic_providers, "#E0E7FF", soloPropietario = true),
            ModuloGestionItem("compra", "Compras", "Registro e historial de compras de insumos", R.drawable.ic_cart, "#FFEDD5", soloPropietario = true),
            ModuloGestionItem("categoria", "Categorías", "Clasificación y catálogo de categorías", R.drawable.ic_catalog, "#F3E8FF", soloPropietario = true),
            ModuloGestionItem("temporada", "Temporadas", "Organización de productos por temporada", R.drawable.ic_inventory, "#FCE7F3", soloPropietario = true),
            ModuloGestionItem("ventas", "Historial de ventas", "Listado completo y anulación de ventas", R.drawable.ic_sale, "#DCFCE7", soloPropietario = true),
            ModuloGestionItem("movimiento_inventario", "Movimientos de inventario", "Auditoría de entradas, salidas y stock", R.drawable.ic_inventory, "#E0E7FF", soloPropietario = true),
            ModuloGestionItem("configuracion", "Configuración y perfil", "Ajustes de cuenta, perfil del negocio y preferencias", R.drawable.ic_settings, "#F1F5F9"),
            ModuloGestionItem("acceso_soporte", "Acceso de soporte", "Autorización y permisos para soporte técnico", R.drawable.ic_privacy, "#E0F2FE", soloPropietario = true)
        )

        val availableModules = allModules.filter { !it.soloPropietario || esPropietario }

        val inflater = LayoutInflater.from(requireContext())
        availableModules.forEach { item ->
            val itemView = inflater.inflate(R.layout.item_modulo_gestion, container, false)
            val card = itemView.findViewById<MaterialCardView>(R.id.cardModulo)
            val iconContainer = itemView.findViewById<MaterialCardView>(R.id.cardIconContainer)
            val ivIcon = itemView.findViewById<ImageView>(R.id.ivIconoModulo)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvTituloModulo)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvDescripcionModulo)

            tvTitle.text = item.title
            tvDesc.text = item.description
            ivIcon.setImageResource(item.iconRes)
            try {
                iconContainer.setCardBackgroundColor(Color.parseColor(item.bgColorHex))
            } catch (_: Exception) {}

            card.setOnClickListener {
                dismiss()
                parentFragmentManager.setFragmentResult(
                    RESULTADO_MODULO,
                    Bundle().apply { putString(CLAVE_MODULO, item.key) }
                )
            }

            container.addView(itemView)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }
}
