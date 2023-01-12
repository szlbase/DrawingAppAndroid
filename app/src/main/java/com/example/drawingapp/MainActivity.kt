package com.example.drawingapp

import android.Manifest
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var brushButton: ImageButton? = null
    private var galleryButton: ImageButton? = null
    private var undoButton: ImageButton? = null
    private var redoButton: ImageButton? = null
    private var saveButton: ImageButton? = null
    private var imageButtonCurrentPaint: ImageButton? = null
    private var customProgressDialog: Dialog? = null

    private var showStorage: Boolean = false

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            if(result.resultCode == RESULT_OK && result.data != null) {
                val imageBg: ImageView = findViewById(R.id.iv_background)
                imageBg.setImageURI(result.data?.data)
            }
        }

    private val storageResultLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted ) {
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE && showStorage) {
                        val pickIntent = Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        openGalleryLauncher.launch(pickIntent)
                        showStorage = false
                    }
                } else {
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(this, "Permission is not granted",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)

        brushButton = findViewById(R.id.ib_brush)
        brushButton?.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        galleryButton = findViewById(R.id.ib_gallery)
        galleryButton?.setOnClickListener {
            showStorage = true
            requestStoragePermission()
        }

        undoButton = findViewById(R.id.ib_undo)
        undoButton?.setOnClickListener {
            onClickUndo()
        }

        redoButton = findViewById(R.id.ib_redo)
        redoButton?.setOnClickListener {
            onClickRedo()
        }

        saveButton = findViewById(R.id.ib_save)
        saveButton?.setOnClickListener {
            onClickSave()
        }

        setDrawingViewDefault()
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size")

        val smallBtn : ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtm : ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtm.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtm : ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtm.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if(view != imageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColorForBrush(colorTag)

            imageButton!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

            imageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal))

            imageButtonCurrentPaint = imageButton
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRationaleDialog("Permission Demon requires camera access",
                "Camera cannot be used because Camera access is denied")
        } else {
            storageResultLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun showRationaleDialog(title: String, message: String) {
        val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(title).setMessage(message).setPositiveButton("Cancel") {
                dialog, _ -> dialog.dismiss()
        }
    }

    private fun setDrawingViewDefault() {
        val linearLayoutPaintColors: LinearLayout = findViewById(R.id.ll_colors)
        imageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton //Black
        imageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
        drawingView!!.setColorForBrush(imageButtonCurrentPaint!!.tag.toString())
        drawingView!!.setSizeForBrush(20.toFloat())
    }

    private fun onClickUndo() {
        drawingView!!.onClickUndo()
    }

    private fun onClickRedo() {
        drawingView!!.onClickRedo()
    }

    private fun onClickSave() {
        showProgressDialog()
        if (isReadStorageAllowed()) {
            lifecycleScope.launch {
                val flDrawingView: FrameLayout = findViewById(
                    R.id.fl_drawing_view_container)
                saveBitmapFile(getBitmapFromView(flDrawingView))
            }
        } else {
            requestStoragePermission()
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = view.findViewById<ImageView>(R.id.iv_background).background
        bgDrawable?.draw(canvas) ?: run {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return bitmap
    }

    private suspend fun saveBitmapFile(bitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            bitmap?.let {
                try {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val file = File(externalCacheDir?.absoluteFile.toString()
                            + File.separator + "KidsDrawingApp_"
                            + System.currentTimeMillis()/1000 + ".png")
                    val fileOutputStream = FileOutputStream(file)
                    fileOutputStream.write(bytes.toByteArray())
                    fileOutputStream.close()

                    result = file.absolutePath

                    runOnUiThread {
                        dismissDialog()
                        if (result.isNotEmpty()) {
                            Toast.makeText(this@MainActivity,
                                "File Saved Successfully: $result",
                                Toast.LENGTH_LONG).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(this@MainActivity,
                                "Something went wrong!",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
                catch (e: java.lang.Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun shareImage(imageDir: String) {
        MediaScannerConnection.scanFile(this, arrayOf(imageDir), null){
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }

    private fun showProgressDialog() {
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun dismissDialog() {
        if(customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }
}