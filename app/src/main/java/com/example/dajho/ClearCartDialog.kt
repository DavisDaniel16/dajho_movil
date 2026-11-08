package com.example.dajho

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Keeps the confirmation visible across a configuration change. */
class ClearCartDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sale_clear_title)
            .setMessage(R.string.sale_clear_message)
            .setNegativeButton(R.string.sale_cancel, null)
            .setPositiveButton(R.string.sale_clear) { _, _ ->
                parentFragmentManager.setFragmentResult(RESULT, Bundle())
            }.create()

    companion object {
        const val RESULT = "clear_sale_cart"
        const val TAG = "clear_sale_cart_dialog"
    }
}
