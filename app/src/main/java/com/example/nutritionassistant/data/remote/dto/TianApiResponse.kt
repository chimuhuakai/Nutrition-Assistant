package com.example.nutritionassistant.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 条形码识别接口 返回的完整数据结构
 * 用途：扫描商品条形码 → 获取商品名称、品牌、规格
 */
@JsonClass(generateAdapter = true)  // Moshi自动生成解析适配器（必须加）
data class BarcodeResponse(
    @Json(name = "code") val code: Int,          // 接口返回状态码 200=成功
    @Json(name = "msg") val msg: String,         // 返回提示信息（如“查询成功”）
    @Json(name = "result") val result: BarcodeResult?  // 真正的商品数据（可能为空）
)

/**
 * 条形码识别 → 具体商品信息
 */
@JsonClass(generateAdapter = true)   // ← 这个注解别忘了！
data class BarcodeResult(
    @Json(name = "barcode") val barcode: String?,     // 条形码号
    @Json(name = "name") val name: String?,           // 商品名称
    @Json(name = "brand") val brand: String?,         // 商品品牌
    @Json(name = "spec") val spec: String?,           // 商品规格（如500ml）
    @Json(name = "firm_name") val firmName: String?,  // 生产厂商
    @Json(name = "goods_type") val goodsType: String?,// 商品分类
    @Json(name = "goods_pic") val goodsPic: String?   // 商品图片URL
)

/**
 * 食材营养成分查询接口 返回的完整数据结构
 * 用途：输入食物名称 → 获取热量、蛋白质、脂肪、碳水
 */
@JsonClass(generateAdapter = true)
data class NutrientResponse(
    @Json(name = "code") val code: Int,          // 状态码
    @Json(name = "msg") val msg: String,         // 提示信息
    @Json(name = "result") val result: NutrientResult?  // 食材列表数据
)

/**
 * 食材查询 → 包含食物列表
 */
data class NutrientResult(
    @Json(name = "list") val list: List<NutrientItem>?  // 食材列表数组
)

/**
 * 单个食材的营养信息
 */
data class NutrientItem(
    @Json(name = "name") val name: String,            // 食物名称（不可空）
    @Json(name = "rl") val calorie: String?,     // 热量(千卡)
    @Json(name = "dbz") val protein: String?,     // 蛋白质(g)
    @Json(name = "zf") val fat: String?,             // 脂肪(g)
    @Json(name = "shhf") val carbohydrate: String? // 碳水化合物(g)
)

/**
 * 菜品拍照识别接口 返回结构
 * 用途：拍一张菜的照片 → 识别菜名 + 营养信息
 */
@JsonClass(generateAdapter = true)
data class ImgCaipinResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String,
    @Json(name = "result") val result: ImgCaipinResult?
)

/**
 * 菜品识别 → 识别结果列表
 */
data class ImgCaipinResult(
    @Json(name = "list") val list: List<CaipinItem>?
)

/**
 * 识别出的菜品 + 营养数据
 */
data class CaipinItem(
    @Json(name = "name") val name: String,           // 菜名
    @Json(name = "calorie") val calorie: String?,    // 热量
    @Json(name = "protein") val protein: String?,    // 蛋白质
    @Json(name = "fat") val fat: String?,            // 脂肪
    @Json(name = "carbohydrate") val carbohydrate: String? // 碳水
)

@JsonClass(generateAdapter = true)
data class RecipeResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "msg") val msg: String,
    @Json(name = "result") val result: RecipeResult?
)

@JsonClass(generateAdapter = true)
data class RecipeResult(
    @Json(name = "list") val list: List<RecipeItem>?
)

@JsonClass(generateAdapter = true)
data class RecipeItem(
    @Json(name = "cp_name") val cpName: String?,
    @Json(name = "yuanliao") val yuanliao: String?,
    @Json(name = "tiaoliao") val tiaoliao: String?,
    @Json(name = "zuofa") val zuofa: String?,
    @Json(name = "texing") val texing: String?,
    @Json(name = "tishi") val tishi: String?,
    @Json(name = "type_name") val typeName: String?
)