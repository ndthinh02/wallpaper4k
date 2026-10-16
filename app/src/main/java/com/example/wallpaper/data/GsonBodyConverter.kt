package com.example.wallpaper.data

import com.google.gson.Gson
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/** The API only consumes GET responses; no request-body serialization is needed. */
class GsonBodyConverter(private val gson: Gson) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *> {
        return Converter<ResponseBody, Any> { body -> body.use { gson.fromJson(it.charStream(), type) } }
    }
}
