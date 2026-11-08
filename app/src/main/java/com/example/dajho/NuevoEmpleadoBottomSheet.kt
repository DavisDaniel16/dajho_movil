package com.example.dajho

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NuevoEmpleadoBottomSheet() : BottomSheetDialogFragment() {
    constructor(negocioId: Long) : this() {
        arguments = android.os.Bundle().apply {
            putString("negocioId", ScreenArgs.json.encodeToString(negocioId))
        }
    }
    private val negocioId: Long get() = ScreenArgs.json.decodeFromString(requireArguments().getString("negocioId")!!)


    companion object {
        const val REQUEST_EMPLEADOS_ACTUALIZADOS = "request_empleados_actualizados"
    }

    private lateinit var etNombreEmpleado: TextInputEditText
    private lateinit var etCorreoEmpleado: TextInputEditText
    private lateinit var etPasswordEmpleado: TextInputEditText
    private lateinit var etTopeDescuento: TextInputEditText
    private lateinit var btnGuardarEmpleado: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_nuevo_empleado, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etNombreEmpleado = view.findViewById(R.id.etNombreEmpleado)
        etCorreoEmpleado = view.findViewById(R.id.etCorreoEmpleado)
        etPasswordEmpleado = view.findViewById(R.id.etPasswordEmpleado)
        etTopeDescuento = view.findViewById(R.id.etTopeDescuento)
        btnGuardarEmpleado = view.findViewById(R.id.btnGuardarEmpleado)

        if (!requireArguments().containsKey("solicitud")) {
            requireArguments().putString("solicitud", java.util.UUID.randomUUID().toString())
        }
        btnGuardarEmpleado.setOnClickListener { guardarEmpleado() }
    }

    private fun guardarEmpleado() {
        val nombre = etNombreEmpleado.text.toString().trim()
        val correo = etCorreoEmpleado.text.toString().trim()
        val password = etPasswordEmpleado.text.toString()
        val topeTexto = etTopeDescuento.text.toString().trim()

        if (nombre.isEmpty()) {
            etNombreEmpleado.error = "Ingresa el nombre del empleado"
            return
        }
        if (correo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreoEmpleado.error = "Ingresa un correo válido"
            return
        }
        if (password.isBlank() || password.length < 8) {
            etPasswordEmpleado.error = "La contraseña debe tener al menos 8 caracteres"
            return
        }
        val tope = topeTexto.toDoubleOrNull()
        if (topeTexto.isEmpty() || tope == null || !tope.isFinite() || tope < 0.0) {
            etTopeDescuento.error = "Ingresa un tope válido (0 o más)"
            return
        }

        btnGuardarEmpleado.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val params = buildJsonObject {
                    put("p_solicitud", requireArguments().getString("solicitud"))
                    put("p_negocio", negocioId)
                    put("p_nombre", nombre)
                    put("p_correo", correo)
                    put("p_password", password)
                    put("p_tope_descuento_usd", tope)
                }
                val online = requireContext().hasInternetConnection()
                if (online) {
                    EmployeeService.create(params)
                } else {
                    SyncManager.enqueueAndTrySync(requireContext(), negocioId, "crear_empleado", params)
                }

                Toast.makeText(requireContext(), if (online) "Empleado registrado en el servidor" else getString(R.string.sync_employee_saved), Toast.LENGTH_SHORT).show()

                parentFragmentManager.setFragmentResult(
                    REQUEST_EMPLEADOS_ACTUALIZADOS,
                    Bundle.EMPTY
                )
                dismiss()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(
                    requireContext(),
                    e.userMessage("registrar el empleado"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnGuardarEmpleado.isEnabled = true
            }
        }
    }
}
