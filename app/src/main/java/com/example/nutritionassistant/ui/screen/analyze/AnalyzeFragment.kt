package com.example.nutritionassistant.ui.screen.analyze

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.util.CoilUtils.result
import com.example.nutritionassistant.databinding.FragmentAnalyzeBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class AnalyzeFragment : Fragment() {

    private var _binding: FragmentAnalyzeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalyzeViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private var photoFile: File? = null
    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { handleImageUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyzeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setOnClickListener()
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                101
            )
        }

        // 观察分析结果
        viewModel.analysisResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                binding.resultCard.visibility = View.VISIBLE
                binding.btnCapture.visibility = View.GONE
                binding.etFoodName.setText(result.foodName)
                binding.tvNutrition.text = "热量: ${result.calories.toInt()} kcal " +
                        "| 蛋白: ${result.protein.toInt()}g " +
                        "| 脂肪: ${result.fat.toInt()}g " +
                        "| 碳水: ${result.carbs.toInt()}g"
            } else {
                binding.resultCard.visibility = View.GONE
                binding.btnCapture.visibility = View.VISIBLE
            }
        }

        // 观察加载状态
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }

        // 观察保存结果
        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                Toast.makeText(requireContext(), "已添加到饮食日记", Toast.LENGTH_SHORT).show()
                binding.resultCard.visibility = View.GONE
                binding.btnCapture.visibility = View.VISIBLE
            }
        }

    }

    private fun setOnClickListener() {
        binding.btnRequery.setOnClickListener {
            val correctedName = binding.etFoodName.text.toString().trim()
            if (correctedName.isNotBlank()) {
                viewModel.requeryNutrition(correctedName)
            } else {
                Toast.makeText(requireContext(), "请输入正确的菜名", Toast.LENGTH_SHORT).show()
            }
        }

        // 拍照按钮
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        // 重拍
        binding.btnRetake.setOnClickListener {
            viewModel.clearResult()
        }

        // 保存到日记
        binding.btnSave.setOnClickListener {
            viewModel.analysisResult.value?.let {
                viewModel.saveToDiary(it)
            }
        }

        binding.btnGallery.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        photoFile = File(
            requireContext().filesDir,
            "food_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile!!).build()
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    photoFile?.absolutePath?.let { viewModel.analyzeFood(it) }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        "拍照失败: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleImageUri(uri: Uri) {
        // 将 content URI 复制到应用内部存储的文件中
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val fileName =
            "gallery_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val file = File(requireContext().filesDir, fileName)
        inputStream?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        // 调用 ViewModel 分析
        viewModel.analyzeFood(file.absolutePath)
    }
}