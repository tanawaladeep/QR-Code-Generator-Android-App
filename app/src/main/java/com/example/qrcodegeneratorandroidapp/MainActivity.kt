package com.example.qrcodegeneratorandroidapp

import android.Manifest
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var currentGeneratedBitmap: Bitmap? = null
    private var selectedTheme: QRTheme = QRTheme.Black
    private val permissionRequestCode = 1001

    private lateinit var colorCards: List<Pair<MaterialCardView, QRTheme>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        val editTextInput = findViewById<TextInputEditText>(R.id.edit_text_input)
        val inputLayout = findViewById<TextInputLayout>(R.id.input_layout)
        val btnPaste = findViewById<MaterialButton>(R.id.btn_paste)
        val btnClear = findViewById<MaterialButton>(R.id.btn_clear)
        val btnGenerate = findViewById<MaterialButton>(R.id.btn_generate)

        val colorBlack = findViewById<MaterialCardView>(R.id.color_black)
        val colorIndigo = findViewById<MaterialCardView>(R.id.color_indigo)
        val colorForest = findViewById<MaterialCardView>(R.id.color_forest)
        val colorSunset = findViewById<MaterialCardView>(R.id.color_sunset)
        val colorViolet = findViewById<MaterialCardView>(R.id.color_violet)

        val layoutPlaceholder = findViewById<LinearLayout>(R.id.layout_placeholder)
        val progressLoading = findViewById<CircularProgressIndicator>(R.id.progress_loading)
        val layoutQrResult = findViewById<LinearLayout>(R.id.layout_qr_result)
        val imageQrCode = findViewById<ImageView>(R.id.image_qr_code)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)
        val btnShare = findViewById<MaterialButton>(R.id.btn_share)

        // Set up color selection themes
        colorCards = listOf(
            colorBlack to QRTheme.Black,
            colorIndigo to QRTheme.Gradient(Color.parseColor("#1A237E"), Color.parseColor("#0288D1")),
            colorForest to QRTheme.Gradient(Color.parseColor("#004D40"), Color.parseColor("#00E676")),
            colorSunset to QRTheme.Gradient(Color.parseColor("#BF360C"), Color.parseColor("#FF9100")),
            colorViolet to QRTheme.Gradient(Color.parseColor("#311B92"), Color.parseColor("#E040FB"))
        )

        // Select default theme (Black)
        updateColorSelection(colorBlack)

        // Color card click listeners
        colorCards.forEach { (card, theme) ->
            card.setOnClickListener {
                updateColorSelection(card)
            }
        }

        // Paste functionality
        btnPaste.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text
                if (!text.isNullOrEmpty()) {
                    editTextInput.setText(text)
                    inputLayout.error = null
                    showSnackbar(editTextInput, "Text pasted successfully")
                } else {
                    showSnackbar(editTextInput, "Clipboard does not contain text")
                }
            } else {
                showSnackbar(editTextInput, "Clipboard is empty")
            }
        }

        // Clear functionality
        btnClear.setOnClickListener {
            editTextInput.setText("")
            inputLayout.error = null
            currentGeneratedBitmap = null
            
            // UI state reset
            layoutPlaceholder.visibility = View.VISIBLE
            progressLoading.visibility = View.GONE
            layoutQrResult.visibility = View.GONE
        }

        // Generate functionality
        btnGenerate.setOnClickListener {
            val text = editTextInput.text.toString().trim()
            if (text.isEmpty()) {
                inputLayout.error = "Please enter some text or URL"
                return@setOnClickListener
            }
            inputLayout.error = null

            // Transition UI to loading state
            layoutPlaceholder.visibility = View.GONE
            progressLoading.visibility = View.VISIBLE
            layoutQrResult.visibility = View.GONE

            lifecycleScope.launch {
                // Generate QR Code off the main thread
                val bitmap = withContext(Dispatchers.Default) {
                    QRCodeGenerator.generate(text, 512, 512, selectedTheme)
                }

                if (bitmap != null) {
                    currentGeneratedBitmap = bitmap
                    imageQrCode.setImageBitmap(bitmap)

                    // Transition UI to success state
                    progressLoading.visibility = View.GONE
                    layoutQrResult.visibility = View.VISIBLE
                } else {
                    // Fail state
                    progressLoading.visibility = View.GONE
                    layoutPlaceholder.visibility = View.VISIBLE
                    showSnackbar(editTextInput, "Failed to generate QR Code")
                }
            }
        }

        // Save functionality
        btnSave.setOnClickListener {
            currentGeneratedBitmap?.let { bitmap ->
                saveBitmapToGallery(bitmap, editTextInput)
            } ?: showSnackbar(editTextInput, "No QR Code generated yet")
        }

        // Share functionality
        btnShare.setOnClickListener {
            currentGeneratedBitmap?.let { bitmap ->
                shareBitmap(bitmap, editTextInput)
            } ?: showSnackbar(editTextInput, "No QR Code generated yet")
        }
    }

    private fun updateColorSelection(selectedCard: MaterialCardView) {
        val primaryColor = getPrimaryColor()
        colorCards.forEach { (card, theme) ->
            if (card == selectedCard) {
                selectedTheme = theme
                card.strokeWidth = dpToPx(3)
                card.strokeColor = primaryColor
            } else {
                card.strokeWidth = dpToPx(1)
                card.strokeColor = Color.parseColor("#CCCCCC")
            }
        }
    }

    private fun getPrimaryColor(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        return typedValue.data
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun showSnackbar(anchor: View, message: String) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, anchor: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val filename = "QR_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QRGenerator")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri).use { outputStream ->
                        if (outputStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    showSnackbar(anchor, "QR Code saved to Gallery")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    showSnackbar(anchor, "Failed to save image: ${e.localizedMessage}")
                }
            } else {
                showSnackbar(anchor, "Failed to create media store entry")
            }
        } else {
            // Legacy Android permissions check
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                saveLegacy(bitmap, anchor)
            } else {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), permissionRequestCode)
            }
        }
    }

    private fun saveLegacy(bitmap: Bitmap, anchor: View) {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "QRGenerator")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val filename = "QR_${System.currentTimeMillis()}.png"
        val file = File(directory, filename)

        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            MediaScannerConnection.scanFile(this, arrayOf(file.toString()), arrayOf("image/png")) { _, _ ->
                runOnUiThread {
                    showSnackbar(anchor, "QR Code saved to Gallery")
                }
            }
        } catch (e: Exception) {
            showSnackbar(anchor, "Failed to save: ${e.localizedMessage}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            val anchor = findViewById<View>(R.id.edit_text_input)
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                currentGeneratedBitmap?.let { saveLegacy(it, anchor) }
            } else {
                showSnackbar(anchor, "Storage permission is required to save image")
            }
        }
    }

    private fun shareBitmap(bitmap: Bitmap, anchor: View) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cachePath = File(cacheDir, "shared_images")
                cachePath.mkdirs()
                val file = File(cachePath, "shared_qr.png")
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                val contentUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "com.example.qrcodegeneratorandroidapp.fileprovider",
                    file
                )

                if (contentUri != null) {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setDataAndType(contentUri, contentResolver.getType(contentUri))
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        type = "image/png"
                    }
                    withContext(Dispatchers.Main) {
                        startActivity(Intent.createChooser(shareIntent, "Share QR Code via"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar(anchor, "Failed to share: ${e.localizedMessage}")
                }
            }
        }
    }
}