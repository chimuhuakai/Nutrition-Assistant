import com.example.nutritionassistant.data.local.entity.FoodRecordEntity

data class DiaryGroup(
    val date: Long,
    val items: List<FoodRecordEntity>
)