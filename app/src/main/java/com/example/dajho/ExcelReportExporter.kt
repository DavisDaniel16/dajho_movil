package com.example.dajho

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ExcelReportExporter {

    fun generateSalesReportXlsx(
        context: Context,
        title: String,
        businessName: String,
        fecha: String?,
        detalles: List<DetalleVentaBalanceDto>
    ): File {
        val fileName = "Reporte_${title.replace(Regex("[^a-zA-Z0-9_]"), "_")}.xlsx"
        val file = File(context.cacheDir, fileName)

        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<?mso-application progid=\"Excel.Sheet\"?>")
        sb.appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"")
        sb.appendLine(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"")
        sb.appendLine(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"")
        sb.appendLine(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"")
        sb.appendLine(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">")
        sb.appendLine(" <Styles>")
        sb.appendLine("  <Style ss:ID=\"Header\">")
        sb.appendLine("   <Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>")
        sb.appendLine("   <Interior ss:Color=\"#1E293B\" ss:Pattern=\"Solid\"/>")
        sb.appendLine("  </Style>")
        sb.appendLine("  <Style ss:ID=\"Bold\">")
        sb.appendLine("   <Font ss:Bold=\"1\" ss:Color=\"#000000\"/>")
        sb.appendLine("  </Style>")
        sb.appendLine(" </Styles>")

        sb.appendLine(" <Worksheet ss:Name=\"Reporte de Venta\">")
        sb.appendLine("  <Table>")

        // Metadata Header
        sb.appendLine("   <Row>")
        sb.appendLine("    <Cell ss:StyleID=\"Bold\"><Data ss:Type=\"String\">Negocio:</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(businessName)}</Data></Cell>")
        sb.appendLine("   </Row>")
        sb.appendLine("   <Row>")
        sb.appendLine("    <Cell ss:StyleID=\"Bold\"><Data ss:Type=\"String\">Comprobante / Título:</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(title)}</Data></Cell>")
        sb.appendLine("   </Row>")
        if (!fecha.isNullOrBlank()) {
            sb.appendLine("   <Row>")
            sb.appendLine("    <Cell ss:StyleID=\"Bold\"><Data ss:Type=\"String\">Fecha:</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(fecha)}</Data></Cell>")
            sb.appendLine("   </Row>")
        }
        sb.appendLine("   <Row></Row>")

        // Table Header
        sb.appendLine("   <Row ss:StyleID=\"Header\">")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Cantidad</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Producto</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">SKU</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Talla</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Color</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Precio Unitario ($)</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Subtotal ($)</Data></Cell>")
        sb.appendLine("   </Row>")

        var totalCalculado = 0.0

        if (detalles.isEmpty()) {
            sb.appendLine("   <Row>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">0</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">Sin productos registrados</Data></Cell>")
            sb.appendLine("   </Row>")
        } else {
            detalles.forEach { item ->
                val prodNombre = item.variante?.producto?.nombre ?: "Producto"
                val cant = item.cantidad ?: 1
                val pu = item.precio_unitario ?: 0.0
                val subtotal = cant * pu
                totalCalculado += subtotal

                val talla = item.variante?.talla.orEmpty()
                val color = item.variante?.color.orEmpty()
                val sku = item.variante?.sku.orEmpty()

                sb.appendLine("   <Row>")
                sb.appendLine("    <Cell><Data ss:Type=\"Number\">$cant</Data></Cell>")
                sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(prodNombre)}</Data></Cell>")
                sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(sku)}</Data></Cell>")
                sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(talla)}</Data></Cell>")
                sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(color)}</Data></Cell>")
                sb.appendLine("    <Cell><Data ss:Type=\"Number\">${String.format(Locale.US, "%.2f", pu)}</Data></Cell>")
                sb.appendLine("    <Cell><Data ss:Type=\"Number\">${String.format(Locale.US, "%.2f", subtotal)}</Data></Cell>")
                sb.appendLine("   </Row>")
            }
        }

        sb.appendLine("   <Row></Row>")
        sb.appendLine("   <Row>")
        sb.appendLine("    <Cell ss:StyleID=\"Bold\"><Data ss:Type=\"String\">TOTAL</Data></Cell>")
        sb.appendLine("    <Cell></Cell><Cell></Cell><Cell></Cell><Cell></Cell><Cell></Cell>")
        sb.appendLine("    <Cell ss:StyleID=\"Bold\"><Data ss:Type=\"Number\">${String.format(Locale.US, "%.2f", totalCalculado)}</Data></Cell>")
        sb.appendLine("   </Row>")

        sb.appendLine("  </Table>")
        sb.appendLine(" </Worksheet>")
        sb.appendLine("</Workbook>")

        FileOutputStream(file).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        return file
    }

    fun generateCatalogReportXlsx(
        context: Context,
        businessName: String,
        variantes: List<VarianteDto>
    ): File {
        val fileName = "Reporte_Catalogo_${businessName.replace(Regex("[^a-zA-Z0-9_]"), "_")}.xlsx"
        val file = File(context.cacheDir, fileName)

        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<?mso-application progid=\"Excel.Sheet\"?>")
        sb.appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"")
        sb.appendLine(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"")
        sb.appendLine(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"")
        sb.appendLine(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"")
        sb.appendLine(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">")
        sb.appendLine(" <Styles>")
        sb.appendLine("  <Style ss:ID=\"Header\">")
        sb.appendLine("   <Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>")
        sb.appendLine("   <Interior ss:Color=\"#1E293B\" ss:Pattern=\"Solid\"/>")
        sb.appendLine("  </Style>")
        sb.appendLine("  <Style ss:ID=\"Bold\">")
        sb.appendLine("   <Font ss:Bold=\"1\" ss:Color=\"#000000\"/>")
        sb.appendLine("  </Style>")
        sb.appendLine(" </Styles>")

        sb.appendLine(" <Worksheet ss:Name=\"Catálogo de Productos\">")
        sb.appendLine("  <Table>")

        sb.appendLine("   <Row>")
        sb.appendLine("    <Cell ss:StyleID=\"Bold\"><Data ss:Type=\"String\">Negocio:</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(businessName)}</Data></Cell>")
        sb.appendLine("   </Row>")
        sb.appendLine("   <Row></Row>")

        sb.appendLine("   <Row ss:StyleID=\"Header\">")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Producto</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">SKU</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Código de Barras</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Talla</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Color</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Precio Venta ($)</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Precio Costo ($)</Data></Cell>")
        sb.appendLine("    <Cell><Data ss:Type=\"String\">Stock Actual</Data></Cell>")
        sb.appendLine("   </Row>")

        variantes.forEach { v ->
            val prod = v.producto?.nombre ?: "Producto"
            val sku = v.sku.orEmpty()
            val cb = v.codigo_barras.orEmpty()
            val talla = v.talla.orEmpty()
            val color = v.color.orEmpty()
            val pv = v.precio_venta ?: 0.0
            val pc = v.precio_costo ?: 0.0
            val stock = v.stock_actual ?: 0

            sb.appendLine("   <Row>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(prod)}</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(sku)}</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(cb)}</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(talla)}</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"String\">${escapeXml(color)}</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"Number\">${String.format(Locale.US, "%.2f", pv)}</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"Number\">${String.format(Locale.US, "%.2f", pc)}</Data></Cell>")
            sb.appendLine("    <Cell><Data ss:Type=\"Number\">$stock</Data></Cell>")
            sb.appendLine("   </Row>")
        }

        sb.appendLine("  </Table>")
        sb.appendLine(" </Worksheet>")
        sb.appendLine("</Workbook>")

        FileOutputStream(file).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        return file
    }

    fun shareXlsxReport(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir reporte (.xlsx)"))
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
