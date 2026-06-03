package com.example.nutritionassistant.ui.screen.scan

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.nutritionassistant.databinding.FragmentScanBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import android.Manifest
import android.R.attr.scaleX
import android.R.attr.scaleY
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContentProviderCompat.requireContext

@AndroidEntryPoint
class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()

        binding.scanOverlay.startScanning()


        binding.buttonCancel.setOnClickListener {
            viewModel.resumeScanning()
            viewModel.clearResult()
        }

        binding.buttonSave.setOnClickListener {
            // 获取餐别
            val mealType = when (binding.rgMealType.checkedRadioButtonId) {
                binding.rbBreakfast.id -> "breakfast"
                binding.rbLunch.id -> "lunch"
                binding.rbDinner.id -> "dinner"
                else -> "snack"
            }
            // 获取克数
            val gramsText = binding.etGrams.text.toString()
            val grams = if (gramsText.isNotBlank()) gramsText.toFloatOrNull() ?: 100f else 100f

            viewModel.saveCurrentResult(mealType, grams)
        }

        binding.btnRescan.setOnClickListener {
            viewModel.resumeScanning()
        }

        binding.btnRequery.setOnClickListener {
            val correctedName = binding.editFoodName.text.toString().trim()
            if (correctedName.isNotBlank()) {
                viewModel.requeryWithCorrectedName(correctedName)
            } else {
                Toast.makeText(requireContext(), "请输入正确的商品名", Toast.LENGTH_SHORT).show()
            }
        }

        if (isCameraPermissionGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { scanning ->
            // 控制绿点显示
            if (scanning) {
                binding.scanOverlay.startScanning()
            } else {
                binding.scanOverlay.stopScanning()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(Executors.newSingleThreadExecutor()) @OptIn(ExperimentalGetImage::class) { imageProxy ->
                    if (viewModel.isScanning.value == true) {
                        // 设置取景框裁剪区域
                        val cropRect = getCropRect(imageProxy)
                        if (cropRect != null) {
                            val imageWidth = imageProxy.width
                            val imageHeight = imageProxy.height
                            // 将比例坐标转换为图像实际像素坐标
                            val leftPx = (cropRect.left / 10000f * imageWidth).toInt()
                            val topPx = (cropRect.top / 10000f * imageHeight).toInt()
                            val rightPx = (cropRect.right / 10000f * imageWidth).toInt()
                            val bottomPx = (cropRect.bottom / 10000f * imageHeight).toInt()
                            imageProxy.setCropRect(Rect(leftPx, topPx, rightPx, bottomPx))
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            val scanner = BarcodeScanning.getClient()
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        barcode.rawValue?.let { value ->
                                            viewModel.onBarcodeScanned(value)
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    } else {
                        imageProxy.close()
                    }
                }
            }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
    }

    private fun observeViewModel() {
        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                binding.editFoodName.setText(result.foodName)
                // 显示营养数值（如果数值为0则显示“?”）
                binding.textNutrition.text = if (result.calories > 0 || result.protein > 0 || result.fat > 0 || result.carbs > 0) {
                    "热量: ${result.calories.toInt()} kcal | 蛋白质: ${result.protein.toInt()}g | 脂肪: ${result.fat.toInt()}g | 碳水: ${result.carbs.toInt()}g"
                } else {
                    "缓存失败，请使用其他方法录入"
                }
                binding.resultCard.visibility = View.VISIBLE
            } else {
                binding.resultCard.visibility = View.GONE
            }
        }

        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                Toast.makeText(requireContext(), "已添加到饮食记录", Toast.LENGTH_SHORT).show()
                binding.resultCard.visibility = View.GONE
                viewModel.resumeScanning()
            }
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getCorrectionMatrix(
        imageProxy: ImageProxy,
        previewView: PreviewView
    ): Matrix {
        val cropRect = imageProxy.cropRect
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()

        val source = floatArrayOf(
            cropRect.left.toFloat(), cropRect.top.toFloat(),
            cropRect.right.toFloat(), cropRect.top.toFloat(),
            cropRect.right.toFloat(), cropRect.bottom.toFloat(),
            cropRect.left.toFloat(), cropRect.bottom.toFloat()
        )

        val destination = floatArrayOf(
            0f, 0f,
            previewView.width.toFloat(), 0f,
            previewView.width.toFloat(), previewView.height.toFloat(),
            0f, previewView.height.toFloat()
        )

        // 根据旋转调整顶点顺序
        val shiftOffset = rotationDegrees / 90 * 2
        val tempArray = destination.clone()
        for (toIndex in source.indices) {
            val fromIndex = (toIndex + shiftOffset) % source.size
            destination[toIndex] = tempArray[fromIndex]
        }

        matrix.setPolyToPoly(source, 0, destination, 0, 4)
        return matrix
    }


    private fun getCropRect(imageProxy: ImageProxy): Rect? {
        val previewView = binding.previewView
        val scanFrame = binding.scanFrame

        val scanLocation = IntArray(2)
        scanFrame.getLocationInWindow(scanLocation)
        val previewLocation = IntArray(2)
        previewView.getLocationInWindow(previewLocation)

        val scanLeft = (scanLocation[0] - previewLocation[0]).toFloat()
        val scanTop = (scanLocation[1] - previewLocation[1]).toFloat()
        val scanRight = scanLeft + scanFrame.width
        val scanBottom = scanTop + scanFrame.height

        if (previewView.width <= 0 || previewView.height <= 0) return null

        // 获取图像→PreviewView 的映射矩阵
        val matrix = getCorrectionMatrix(imageProxy, previewView)
        // 求逆矩阵
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return null

        // 用逆矩阵把取景框坐标反向映射回图像坐标
        val src = floatArrayOf(scanLeft, scanTop, scanRight, scanBottom)
        val dst = FloatArray(4)
        inverse.mapPoints(dst, src)

        val left = dst[0].toInt().coerceIn(0, imageProxy.width)
        val top = dst[1].toInt().coerceIn(0, imageProxy.height)
        val right = dst[2].toInt().coerceIn(0, imageProxy.width)
        val bottom = dst[3].toInt().coerceIn(0, imageProxy.height)

        return Rect(left, top, right, bottom)
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100//相机权限
    }
}