package com.example.nutritionassistant.ui.screen.diary

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nutritionassistant.data.remote.dto.NutrientItem
import com.example.nutritionassistant.databinding.DialogAddFoodBinding
import com.example.nutritionassistant.databinding.FragmentDiaryBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.nutritionassistant.R
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import com.example.nutritionassistant.databinding.DialogChangeMealTypeBinding
import com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder


/**
 * 饮食日记页面
 * 功能：展示所有饮食记录，按日期分组，支持删除记录
 */
@AndroidEntryPoint // Hilt 注入
class DiaryFragment : Fragment() {

    // ViewBinding 绑定布局
    private var _binding: FragmentDiaryBinding? = null
    // 在 DiaryFragment 的成员变量区域添加
    private var queryObserver: androidx.lifecycle.Observer<NutrientItem?>? = null
    private val binding get() = _binding!!

    // 注入 ViewModel
    private val viewModel: DiaryViewModel by viewModels()

    // 列表适配器
    private lateinit var adapter: DiaryAdapter

    private var selectedDate: Long = System.currentTimeMillis()
    private var hasUserQueried = false


    // 创建视图
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 视图创建完成
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化适配器
        // 点击删除按钮 → 调用 ViewModel 删除记录
        adapter = DiaryAdapter(
            onDelete = { record -> viewModel.deleteRecord(record) },
            onMealTagClick = { record -> showMealTypePicker(record) }  // 新增
        )

        // 设置 RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // 观察 ViewModel 里的饮食记录数据
        viewModel.records.observe(viewLifecycleOwner) { groups ->
            // 把分组数据 → 转成扁平列表 → 显示到界面
            val flatList = groups.toFlatList()
            adapter.submitList(flatList)

            // 控制空状态提示的显示/隐藏
            if (groups.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            }
        }

        binding.fabAdd.setOnClickListener {
            showAddFoodDialog()
        }
        // 快捷入口：跳转到扫码录入
        binding.btnGoScan.setOnClickListener {
            findNavController().navigate(R.id.action_diaryFragment_to_scanFragment)
        }

        // 快捷入口：跳转到拍照分析
        binding.btnGoAnalyze.setOnClickListener {
            findNavController().navigate(R.id.action_diaryFragment_to_analyzeFragment)
        }
    }

    private fun showAddFoodDialog() {
        val dialogBinding = DialogAddFoodBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        var queriedItem: NutrientItem? = null

        // 默认餐别：加餐
        dialogBinding.rgMealType.check(dialogBinding.rbSnack.id)

        // 日期默认为今天
        updateDateText(dialogBinding.tvDate, selectedDate)
        dialogBinding.tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedDate
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    cal.set(year, month, day)
                    selectedDate = cal.timeInMillis
                    updateDateText(dialogBinding.tvDate, selectedDate)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialogBinding.btnSave.setOnClickListener {
            val gramsText = dialogBinding.etGrams.text.toString()
            val grams = if (gramsText.isNotBlank()) gramsText.toFloatOrNull() ?: 100f else 100f
            val name = dialogBinding.etFoodName.text.toString()
            val mealType = when (dialogBinding.rgMealType.checkedRadioButtonId) {
                dialogBinding.rbBreakfast.id -> "breakfast"
                dialogBinding.rbLunch.id -> "lunch"
                dialogBinding.rbDinner.id -> "dinner"
                else -> "snack"
            }
            queriedItem?.let { viewModel.addManualFood(name, mealType, selectedDate, it, grams) }
            viewModel.clearQueryResult()
            hasUserQueried = false  // 重置标志位
            dialog.dismiss()
            Toast.makeText(requireContext(), "已添加", Toast.LENGTH_SHORT).show()
        }

        // 取消
        dialogBinding.btnCancel.setOnClickListener {
            viewModel.clearQueryResult()
            hasUserQueried = false  // 重置标志位
            dialog.dismiss()
        }

        dialogBinding.btnQuery.setOnClickListener {
            val name = dialogBinding.etFoodName.text.toString()
            if (name.isBlank()) {
                Toast.makeText(requireContext(), "请输入食物名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogBinding.layoutPreview.visibility = View.VISIBLE
            dialogBinding.tvPreview.text = "正在查询..."
            dialogBinding.btnSave.isEnabled = false     // 禁用保存按钮
            dialogBinding.btnQuery.isEnabled = false    // 禁用查询按钮，防止重复点击

            // 触发ViewModel查询
            viewModel.queryFood(name)
            hasUserQueried = true
        }

        dialog.show()

        queryObserver?.let { viewModel.queryResult.removeObserver(it) }

        queryObserver = androidx.lifecycle.Observer { item ->
            dialogBinding.btnQuery.isEnabled = true

            if (item != null) {
                queriedItem = item
                dialogBinding.layoutPreview.visibility = View.VISIBLE
                dialogBinding.tvPreview.text =
                            "每100克：\n" +
                            "热量：${item.calorie ?: "?"} kcal\n" +
                            "蛋白质：${item.protein ?: "?"} g\n" +
                            "脂肪：${item.fat ?: "?"} g\n" +
                            "碳水：${item.carbohydrate ?: "?"} g"
                dialogBinding.btnSave.isEnabled = true
            } else {
                if (hasUserQueried) {
                    dialogBinding.layoutPreview.visibility = View.VISIBLE
                    dialogBinding.tvPreview.text = "未找到该食物，请尝试其他关键词"
                    dialogBinding.btnSave.isEnabled = false
                    hasUserQueried = false  // 重置标志位
                }
            }
        }
        viewModel.queryResult.observe(viewLifecycleOwner, queryObserver!!)
    }

    private fun updateDateText(tv: TextView, timestamp: Long) {
        val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
        tv.text = sdf.format(Date(timestamp))
    }

    private fun showMealTypePicker(record: FoodRecordEntity) {
        val mealTypes = arrayOf("早餐", "午餐", "晚餐", "加餐")
        val mealTypeValues = arrayOf("breakfast", "lunch", "dinner", "snack")
        val currentIndex = mealTypeValues.indexOf(record.mealType)

        // 1. 创建 MaterialAlertDialog 实例
        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            ThemeOverlay_Material3_MaterialAlertDialog
        ).create()

        // 2. 引入 ViewBinding 并设置给 Dialog
        val dialogBinding = DialogChangeMealTypeBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setView(dialogBinding.root)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        when (currentIndex) {
            0 -> dialogBinding.rgMealTypeContainer.check(dialogBinding.rbBreakfast.id)
            1 -> dialogBinding.rgMealTypeContainer.check(dialogBinding.rbLunch.id)
            2 -> dialogBinding.rgMealTypeContainer.check(dialogBinding.rbDinner.id)
            3 -> dialogBinding.rgMealTypeContainer.check(dialogBinding.rbSnack.id)
        }

        // 5. 监听 RadioGroup 选择状态变化：点击即保存，交互一气呵成
        dialogBinding.rgMealTypeContainer.setOnCheckedChangeListener { _, checkedId ->
            val selectIndex = when (checkedId) {
                dialogBinding.rbBreakfast.id -> 0
                dialogBinding.rbLunch.id -> 1
                dialogBinding.rbDinner.id -> 2
                dialogBinding.rbSnack.id -> 3
                else -> currentIndex
            }

            val newMealType = mealTypeValues[selectIndex]

            // 触发你的 ViewModel 执行数据库或内存更新
            viewModel.updateMealType(record, newMealType)

            // 关闭弹窗
            dialog.dismiss()

            // 丝滑提示用户
            Toast.makeText(
                requireContext(),
                "餐别已修改为 ${mealTypes[selectIndex]}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 6. 绑定取消按钮点击事件
        dialogBinding.btnMealTypeCancel.setOnClickListener {
            dialog.dismiss()
        }

        // 7. 最终呈现弹窗
        dialog.show()
    }

    // 页面销毁，释放资源
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}