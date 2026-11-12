package com.yourname.buggame.gold

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

// Сервис ЦБ РФ с ценами на драгоценные металлы (ответ — XML)
interface GoldApi {

    @GET("scripts/xml_metall.asp")
    fun metals(
        @Query("date_req1") from: String,
        @Query("date_req2") to: String
    ): Call<ResponseBody>
}
