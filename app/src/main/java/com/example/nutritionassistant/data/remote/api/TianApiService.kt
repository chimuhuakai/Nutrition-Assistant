package com.example.nutritionassistant.data.remote.api

import com.example.nutritionassistant.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * 天聚数行 API 接口定义
 * 作用：统一管理所有网络请求（条形码、食材营养、菜品识别）
 * 由 Retrofit 自动实现具体网络请求逻辑
 */
interface TianApiService {

    /**
     * 1. 条形码识别接口（GET 请求）
     * 功能：传入商品条形码 → 返回商品名称、品牌、规格
     * @param barcode 商品条形码编号
     * @return BarcodeResponse 条形码识别结果
     */
    @GET("barcode/index")
    suspend fun getBarcodeInfo(
        @Query("barcode") barcode: String
    ): BarcodeResponse

    /**
     * 2. 食材营养成分查询（GET 请求）
     * 功能：传入食物名称 → 返回热量、蛋白质、脂肪、碳水
     * @param foodName 食物名称（如：苹果、鸡蛋）
     * @return NutrientResponse 营养信息结果
     */
    @GET("nutrient/index")
    suspend fun getNutrient(
        @Query("word") foodName: String,
        @Query("mode") mode: Int = 0   // ← 补上这个参数
    ): NutrientResponse

    /**
     * 3. 菜品拍照识别（POST 请求，上传图片）
     * 功能：上传菜品图片 → AI 识别菜名 + 营养信息
     * @param img 图片文件（以 Multipart 形式上传）
     * @return ImgCaipinResponse 菜品识别结果
     */
    @FormUrlEncoded
    @POST("imgcaipin/index")
    suspend fun recognizeFood(
        @Field("img") base64Image: String
    ): ImgCaipinResponse

    @GET("caipu/index")
    suspend fun getRecipe(
        @Query("word") recipeName: String,
        @Query("num") num: Int = 1
    ): RecipeResponse


}