package com.example.dajho

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

data class FormField(val label: String, val value: String = "", val numeric: Boolean = false)

private val Int.dp: Int get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

/** Keep a failed form open; never report success before the server responds. */
fun Fragment.showForm(
    title: String,
    fields: List<FormField>,
    onSaved: () -> Unit = {},
    save: suspend (List<String>) -> Unit
) {
    val context = requireContext()
    val dialog = BottomSheetDialog(context)

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.dp, 16.dp, 20.dp, 24.dp)
        setBackgroundResource(R.drawable.bg_bottom_sheet)
    }

    // Asa del BottomSheet
    val handleBar = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(40.dp, 4.dp).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = 16.dp
        }
        setBackgroundColor(Color.parseColor("#CBD5E1"))
    }
    root.addView(handleBar)

    // Título del formulario
    val tvTitle = TextView(context).apply {
        text = title
        textSize = 20f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(Color.parseColor("#1E293B"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16.dp }
    }
    root.addView(tvTitle)

    val formContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    val inputs = fields.map { field ->
        val til = TextInputLayout(context, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox).apply {
            hint = field.label
            setBoxCornerRadii(12.dp.toFloat(), 12.dp.toFloat(), 12.dp.toFloat(), 12.dp.toFloat())
            setBoxStrokeColorStateList(ColorStateList.valueOf(Color.parseColor("#1E293B")))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
        }

        val et = TextInputEditText(til.context).apply {
            setText(field.value)
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 15f
            isSingleLine = true
            if (field.numeric) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }

        til.addView(et)
        formContent.addView(til)
        et
    }

    val scrollView = ScrollView(context).apply {
        isFillViewport = true
        addView(formContent)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    root.addView(scrollView)

    // Botón Guardar
    val btnSave = MaterialButton(context).apply {
        text = "GUARDAR"
        textSize = 15f
        setTypeface(null, android.graphics.Typeface.BOLD)
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        setTextColor(Color.WHITE)
        cornerRadius = 12.dp
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            52.dp
        ).apply { topMargin = 16.dp }
    }
    root.addView(btnSave)

    // Botón Cancelar
    val btnCancel = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = "CANCELAR"
        textSize = 14f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(Color.parseColor("#64748B"))
        strokeColor = ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
        cornerRadius = 12.dp
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            48.dp
        ).apply { topMargin = 8.dp }
        setOnClickListener { dialog.dismiss() }
    }
    root.addView(btnCancel)

    btnSave.setOnClickListener {
        btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                save(inputs.map { it.text.toString().trim() })
                dialog.dismiss()
                onSaved()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppFeedback.makeText(root, e.userMessage("guardar los datos del formulario"), AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
            } finally {
                btnSave.isEnabled = true
            }
        }
    }

    dialog.setContentView(root)
    dialog.setOnShowListener { dialogInterface ->
        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.let { sheet ->
            sheet.setBackgroundResource(android.R.color.transparent)
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }
    dialog.show()
}
