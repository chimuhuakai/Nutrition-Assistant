package com.example.nutritionassistant.ui.screen.aiadvisor

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.nutritionassistant.R
import com.example.nutritionassistant.databinding.DialogAdoptAllConfirmBinding
import com.example.nutritionassistant.databinding.DialogChangeMealTypeBinding
import com.example.nutritionassistant.databinding.FragmentAiAdvisorBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AiAdvisorFragment : Fragment() {

    private var _binding: FragmentAiAdvisorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AiAdvisorViewModel by viewModels()
    private var currentMode = MODE_NEXT_MEAL  // 默认下一餐

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiAdvisorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 模式切换监听
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = if (checkedId == R.id.btnModeNextMeal) MODE_NEXT_MEAL else MODE_FULL_DAY
                viewModel.clearAdviceList()
                binding.layoutChat.removeAllViews()
                loadMoreButton = null
                loadMoreProgress = null
            }
        }

        // 发送按钮
        binding.btnSend.setOnClickListener {
            val question = binding.etQuestion.text.toString().trim()
            when (currentMode) {
                MODE_NEXT_MEAL -> {
                    if (question.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入你想问的问题", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    viewModel.askQuestion(question)
                }
                MODE_FULL_DAY -> {
                    // 全天模式允许空问题，也可以输入特殊要求（如“来月经上火”）
                    viewModel.requestFullDayPlan(question)
                }
            }
            binding.etQuestion.text.clear()
        }

        // 加载状态观察
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val isLoadMoreMode = viewModel.isLoadMore.value ?: false

            if (loading) {
                if (isLoadMoreMode) {
                    // 加载更多：隐藏全局进度条，显示内联进度圈
                    binding.progressBar.visibility = View.GONE
                    loadMoreButton?.visibility = View.INVISIBLE
                    loadMoreProgress?.visibility = View.VISIBLE
                } else {
                    // 首次提问或全天规划：显示全局进度条
                    binding.progressBar.visibility = View.VISIBLE
                    loadMoreButton?.visibility = View.INVISIBLE // 按钮不可见
                    loadMoreProgress?.visibility = View.GONE
                }
                binding.btnSend.isEnabled = false
            } else {
                // 加载结束：恢复默认
                binding.progressBar.visibility = View.GONE
                binding.btnSend.isEnabled = true
                loadMoreButton?.visibility = View.VISIBLE
                loadMoreButton?.text = "✨ 探索更多相似美食"
                loadMoreButton?.isEnabled = true
                loadMoreProgress?.visibility = View.GONE
            }
        }

        // 推荐列表变化
        viewModel.adviceList.observe(viewLifecycleOwner) { items ->
            rebuildChatCards(items)
            Log.d("AdviceList", "收到 ${items.size} 条推荐")
        }

        // 采纳结果
        viewModel.adoptResult.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                Toast.makeText(requireContext(), "已添加到今日饮食日记", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var loadMoreButton: Button? = null
    private var loadMoreProgress: ProgressBar? = null

    private fun rebuildChatCards(items: List<AdviceItem>) {
        binding.layoutChat.removeAllViews()

        // 添加食物卡片
        for (item in items) {
            val card = layoutInflater.inflate(R.layout.card_advice, binding.layoutChat, false)
            card.findViewById<TextView>(R.id.tvFoodName).text = item.foodName
            card.findViewById<TextView>(R.id.tvReason).text = item.reason
            card.findViewById<TextView>(R.id.tvNutrition).text = item.nutritionText

            // 显示餐别标签（如果全天模式且布局中有 tvMealTag）
            if (item.mealType.isNotEmpty()) {
                val mealInfo = getMealInfo(item.mealType)

                card.findViewById<TextView>(R.id.tvMealTag)?.let { mealTag ->
                    mealTag.text = mealInfo.first
                    mealTag.setTextColor(getColor(requireContext(), mealInfo.third))
                    mealTag.backgroundTintList = ColorStateList.valueOf(getColor(requireContext(), mealInfo.second))
                    mealTag.visibility = View.VISIBLE
                }
            }

            // 采纳按钮
            card.findViewById<Button>(R.id.btnAdopt).setOnClickListener {
                showMealTypePicker(item)
            }
            binding.layoutChat.addView(card)
        }

        // 根据模式添加底部按钮
        when (currentMode) {
            MODE_FULL_DAY -> {
                // 全天模式：一键采纳按钮
                if (items.isNotEmpty()) {
                    val btnAdoptAll = Button(requireContext()).apply {
                        text = "一键采纳全天"
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_surface))
                        backgroundTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.brand_primary)
                        )
                        setOnClickListener { confirmAdoptAll(items) }
                    }
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams.topMargin = 12
                    btnAdoptAll.layoutParams = layoutParams
                    binding.layoutChat.addView(btnAdoptAll)
                }
            }
            MODE_NEXT_MEAL -> {
                // 下一餐模式：推荐更多按钮
                if (items.isNotEmpty()) {
                    val loadMoreView = layoutInflater.inflate(R.layout.item_load_more_button, binding.layoutChat, false)
                    loadMoreButton = loadMoreView.findViewById(R.id.btnLoadMore)
                    loadMoreProgress = loadMoreView.findViewById(R.id.progressLoadMore)

                    loadMoreButton?.visibility = View.VISIBLE
                    loadMoreButton?.text = "✨ 探索更多相似美食"
                    loadMoreButton?.isEnabled = true
                    loadMoreProgress?.visibility = View.GONE

                    loadMoreButton?.setOnClickListener {
                        if (viewModel.loading.value != true) {
                            viewModel.loadMore()
                        }
                    }
                    binding.layoutChat.addView(loadMoreView)
                }
            }
        }
    }

    private fun confirmAdoptAll(items: List<AdviceItem>) {

        val dialogBinding = DialogAdoptAllConfirmBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnAdoptConfirm.setOnClickListener {
            for (item in items) {
                val mealType = item.mealType.ifEmpty { "snack" }
                viewModel.adoptAdvice(item, mealType,MODE_FULL_DAY)
            }
            Toast.makeText(requireContext(), "已全部添加到日记", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialogBinding.btnAdoptCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showMealTypePicker(item: AdviceItem) {
        val dialogBinding = DialogChangeMealTypeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnMealTypeCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.rbBreakfast.setOnClickListener {
            viewModel.adoptAdvice(item, "breakfast",MODE_NEXT_MEAL)
            dialog.dismiss()
        }
        dialogBinding.rbLunch.setOnClickListener {
            viewModel.adoptAdvice(item, "lunch",MODE_NEXT_MEAL)
            dialog.dismiss()
        }
        dialogBinding.rbDinner.setOnClickListener {
            viewModel.adoptAdvice(item, "dinner",MODE_NEXT_MEAL)
            dialog.dismiss()
        }
        dialogBinding.rbSnack.setOnClickListener {
            viewModel.adoptAdvice(item, "snack",MODE_NEXT_MEAL)
            dialog.dismiss()
        }

        dialogBinding.tvMealTypeDescription.text = "规划此食物在今日时间轴上的归属"
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getMealInfo(mealType: String): Triple<String, Int, Int> {
        return when (mealType) {
            "breakfast" -> Triple("早餐", R.color.tag_breakfast_bg, R.color.tag_breakfast_text)
            "lunch" -> Triple("午餐", R.color.tag_lunch_bg, R.color.tag_lunch_text)
            "dinner" -> Triple("晚餐", R.color.tag_dinner_bg, R.color.tag_dinner_text)
            else -> Triple("加餐", R.color.tag_snack_bg, R.color.tag_snack_text)
        }
    }

    companion object {
        const val MODE_NEXT_MEAL = 0
        const val MODE_FULL_DAY = 1
    }
}