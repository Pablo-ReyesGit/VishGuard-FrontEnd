package com.vishguard.ai.data.remote

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

data class TokenResponse(
    val access_token: String,
    val token_type: String
)

interface AuthApiService {
    @FormUrlEncoded
    @POST("api/v1/login/access-token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>
}