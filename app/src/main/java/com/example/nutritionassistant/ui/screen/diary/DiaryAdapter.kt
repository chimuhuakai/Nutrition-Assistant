package com.example.nutritionassistant.ui.screen.diary

import DiaryGroup
import android.R.id.message
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nutritionassistant.R
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import com.example.nutritionassistant.databinding.DialogDailySummaryBinding
import com.example.nutritionassistant.databinding.DialogDeleteFoodBinding
import com.example.nutritionassistant.databinding.DialogFoodDetailBinding
import com.example.nutritionassistant.databinding.ItemDiaryHeaderBinding
import com.example.nutritionassistant.databinding.ItemFoodRecordBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 饮食日记列表适配器
 * 功能：
 * 1. 显示【日期标题头】（如：2025年05月21日）
 * 2. 显示【食物记录条目】
 * 3. 支持删除按钮点击
 * 4. 自动刷新列表（DiffUtil）
 */
class DiaryAdapter(
    private val onDelete: (FoodRecordEntity) -> Unit, // 删除回调
    private val onMealTagClick: (FoodRecordEntity) -> Unit,  // 新增
    ) : ListAdapter<DiaryItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        // 列表类型：日期头
        private const val TYPE_HEADER = 0

        // 列表类型：食物记录
        private const val TYPE_RECORD = 1

        // DiffUtil：高效刷新列表，只更新变化部分
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DiaryItem>() {
            override fun areItemsTheSame(oldItem: DiaryItem, newItem: DiaryItem): Boolean {
                // 比较唯一ID
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: DiaryItem, newItem: DiaryItem): Boolean {
                // 比较内容是否完全一致
                return oldItem == newItem
            }
        }
    }

    // 返回当前位置的条目类型（头 / 记录）
    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isHeader) TYPE_HEADER else TYPE_RECORD
    }

    // 创建视图（根据类型加载不同布局）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            // 日期头
            HeaderViewHolder(ItemDiaryHeaderBinding.inflate(inflater, parent, false))
        } else {
            // 食物记录
            RecordViewHolder(ItemFoodRecordBinding.inflate(inflater, parent, false))
        }
    }

    // 绑定数据到界面
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item.date,item.groupItems?:emptyList())
            is RecordViewHolder -> holder.bind(item.record!!, onDelete,onMealTagClick)
        }
    }

    // =============== 日期头 ViewHolder ===============
    class HeaderViewHolder(private val binding: ItemDiaryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: Long,items: List<FoodRecordEntity>) {
            // 时间戳 → 日期字符串 2025年05月21日
            val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
            binding.textHeader.text = sdf.format(Date(date))
            binding.btnDetail.setOnClickListener {
                showDaySummary(items, binding.root.context, date)
            }
        }

        private fun showDaySummary(items: List<FoodRecordEntity>, context: Context, date: Long) {
            val totalCal = items.sumOf { it.calories.toDouble() }
            val totalProtein = items.sumOf { it.protein.toDouble() }
            val totalFat = items.sumOf { it.fat.toDouble() }
            val totalCarbs = items.sumOf { it.carbs.toDouble() }
            val mealCount = items.size
            val dateStr = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date(date))
            val dialogBinding = DialogDailySummaryBinding.inflate(LayoutInflater.from(context), binding.root, false)
            dialogBinding.apply {
                tvSummaryDate.text = dateStr
                tvSummaryCalories.text = totalCal.toInt().toString()
                tvSummaryProtein.text = totalProtein.toInt().toString()
                tvSummaryFat.text = totalFat.toInt().toString()
                tvSummaryCarbs.text = totalCarbs.toInt().toString()
                tvSummaryCountTag.text = "共 ${mealCount} 条记录"
            }

            val dialog = AlertDialog.Builder(context)
                .setView(dialogBinding.root)
                .show()

            dialogBinding.btnSummaryClose.setOnClickListener { dialog.dismiss() }

        }
    }

    // =============== 食物记录 ViewHolder ===============
    class RecordViewHolder(private val binding: ItemFoodRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(record: FoodRecordEntity,
                 onDelete: (FoodRecordEntity) -> Unit,
                 onMealTagClick: (FoodRecordEntity) -> Unit) {
            binding.textFoodName.text = record.name
            binding.textNutrition.text =
                "${record.calories.toInt()} kcal | ${record.protein.toInt()}g 蛋白质"

            val mealInfo = getMealInfo(record.mealType)

            binding.textMealTag.text = mealInfo.first
            binding.textMealTag.backgroundTintList = ColorStateList.valueOf(getColor(binding.root.context, mealInfo.second))
            binding.textMealTag.setTextColor(getColor(binding.root.context, mealInfo.third))

            binding.textMealTag.setOnClickListener { onMealTagClick(record) }

            binding.btnDelete.setOnClickListener { showDeleteDialog(record, onDelete)}



            binding.apply {
                root.setOnClickListener {
                    showDetailDialog(record)
                }
            }
        }

        fun showDeleteDialog(record: FoodRecordEntity, onDelete: (FoodRecordEntity) -> Unit) {

            val dialogBinding = DialogDeleteFoodBinding.inflate(LayoutInflater.from(binding.root.context), binding.root, false)

            val dialog = AlertDialog.Builder(binding.root.context)
                .setView(dialogBinding.root)
                .show()

            dialogBinding.apply {
                btnConfirm.setOnClickListener {
                    onDelete(record)
                    Toast.makeText(binding.root.context, "删除成功", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                btnCancel.setOnClickListener { dialog.dismiss() }

                tvDeleteFoodName.text = record.name
            }
        }



        fun showDetailDialog(record: FoodRecordEntity) {
            val context = binding.root.context
            val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)

            val mealInfo = getMealInfo(record.mealType)
            val mealTypeText = mealInfo.first

            val dialogBinding = DialogFoodDetailBinding.inflate(LayoutInflater.from(context), binding.root, false)

            val dialog = AlertDialog.Builder(context)
                .setView(dialogBinding.root)
                .show()

            dialogBinding.apply {
                tvDetailMealTag.text = mealTypeText
                tvDetailMealTag.backgroundTintList = ColorStateList.valueOf(getColor(context, mealInfo.second))
                tvDetailMealTag.setTextColor(getColor(context, mealInfo.third))
                tvDetailTime.text = "记录于 ${dateFormat.format(Date(record.date))}"
                tvDetailName.text = record.name
                tvDetailCalories.text = String.format("%.2f", record.calories)
                tvDetailProtein.text = String.format("%.2f", record.protein)
                tvDetailFat.text = String.format("%.2f", record.fat)
                tvDetailCarbs.text = String.format("%.2f", record.carbs)
                btnDetailClose.setOnClickListener { dialog.dismiss() }
            }
        }
    }
}

// ===================== 列表条目包装类 =====================
/**
 * 统一列表数据类型
 * 一个 DiaryItem 要么是日期头，要么是食物记录
 */
data class DiaryItem(
    val id: Long,
    val isHeader: Boolean,
    val date: Long = 0,
    val record: FoodRecordEntity? = null,
    val groupItems: List<FoodRecordEntity>? = null  // 仅header有效
)

// ===================== 分组数据转扁平列表 =====================
/**
 * 把按日期分组的 DiaryGroup
 * 转换成列表可直接显示的扁平结构：
 * 日期头 → 食物1 → 食物2 → 日期头 → 食物3...
 */
fun List<DiaryGroup>.toFlatList(): List<DiaryItem> {
    val mealOrder = mapOf("breakfast" to 0, "lunch" to 1, "dinner" to 2, "snack" to 3)
    val list = mutableListOf<DiaryItem>()
    for (group in this) {
        // 添加日期头
        list.add(DiaryItem(id = group.date, isHeader = true, date = group.date, groupItems = group.items))
        // 按餐别排序后添加食物记录
        val sortedItems = group.items.sortedBy { mealOrder[it.mealType] ?: 4 }
        for (record in sortedItems) {
            list.add(DiaryItem(id = record.id.toLong(), isHeader = false, record = record))
        }
    }
    return list
}

private fun getMealInfo(mealType: String): Triple<String, Int, Int> {
    return when (mealType) {
        "breakfast" -> Triple("早餐", R.color.tag_breakfast_bg, R.color.tag_breakfast_text)
        "lunch" -> Triple("午餐", R.color.tag_lunch_bg, R.color.tag_lunch_text)
        "dinner" -> Triple("晚餐", R.color.tag_dinner_bg, R.color.tag_dinner_text)
        else -> Triple("加餐", R.color.tag_snack_bg, R.color.tag_snack_text)
    }
}



