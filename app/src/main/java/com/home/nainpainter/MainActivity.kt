package com.home.nainpainter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.opengl.Visibility
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Window
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.alpha
import com.github.dhaval2404.colorpicker.ColorPickerDialog
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape
import com.github.dhaval2404.colorpicker.model.ColorSwatch
import com.github.dhaval2404.colorpicker.util.setVisibility
import com.home.nainpainter.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.round


class MainActivity : AppCompatActivity() ,Multipart.OnFileUploadedListener{

    var resizeAspect : Float = 1f
    val REQUEST_CODE = 200
    lateinit var mGetContent: ActivityResultLauncher<Uri>
    lateinit var builder : AlertDialog
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initDebug()
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        binding.btCapture.setOnClickListener {
            openCamera()
        }
        registerCamera()
        binding.btColorPicker.setOnClickListener {
            openColorPicker()
        }
    }
    fun openColorPicker()
    {
        ColorPickerDialog
            .Builder(this)        				// Pass Activity Instance
            .setTitle("Pick Theme")           	// Default "Choose Color"
            .setColorShape(ColorShape.SQAURE)   // Default ColorShape.CIRCLE
            //.setDefaultColor(mDefaultColor)     // Pass Default Color
            .setColorListener { color, colorHex ->
                // Handle Color Selection
                val image = Bitmap.createBitmap(100,100,Bitmap.Config.RGB_565)
                image.eraseColor(color)
                processImages(image)
            }
            .show()

    }

    fun registerCamera()
    {
        val file = File(filesDir, "picFromCamera.jpg")
        val uri: Uri =
            FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", file)

        mGetContent = registerForActivityResult(
            TakePicture()
        ) {
            uri.path?.let { it1 -> Log.d("capture res", it1) }
            Log.d("Exist", file.exists().toString())
            var im = BitmapFactory.decodeFile(file.path)
            im = correctImageOrientation(im,file.path)
            Log.d(" Image  = " , im.width.toString() + " " + im.height.toString())

            val w = 1080
            val h = w * im.height/im.width
            resizeAspect = (im.width.toFloat()) /w.toFloat()
            im = Bitmap.createScaledBitmap(im,w, h, true)

            val file = File(filesDir, "mainImage.jpg")
            val fOut = FileOutputStream(file)
            im.compress(Bitmap.CompressFormat.JPEG, 95, fOut)
            fOut.flush()
            fOut.close()

            binding.capturedImage.setImageBitmap(im)
            val rezFile = resizeImage(im)
            var imRes = BitmapFactory.decodeFile(rezFile.path)
            Log.d("imRes Image  = " , imRes.width.toString() + " " + imRes.height.toString())

            builder = AlertDialog.Builder(this,com.home.nainpainter.R.style.CustomAlertDialog)
                .create()

            val window: Window? = builder.window
//            window?.setLayout(
//                WindowManager.LayoutParams.MATCH_PARENT,
//                WindowManager.LayoutParams.WRAP_CONTENT
//            )
            window?.setGravity(Gravity.CENTER)
            val view = layoutInflater.inflate(com.home.nainpainter.R.layout.loading_dialog,null)
            builder.setView(view)

            builder.setCanceledOnTouchOutside(false)
            builder.show()

            Thread(Runnable {
                uploadImage(rezFile.path)
            }).start()

        }
    }

    private fun resizeImage(bitmap : Bitmap) : File
    {
        //w = 300
        //h = w * imageMain.height/imageMain.width
        val w = 400
        val h = w * bitmap.height/bitmap.width

        resizeAspect = (bitmap.width.toFloat()) /w.toFloat()

        val resized = Bitmap.createScaledBitmap(bitmap,w, h, true)
        val file = File(filesDir, "resized.jpg")
        val uri: Uri =
            FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", file)

        val fOut = FileOutputStream(file)

        resized.compress(Bitmap.CompressFormat.PNG, 95, fOut)
        fOut.flush()
        fOut.close()
        Log.d("====>fOut",file.path)
        uri.path?.let { Log.d("====>URI", it) }
        return file

    }

    private fun correctImageOrientation(bitmap : Bitmap , path : String) : Bitmap
    {
        val ei = ExifInterface(path)
        val orientation: Int = ei.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        var rotatedBitmap: Bitmap
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotatedBitmap = rotateImage(bitmap, 90F)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotatedBitmap = rotateImage(bitmap, 180F)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotatedBitmap = rotateImage(bitmap, 270F)
            ExifInterface.ORIENTATION_NORMAL -> rotatedBitmap = bitmap
            else -> rotatedBitmap = bitmap
        }
        return  rotatedBitmap

    }
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }
    private fun openCamera()
    {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE
            )
        }
        else
        {
            val file = File(filesDir, "picFromCamera.jpg")
            val uri: Uri =
                FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", file)

            mGetContent.launch(uri)
        }


    }


    private fun uploadImage(path: String)
    {
        val fileOut = File(filesDir, "output.png")
        val file = File(path)
        val multipart = Multipart(URL("http://185.252.30.175:5000/img"),fileOut)
        multipart.addFilePart("image_file",file,"image.jpg","image/jpeg")
        multipart.upload(this)


    }

    override fun onFileUploadingFailed(responseCode: Int) {

        Log.d("File uplod","onFileUploadingFailed")
        runOnUiThread(Runnable {
            builder.dismiss()
        })
    }

    //fun processImages(mainIm : Bitmap , mask : Bitmap , sample :Bitmap )
    fun processImages(inputIcon : Bitmap?)
    {
        Log.d("Start","Start Procvessing")
        val file = File(filesDir, "mainImage.jpg")

        val mainIm = BitmapFactory.decodeFile(file.path)
        val maskFile = File(filesDir, "mask.png")
        var mask = BitmapFactory.decodeFile(maskFile.path)
        mask = Bitmap.createScaledBitmap(mask,mainIm.width, mainIm.height, true)

        var icon = BitmapFactory.decodeResource(
            resources,
            com.home.nainpainter.R.drawable.color_small
        )
        if (inputIcon != null)
        {
            icon = inputIcon
        }
        val matMain = Mat()
        Utils.bitmapToMat(mainIm, matMain)


        val matMask = Mat()
        Utils.bitmapToMat(mask, matMask)
        val matMaskBig = Mat()
        val matSample = Mat()
        Utils.bitmapToMat(icon, matSample)
        val wdiff = ((mask.width - icon.width)/2).toInt()
        val hdiff = (mask.height - icon.height)/2

        Core.copyMakeBorder(matSample,matMaskBig,hdiff,hdiff,wdiff,wdiff,Core.BORDER_REFLECT101)
        Imgproc.resize(matMaskBig,matMaskBig, Size(mainIm.width.toDouble(),mainIm.height.toDouble()))
        val  coloredLayers = ArrayList<Mat>()
        Core.split(matMaskBig,coloredLayers)
        val  maskLayers = ArrayList<Mat>()
        Core.split(matMask,maskLayers)
        val combined = ArrayList<Mat>()
        combined.add(coloredLayers[0])
        combined.add(coloredLayers[1])
        combined.add(coloredLayers[2])
        combined.add(maskLayers[3])
        val outPutMat = Mat()

        Core.merge(combined,outPutMat)
        val rangeMath = Mat()
        Core.inRange(outPutMat, Scalar(0.0,0.0,0.0,100.0),
            Scalar(255.0,255.0,255.0,255.0),rangeMath)

        val indexMat = Mat()
        Core.findNonZero(rangeMath,indexMat)
        Log.d("index size", indexMat.size().toString())

        val mainMatPng = Mat()
        Imgproc.cvtColor(matMain,mainMatPng,Imgproc.COLOR_RGBA2RGB)

        for (i in 0 until indexMat.rows()) {
            val overlayColorAlpha = outPutMat.get((indexMat[i, 0][1]).toInt(), (indexMat[i, 0][0]).toInt())
            val backgroundColorAlpha = matMain.get((indexMat[i, 0][1]).toInt(), (indexMat[i, 0][0]).toInt())
            val overlayColor = doubleArrayOf(overlayColorAlpha[0],overlayColorAlpha[1],overlayColorAlpha[2])
            val backgroundColor = doubleArrayOf(backgroundColorAlpha[0],backgroundColorAlpha[1],backgroundColorAlpha[2])
            val grayVal = (backgroundColorAlpha[0]+backgroundColorAlpha[1]+backgroundColorAlpha[2])/3
            //Log.d("==>",grayVal.toString())
            var alpha = 0.1
            if ((grayVal - 200) > 0)
                alpha += (grayVal - 200) / 100
            val overlayAlpha = (overlayColorAlpha[3]/255 - alpha)
            val bgc = backgroundColor.map { it * (1 - overlayAlpha) }
            val ovc = overlayColor.map { it * overlayAlpha }
            val compositeColor = bgc.zip(ovc).map { (a,b) -> a+b }
            val byteArray = compositeColor.map { it.toInt().toByte() }.toByteArray()
            mainMatPng.put((indexMat[i, 0][1]).toInt(), (indexMat[i, 0][0]).toInt(),byteArray)

        }
            //Log.d("data",indexMat[i,0].toString())
//        for (i in 0 until matMain.rows())
//            for (j in 0 until matMain.cols() ) {
//                val data = ByteArray(1)
//                rangeMath[i, j,data]
//                Log.d("test","test")



           // }






        val outBitmap = mainIm.copy(mainIm.config,true)
        Utils.matToBitmap(mainMatPng,outBitmap)



        binding.capturedImage.setImageBitmap(outBitmap)
        print("Processed")
        binding.btColorPicker.setVisibility(visible = true)



//        val grayBitmap = bitmap.copy(bitmap.config, true)
//        Utils.matToBitmap(mat, grayBitmap)


    }

    override fun onFileUploadingSuccess(response: File) {
        Log.d("File uplod","onFileUploadingSuccess")
        var imRes = BitmapFactory.decodeFile(response.path)
        val w = round( resizeAspect * imRes.width)
        val h = round( resizeAspect * imRes.height)
        val resized = Bitmap.createScaledBitmap(imRes,w.toInt(), h.toInt(), true)
        val file = File(filesDir, "mask.png")
        val fOut = FileOutputStream(file)

        resized.compress(Bitmap.CompressFormat.PNG, 95, fOut)
        fOut.flush()
        fOut.close()
        runOnUiThread(Runnable {
            print("Server responed")
            //BitmapFactory.decodeFile(file)
            //binding.capturedImage.setImageBitmap(resized)
            processImages(null)
            builder.dismiss()
        })


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE)
        {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                openCamera()
            }
        }
    }


}