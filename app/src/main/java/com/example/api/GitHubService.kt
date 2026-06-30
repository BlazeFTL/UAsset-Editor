package com.example.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface GitHubService {

    @POST
    @Headers("Accept: application/json")
    suspend fun getDeviceCode(
        @Url url: String = "https://github.com/login/device/code",
        @Query("client_id") clientId: String,
        @Query("scope") scope: String = "repo,user"
    ): DeviceCodeResponse

    @POST
    @Headers("Accept: application/json")
    suspend fun getAccessToken(
        @Url url: String = "https://github.com/login/oauth/access_token",
        @Query("client_id") clientId: String,
        @Query("device_code") deviceCode: String,
        @Query("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
    ): AccessTokenResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getRepositoryInfo(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): RepositoryResponse

    @GET("user")
    suspend fun getAuthenticatedUser(
        @Header("Authorization") authHeader: String
    ): UserResponse

    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    suspend fun getGitTree(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Query("recursive") recursive: Int = 1
    ): GitTreeResponse

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") authHeader: String?,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String
    ): ContentResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun commitFileChange(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CommitRequest
    ): CommitResponse

    companion object {
        private const val BASE_URL = "https://api.github.com/"

        fun create(): GitHubService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(GitHubService::class.java)
        }
    }
}
