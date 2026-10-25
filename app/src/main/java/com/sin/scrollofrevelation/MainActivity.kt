package com.sin.scrollofrevelation

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import kotlin.math.min

/** Offline screenshot-to-readable-PDF tool. No storage or network permissions. */
class MainActivity : Activity() {
    private val shots = mutableListOf<Uri>()
    private lateinit var info: TextView
    private lateinit var items: LinearLayout
    private val gold = Color.rgb(226, 183, 65)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 22, 18, 12)
            setBackgroundColor(Color.rgb(10, 10, 13))
        }
        root.addView(TextView(this).apply {
            setBackgroundResource(R.drawable.title_panel)
            setPadding(12, 26, 12, 26)
            text = "ScrolL of RevelatioN"
            textSize = 26f
            setTextColor(gold)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "SIN Capture Suite  •  Read every word"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        fun action(title: String, click: () -> Unit) {
            root.addView(Button(this).apply {
                text = title
                setTextColor(Color.BLACK)
                setBackgroundColor(gold)
                setOnClickListener { click() }
            })
        }
        action("Add screenshots") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }, 1)
        }
        action("Save readable PDF") {
            if (shots.isEmpty()) {
                Toast.makeText(this, "Select screenshots first", Toast.LENGTH_SHORT).show()
            } else {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/pdf"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, "Scroll-of-Revelation.pdf")
                }, 2)
            }
        }
        action("Clear screenshots") { shots.clear(); refresh() }
        info = TextView(this).apply { setTextColor(gold); setPadding(0, 12, 0, 12) }
        root.addView(info)
        items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(items) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        info.text = "${shots.size} screenshot(s). Arrange in reading order."
        items.removeAllViews()
        shots.forEachIndexed { index, uri ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = "${index + 1}. ${uri.lastPathSegment?.takeLast(35) ?: "Screenshot"}"
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            fun moveButton(symbol: String, delta: Int) {
                row.addView(Button(this).apply {
                    text = symbol
                    setOnClickListener {
                        val target = index + delta
                        if (target in shots.indices) {
                            val item = shots.removeAt(index)
                            shots.add(target, item)
                            refresh()
                        }
                    }
                })
            }
            moveButton("↑", -1)
            moveButton("↓", 1)
            items.addView(row)
        }
    }

    @Deprecated("Using legacy result callback for simple picker MVP")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            1 -> {
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) shots.add(clip.getItemAt(i).uri)
                } ?: data.data?.let { shots.add(it) }
                refresh()
            }
            2 -> data.data?.let { exportPdf(it) }
        }
    }

    private fun exportPdf(destination: Uri) {
        val pdf = PdfDocument()
        try {
            var pageNumber = 1
            shots.forEachIndexed { index, source ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(source)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                    "Cannot decode screenshot ${index + 1}"
                }
                var sample = 1
                while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 12000) sample *= 2
                val bitmap = contentResolver.openInputStream(source)?.use {
                    BitmapFactory.decodeStream(it, null,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Cannot open screenshot ${index + 1}")
                try {
                    var top = 0
                    while (top < bitmap.height) {
                        val height = min(3000, bitmap.height - top)
                        val page = pdf.startPage(
                            PdfDocument.PageInfo.Builder(bitmap.width, height, pageNumber++).create()
                        )
                        page.canvas.drawBitmap(bitmap, 0f, -top.toFloat(), null)
                        pdf.finishPage(page)
                        top += height
                    }
                } finally { bitmap.recycle() }
            }
            contentResolver.openOutputStream(destination)?.use { pdf.writeTo(it) }
                ?: error("Unable to write PDF")
            Toast.makeText(this, "Readable PDF saved", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "PDF export failed", Toast.LENGTH_LONG).show()
        } finally { pdf.close() }
    }
}
