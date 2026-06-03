package com.example.nutritionassistant.ui.screen.home

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.nutritionassistant.R
import com.example.nutritionassistant.data.local.ApiKeyManager
import com.example.nutritionassistant.data.local.ReminderPreferences
import com.example.nutritionassistant.databinding.DialogApiKeyWarningBinding
import com.example.nutritionassistant.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    @Inject
    lateinit var reminderPreferences: ReminderPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        showProfile()
        showTodayNutrition()
        showNutritionAdvice()
        askPost()
        checkApiKeys()

    }

    private fun showNutritionAdvice() {
        viewModel.nutritionAdvice.observe(viewLifecycleOwner) { advice ->
            binding.tvAdvice.text = advice
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()  // 从扫码页返回时自动刷新
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()  // 从扫码页返回时自动刷新
    }

    private fun showProfile() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            if (profile != null) {
                binding.textGreeting.text = "你好，${profile.name}"
                binding.buttonSetupProfile.visibility = View.VISIBLE
                binding.buttonScan.visibility = View.VISIBLE
                binding.buttonDiary.visibility = View.VISIBLE
                binding.buttonReport.visibility = View.VISIBLE
            } else {
                binding.textGreeting.text = "请先设置个人资料"
                binding.buttonSetupProfile.visibility = View.VISIBLE
                binding.buttonScan.visibility = View.GONE
                binding.buttonDiary.visibility = View.GONE
                binding.buttonReport.visibility = View.GONE
            }
        }
    }

    private fun showTodayNutrition() {
        viewModel.todayNutrition.observe(viewLifecycleOwner) { nutrition ->
            val goal = viewModel.dailyGoal.value

            if (nutrition != null) {
                // 有实际摄入数据
                binding.textCalories.text = "${nutrition.totalCalories.toInt()} kcal"
                binding.textProtein.text = "${nutrition.totalProtein.toInt()}g"
                binding.textFat.text = "${nutrition.totalFat.toInt()}g"
                binding.textCarbs.text = "${nutrition.totalCarbs.toInt()}g"
                binding.textMealCount.text = "今日记录餐数: ${viewModel.mealCount.value} 餐"

                if (goal != null && goal.calories >= 0) {
                    val progress =
                        (nutrition.totalCalories / goal.calories * 100).toInt().coerceIn(0, 100)
                    binding.progressCalories.progress = progress
                    binding.textCalories.text =
                        "${nutrition.totalCalories.toInt()} / ${goal.calories.toInt()} kcal"
                    binding.progressCalories.setIndicatorColor(getProgressColor(progress))

                    // 蛋白进度条
                    val proteinProgress =
                        (nutrition.totalProtein / goal.protein * 100).toInt().coerceIn(0, 100)
                    binding.progressProtein.progress = proteinProgress
                    binding.progressProtein.setIndicatorColor(getProgressColor(proteinProgress))

                    // 脂肪进度条
                    val fatProgress = (nutrition.totalFat / goal.fat * 100).toInt().coerceIn(0, 100)
                    binding.progressFat.progress = fatProgress
                    binding.progressFat.setIndicatorColor(getProgressColor(fatProgress))

                    // 碳水进度条
                    val carbsProgress =
                        (nutrition.totalCarbs / goal.carbs * 100).toInt().coerceIn(0, 100)
                    binding.progressCarbs.progress = carbsProgress
                    binding.progressCarbs.setIndicatorColor(getProgressColor(carbsProgress))

                    viewModel.generateAdvice(nutrition, goal)

                }
            } else {
                // 还没有摄入记录，则显示每日目标（如果已设置）
                if (goal != null) {
                    binding.textCalories.text = "${goal.calories.toInt()} kcal"
                    binding.textProtein.text = "${goal.protein.toInt()}g"
                    binding.textFat.text = "${goal.fat.toInt()}g"
                    binding.textCarbs.text = "${goal.carbs.toInt()}g"
                } else {
                    // 连目标都没有，全部置零
                    binding.textCalories.text = "0 kcal"
                    binding.textProtein.text = "0g"
                    binding.textFat.text = "0g"
                    binding.textCarbs.text = "0g"
                }

                binding.progressCalories.progress = 0
                binding.progressCalories.setIndicatorColor(getProgressColor(0))
                binding.progressProtein.progress = 0
                binding.progressProtein.setIndicatorColor(getProgressColor(0))
                binding.progressFat.progress = 0
                binding.progressFat.setIndicatorColor(getProgressColor(0))
                binding.progressCarbs.progress = 0
                binding.progressCarbs.setIndicatorColor(getProgressColor(0))
            }
        }
    }

    private fun getProgressColor(progress: Int): Int {
        return when {
            progress >= 100 -> ContextCompat.getColor(requireContext(), R.color.state_danger)
            progress >= 80 -> ContextCompat.getColor(requireContext(), R.color.state_warning)
            else -> ContextCompat.getColor(requireContext(), R.color.brand_primary)
        }
    }

    private fun setupClickListeners() {
        binding.buttonSetupProfile.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
        }
        binding.buttonScan.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_scanFragment)
        }
        binding.buttonDiary.setOnClickListener {
            // 稍后跳转日记页面
            findNavController().navigate(R.id.action_homeFragment_to_diaryFragment)
        }
        binding.buttonReport.setOnClickListener {
            // 稍后跳转报告页面
            findNavController().navigate(R.id.action_homeFragment_to_reportFragment)
        }
        binding.buttonAi.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_aiAdvisorFragment)
        }
        binding.buttonAnalyze.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_analyzeFragment)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun askPost() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    102
                )
            }
        }
    }

    private fun checkApiKeys() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 如果已经提示过，直接返回
            if (reminderPreferences.isApiKeyWarningShown()) return@launch

            val (tianKey, aiKey) = reminderPreferences.getApiKeys()

            if (tianKey.isEmpty() || aiKey.isEmpty()) {
                val dialogBinding = DialogApiKeyWarningBinding.inflate(layoutInflater)
                val dialog = AlertDialog.Builder(requireContext())
                    .setView(dialogBinding.root)
                    .show()

                dialogBinding.btnWarningGoSetup.setOnClickListener {
                    findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
                    dialog.dismiss()
                }

                dialogBinding.btnWarningCancel.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        reminderPreferences.setApiKeyWarningShown()
                    }
                    dialog.dismiss()
                }
            }
        }
    }
}
