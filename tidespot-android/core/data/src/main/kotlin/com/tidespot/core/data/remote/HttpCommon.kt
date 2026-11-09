package com.tidespot.core.data.remote

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * M3 新增 HTTP 客户端的公共组装点（不改动 M2 既有类公开签名）。
 * - UA 统一拦截器注入（§12 / CONTRACT 冻结点 5）；
 * - followRedirects(true)：§5.8 坑——RG-A 实测 307 跳转备用域，HTTP 客户端必须跟随。
 */
internal fun tideSpotHttpClient(callTimeoutSec: Long): OkHttpClient =
    OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", TideSpotUserAgent.VALUE)
                    .build()
            )
        }
        .build()

internal fun getRequest(url: HttpUrl): Request = Request.Builder().url(url).get().build()

internal fun postFormRequest(url: HttpUrl, form: FormBody): Request =
    Request.Builder().url(url).post(form).build()

/** 执行请求并返回 (HTTP 状态码, UTF-8 响应体)；网络异常向调用方抛出（由各客户端自行降级）。 */
internal suspend fun httpExecute(
    http: OkHttpClient,
    request: Request,
    ioDispatcher: CoroutineDispatcher,
): Pair<Int, String> = withContext(ioDispatcher) {
    http.newCall(request).execute().use { resp ->
        resp.code to (resp.body?.string().orEmpty())
    }
}
