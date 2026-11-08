package com.example.dajho

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class EmpleadosFragment : Fragment() {

    private lateinit var adapter: EmpleadosAdapter
    private lateinit var progressBar: ProgressBar
    private var negocioIdActual: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_empleados, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if ((activity as? HomeActivity)?.esPropietario() != true) {
            (activity as? HomeActivity)?.replaceFragment(HomeFragment())
            return
        }

        progressBar = view.findViewById(R.id.progressEmpleados)
        adapter = EmpleadosAdapter { empleado ->
            if (empleado.usuario_id == null) return@EmpleadosAdapter
            showForm("Editar ${empleado.nombre}", listOf(
                FormField("Activo (sí/no)", if (empleado.activo == true) "sí" else "no"),
                FormField("Tope de descuento USD", empleado.tope_descuento_usd?.toString() ?: "0", true)
            ), { cargarEmpleados() }) { values ->
                require(values[0].lowercase() in listOf("si", "sí", "no")) { "Escribe sí o no" }
                val tope = Money.amount(values[1]); require(tope.signum() >= 0)
                SupabaseModule.client.postgrest.rpc("editar_empleado", buildJsonObject {
                    put("p_negocio", requireNotNull(negocioIdActual))
                    put("p_usuario", empleado.usuario_id)
                    put("p_activo", values[0].lowercase() != "no")
                    put("p_tope", Money.json(tope))
                })
            }
        }

        view.findViewById<RecyclerView>(R.id.rvEmpleados).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EmpleadosFragment.adapter
        }

        childFragmentManager.setFragmentResultListener(
            NuevoEmpleadoBottomSheet.REQUEST_EMPLEADOS_ACTUALIZADOS,
            viewLifecycleOwner
        ) { _, _ ->
            cargarEmpleados()
        }

        view.findViewById<FloatingActionButton>(R.id.fabAgregarEmpleado)
            .setOnClickListener {
                if ((activity as? HomeActivity)?.esPropietario() != true) return@setOnClickListener

                val negocioId = negocioIdActual
                if (negocioId == null) {
                    Toast.makeText(requireContext(), "Cargando negocio...", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                NuevoEmpleadoBottomSheet(negocioId)
                    .show(childFragmentManager, "NuevoEmpleadoBottomSheet")
            }

        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ -> cargarEmpleados() }
        cargarEmpleados()
    }

    private fun mostrarEmpleados(empleados: List<EmpleadoDto>) {
        val pendientes = emptyList<EmpleadoDto>()
        adapter.updateList(pendientes + empleados)
        view?.findViewById<View>(R.id.tvEstadoVacio)?.visibility =
            if (empleados.isEmpty() && pendientes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun cargarEmpleados() {
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userId = SupabaseModule.client.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("No hay sesión activa")

                val membresia = BusinessSession.current()

                val negocioId = membresia?.negocio_id
                if (membresia?.rol != "propietario" || negocioId == null) {
                    Toast.makeText(
                        requireContext(),
                        "No tienes acceso al módulo de empleados",
                        Toast.LENGTH_SHORT
                    ).show()
                    (activity as? HomeActivity)?.replaceFragment(HomeFragment())
                    return@launch
                }

                negocioIdActual = negocioId
                val db = DajhoDatabase.getInstance(requireContext())
                val empleados = if (requireContext().hasInternetConnection()) {
                    try {
                        val list = SupabaseModule.client.postgrest.rpc(
                            "obtener_empleados",
                            buildJsonObject { put("p_negocio_id", negocioId) }
                        ).decodeList<EmpleadoDto>()
                        db.saveGestionItems(negocioId, "empleado", ScreenArgs.json.encodeToString(list))
                        list
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        val localStr = db.getGestionItems(negocioId, "empleado")
                        if (localStr != null) {
                            ScreenArgs.json.decodeFromString<List<EmpleadoDto>>(localStr)
                        } else emptyList()
                    }
                } else {
                    val localStr = db.getGestionItems(negocioId, "empleado")
                    if (localStr != null) {
                        ScreenArgs.json.decodeFromString<List<EmpleadoDto>>(localStr)
                    } else emptyList()
                }

                mostrarEmpleados(empleados)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("EmpleadosFragment", "Error al cargar empleados" + ": " + e.userMessage())
                Toast.makeText(
                    requireContext(),
                    e.userMessage("cargar la lista de empleados"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}

private class EmpleadosAdapter(
    private val onItemClick: (EmpleadoDto) -> Unit
) : RecyclerView.Adapter<EmpleadosAdapter.ViewHolder>() {

    private var empleados: List<EmpleadoDto> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombre: TextView = view.findViewById(R.id.tvNombreEmpleado)
        val correo: TextView = view.findViewById(R.id.tvCorreoEmpleado)
        val estado: TextView = view.findViewById(R.id.tvEstadoEmpleado)
        val editar: ImageView = view.findViewById(R.id.ivEditarEmpleado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_empleado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val empleado = empleados[position]

        holder.nombre.text = empleado.nombre
            ?.takeIf { it.isNotBlank() } ?: "Empleado sin nombre"
        holder.correo.text = empleado.correo ?: "Correo pendiente"

        holder.estado.text = when (empleado.activo) {
            true -> "Activo"
            false -> "Inactivo"
            null -> "Estado desconocido"
        }
        holder.estado.setTextColor(
            Color.parseColor(if (empleado.activo == true) "#059669" else "#64748B")
        )

        holder.itemView.setOnClickListener { onItemClick(empleado) }
        holder.editar.setOnClickListener { onItemClick(empleado) }
    }

    override fun getItemCount(): Int = empleados.size

    fun updateList(nuevosEmpleados: List<EmpleadoDto>) {
        empleados = nuevosEmpleados
        notifyDataSetChanged()
    }
}
