package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitTreeResponse(
    @Json(name = "sha") val sha: String,
    @Json(name = "url") val url: String,
    @Json(name = "tree") val tree: List<GitTreeEntry>,
    @Json(name = "truncated") val truncated: Boolean
)

@JsonClass(generateAdapter = true)
data class GitTreeEntry(
    @Json(name = "path") val path: String,
    @Json(name = "mode") val mode: String,
    @Json(name = "type") val type: String, // "blob" or "tree"
    @Json(name = "sha") val sha: String,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "url") val url: String
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    @Json(name = "type") val type: String,
    @Json(name = "encoding") val encoding: String, // e.g. "base64"
    @Json(name = "size") val size: Long,
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "content") val content: String?, // raw file content, base64 encoded
    @Json(name = "sha") val sha: String,
    @Json(name = "download_url") val downloadUrl: String?
)

@JsonClass(generateAdapter = true)
data class CommitRequest(
    @Json(name = "message") val message: String,
    @Json(name = "content") val content: String, // base64 encoded text
    @Json(name = "sha") val sha: String, // previous SHA of the file
    @Json(name = "branch") val branch: String? = null
)

@JsonClass(generateAdapter = true)
data class CommitResponse(
    @Json(name = "content") val content: CommitContentInfo?,
    @Json(name = "commit") val commit: CommitInfo?
)

@JsonClass(generateAdapter = true)
data class CommitContentInfo(
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "sha") val sha: String
)

@JsonClass(generateAdapter = true)
data class CommitInfo(
    @Json(name = "sha") val sha: String,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class UserResponse(
    @Json(name = "login") val login: String,
    @Json(name = "avatar_url") val avatarUrl: String?
)

@JsonClass(generateAdapter = true)
data class DeviceCodeResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_uri") val verificationUri: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "interval") val interval: Int
)

@JsonClass(generateAdapter = true)
data class AccessTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "scope") val scope: String? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null
)

@JsonClass(generateAdapter = true)
data class RepositoryResponse(
    @Json(name = "default_branch") val defaultBranch: String
)
