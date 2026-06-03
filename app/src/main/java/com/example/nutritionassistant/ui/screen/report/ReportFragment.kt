package com.example.nutritionassistant.ui.screen.report

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.nutritionassistant.R
import com.example.nutritionassistant.data.repository.FoodRepository.DaySummary
import com.example.nutritionassistant.databinding.FragmentReportBinding
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.map
import kotlin.collections.mapIndexed

@AndroidEntryPoint
class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportViewModel by viewModels()
    private var isAiAnalysisRequested = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chartCalories.setNoDataText("暂无数据，请先记录饮食")
        binding.chartProtein.setNoDataText("暂无数据，请先记录饮食")
        binding.chartFat.setNoDataText("暂无数据，请先记录饮食")
        binding.chartCarbs.setNoDataText("暂无数据，请先记录饮食")

        initChart()

        viewModel.aiAnalysis.observe(viewLifecycleOwner) { analysis ->
            if (analysis.isNotEmpty()) {
                binding.tvAiAnalysis.text = analysis
                binding.tvAiAnalysis.visibility = View.VISIBLE
                if (isAiAnalysisRequested) {
                    binding.tvAiStatus.text = "\uD83D\uDCA1 智能洞察(已生成)"
                } else {
                    binding.tvAiStatus.text = "\uD83D\uDCA1 智能洞察(点击生成)"
                }
            }
        }

        binding.cardAiAnalysis.setOnClickListener {
            if (!isAiAnalysisRequested) {
                viewModel.requestAiAnalysis()
                isAiAnalysisRequested = true
                binding.tvAiStatus.text = "生成中..."
            }
        }



    }

    private fun initChart() {
        // 当周数据到达时，直接绘制图表（如果目标值已加载）
        viewModel.weeklyData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                val goal = viewModel.dailyGoal.value
                setupChart(
                    binding.chartCalories,
                    data,
                    "热量",
                    { it.totalCalories },
                    goal?.calories ?: 0f
                )

                setupChart(
                    binding.chartProtein,
                    data,
                    "蛋白质",
                    { it.totalProtein },
                    goal?.protein ?: 0f
                )

                setupChart(
                    binding.chartFat,
                    data,
                    "脂肪",
                    { it.totalFat },
                    goal?.fat ?: 0f,
                )

                setupChart(
                    binding.chartCarbs,
                    data,
                    "碳水",
                    { it.totalCarbs },
                    goal?.carbs ?: 0f,
                )
            }
        }

        // 当目标值到达时，如果周数据已存在，重新绘制（补上目标线）
        viewModel.dailyGoal.observe(viewLifecycleOwner) { goal ->
            android.util.Log.d("ReportDebug", "目标热量: ${goal.calories}")
            val data = viewModel.weeklyData.value
            if (data != null && data.isNotEmpty()) {
                setupChart(binding.chartCalories, data, "热量", { it.totalCalories }, goal.calories)
                setupChart(binding.chartProtein, data, "蛋白质", { it.totalProtein }, goal.protein)
                setupChart(binding.chartFat, data, "脂肪", { it.totalFat }, goal.fat)
                setupChart(binding.chartCarbs, data, "碳水", { it.totalCarbs }, goal.carbs)
            }
        }
    }

    private fun setupChart(
        chart: com.github.mikephil.charting.charts.LineChart,
        data: List<DaySummary>,
        label: String,
        valueSelector: (DaySummary) -> Float,
        goalValue: Float
    ) {

        chart.clear()
        chart.axisLeft.removeAllLimitLines()


        val entries = data.mapIndexed { index, day ->
            Entry(index.toFloat(), valueSelector(day))
        }

        val dataSet = LineDataSet(entries, label).apply {
            color = Color.rgb(76, 175, 80)
            valueTextSize = 10f
            lineWidth = 2f
            setDrawCircles(true)
            setDrawValues(false)

            when(label) {
                "热量" -> color = Color.rgb(255, 87, 34)    // 橙色
                "蛋白质" -> color = Color.rgb(76, 175, 80)  // 绿色
                "脂肪" -> color = Color.rgb(255, 193, 7)    // 黄色
                "碳水" -> color = Color.rgb(33, 150, 243)   // 蓝色
            }

        }
        if (goalValue > 0) {
            val limitLine = LimitLine(goalValue, "目标：${goalValue.toInt()}")
            limitLine.apply {
                lineWidth = 1f
                lineColor = ContextCompat.getColor(requireContext(), R.color.state_danger)
                textSize = 10f
                textColor = ContextCompat.getColor(requireContext(), R.color.state_danger)
                enableDashedLine(10f, 10f, 0f)
            }
            chart.axisLeft.addLimitLine(limitLine)
        }

        chart.apply {
            this.data = LineData(dataSet)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(data.map {
                    SimpleDateFormat("MM/dd", Locale.CHINA).format(Date(it.date))
                })
                granularity = 1f
            }

            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            description.isEnabled = false

            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}