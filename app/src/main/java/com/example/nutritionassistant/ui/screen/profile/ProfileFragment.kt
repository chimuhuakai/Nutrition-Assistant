package com.example.nutritionassistant.ui.screen.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.nutritionassistant.NutritionAssistant
import com.example.nutritionassistant.data.local.ReminderPreferences
import com.example.nutritionassistant.data.local.ReminderTimes
import com.example.nutritionassistant.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// 允许 Hilt 注入ViewModel（必须加）
@AndroidEntryPoint
class ProfileFragment : Fragment() {

    // 视图绑定：用来获取 XML 里的输入框、按钮
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // 自动获取 ProfileViewModel（Hilt 自动创建）
    private val viewModel: ProfileViewModel by viewModels()

    @Inject
    lateinit var reminderPrefs: ReminderPreferences   // 添加这一行
    private var breakfastHour = 7
    private var breakfastMinute = 30
    private var lunchHour = 12
    private var lunchMinute = 0
    private var dinnerHour = 20
    private var dinnerMinute = 0

    // 加载页面布局 XML
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 页面创建完成，写逻辑
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.existingProfile.observe(viewLifecycleOwner) { profile ->
            if (profile != null) {
                binding.etName.setText(profile.name)
                binding.etAge.setText(profile.age.toString())
                if (profile.gender == "male") {
                    binding.rbMale.isChecked = true
                } else {
                    binding.rbFemale.isChecked = true
                }
                binding.etHeight.setText(profile.height.toString())
                binding.etWeight.setText(profile.weight.toString())
                when (profile.goal) {
                    "lose_fat" -> binding.rbLoseFat.isChecked = true
                    "gain_muscle" -> binding.rbGainMuscle.isChecked = true
                    else -> binding.rbMaintain.isChecked = true
                }

                if (profile.blacklist.isNotBlank()) {
                    binding.etBlacklist.setText(profile.blacklist)
                }
            }

            viewModel.reminderTimes.observe(viewLifecycleOwner) { times ->
                breakfastHour = times.breakfastHour
                breakfastMinute = times.breakfastMinute
                binding.tvBreakfastTime.text = String.format("%02d:%02d", breakfastHour, breakfastMinute)

                lunchHour = times.lunchHour
                lunchMinute = times.lunchMinute
                binding.tvLunchTime.text = String.format("%02d:%02d", lunchHour, lunchMinute)

                dinnerHour = times.dinnerHour
                dinnerMinute = times.dinnerMinute
                binding.tvDinnerTime.text = String.format("%02d:%02d", dinnerHour, dinnerMinute)

                binding.switchReminder.isChecked = times.enabled
            }

            viewModel.tianApiKey.observe(viewLifecycleOwner) { key ->
                binding.etTianApiKey.setText(key)
            }

            viewModel.aiApiKey.observe(viewLifecycleOwner) { key ->
                binding.etAiApiKey.setText(key)
            }
        }

        // 早餐时间点击
        binding.tvBreakfastTime.setOnClickListener {
            showTimePicker { hour, minute ->
                breakfastHour = hour; breakfastMinute = minute
                binding.tvBreakfastTime.text = String.format("%02d:%02d", hour, minute)
            }
        }

        // 午餐时间点击
        binding.tvLunchTime.setOnClickListener {
            showTimePicker { hour, minute ->
                lunchHour = hour; lunchMinute = minute
                binding.tvLunchTime.text = String.format("%02d:%02d", hour, minute)
            }
        }

        // 晚餐时间点击
        binding.tvDinnerTime.setOnClickListener {
            showTimePicker { hour, minute ->
                dinnerHour = hour; dinnerMinute = minute
                binding.tvDinnerTime.text = String.format("%02d:%02d", hour, minute)
            }
        }

        // 在 onViewCreated 中添加
        binding.btnTestApi.setOnClickListener {
            val tianKey = binding.etTianApiKey.text.toString().trim()
            val aiKey = binding.etAiApiKey.text.toString().trim()


            if (tianKey.isEmpty() && aiKey.isEmpty()) {
                Toast.makeText(requireContext(), "请先输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.testApiConnection(tianKey, aiKey)
        }

        viewModel.testResult.observe(viewLifecycleOwner) { result ->
            Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
        }


        // 点击【保存】按钮
        binding.btnSave.setOnClickListener {
            // 1. 获取用户输入的内容
            val name = binding.etName.text.toString() // 姓名
            val age = binding.etAge.text.toString().toIntOrNull() ?: 0 // 年龄
            val gender = if (binding.rbMale.isChecked) "male" else "female" // 性别
            val weight = binding.etWeight.text.toString().toFloatOrNull() ?: 0f // 体重
            val height = binding.etHeight.text.toString().toFloatOrNull() ?: 0f // 身高
            val goal = when { // 目标：减脂 / 增肌 / 维持
                binding.rbLoseFat.isChecked -> "lose_fat"
                binding.rbGainMuscle.isChecked -> "gain_muscle"
                else -> "maintain"
            }
            val blacklist = binding.etBlacklist.text.toString().trim()
            val enabled = binding.switchReminder.isChecked
            val tianKey = binding.etTianApiKey.text.toString().trim()
            val aiKey = binding.etAiApiKey.text.toString().trim()
            val times = ReminderTimes(
                breakfastHour, breakfastMinute,
                lunchHour, lunchMinute,
                dinnerHour, dinnerMinute,
                enabled
            )
            (requireActivity().application as NutritionAssistant).rescheduleMealReminders(times)



            // 2. 检查是否填写完整（非空判断）
            if (name.isBlank() || age == 0 || weight == 0f || height == 0f) {
                Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. 调用 ViewModel 保存数据到数据库
            viewModel.saveProfile(name, age, gender, weight, height, goal, blacklist)

            viewModel.saveReminderTimes(
                breakfastHour, breakfastMinute,
                lunchHour, lunchMinute,
                dinnerHour, dinnerMinute,
                enabled
            )

            viewModel.saveApiKeys(tianKey, aiKey)

            viewModel.saveReminderTimes(
                breakfastHour, breakfastMinute,
                lunchHour, lunchMinute,
                dinnerHour, dinnerMinute,
                enabled
            )

//             4. 提示保存成功b
            Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()


            // 5. 返回上一页（回到首页）
            findNavController().navigateUp()
        }
    }

    private fun showTimePicker(onTimeSet: (Int, Int) -> Unit) {
        val now = java.util.Calendar.getInstance()
        android.app.TimePickerDialog(
            requireContext(),
            { _, hour, minute -> onTimeSet(hour, minute) },
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            true
        ).show()
    }

    // 页面销毁时释放绑定，防止内存泄漏
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}