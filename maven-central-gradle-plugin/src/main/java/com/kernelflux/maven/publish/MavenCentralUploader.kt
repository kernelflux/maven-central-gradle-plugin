package com.kernelflux.maven.publish

import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.UUID

/**
 * Helper class: calling sonatype api to upload file.
 */
object MavenCentralUploader {
    private const val REPO_REQUEST_HOST = "https://central.sonatype.com"

    private const val REPO_UPLOAD_URL = "/api/v1/publisher/upload"

    private const val REPO_STATUS_VERIFY_URL = "/api/v1/publisher/status"
    private const val REPO_DEPLOY_URL = "/api/v1/publisher/deployment"

    private const val AUTO_DEPLOY = true


    private fun generateBoundary(): String {
        // Generate a random boundary using UUID.
        return "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "")
    }


    /**
     * Helper method: Write a file to a multipart request
     */
    @JvmStatic
    private fun writeFile(
        output: DataOutputStream,
        file: File,
        boundary: String
    ) {
        writeMultipartField(
            output,
            "publishingType",
            if (AUTO_DEPLOY) "AUTOMATIC" else "USER_MANAGED",
            boundary
        )
        writeMultipartField(output, "bundle", file, boundary)
        output.writeBytes("--$boundary--\r\n")

    }

    @JvmStatic
    private fun writeMultipartField(
        output: DataOutputStream,
        fieldName: String,
        value: Any,
        boundary: String
    ) {
        output.writeBytes("\r\n--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"")

        if (value is File) {
            output.writeBytes("; filename=\"${value.name}\"\r\n")
            output.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
            value.inputStream().use { it.copyTo(output) }
        } else {
            output.writeBytes("\r\n\r\n$value\r\n")
        }
    }


    @JvmStatic
    fun uploadArtifact(
        zipFile: File,
        username: String,
        password: String
    ) {
        if (!zipFile.exists()) {
            throw FileNotFoundException("Upload file not found :${zipFile.absolutePath}")
        }
        val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        val repoUploadRequestUrl = REPO_REQUEST_HOST + REPO_UPLOAD_URL
        val url = URI(repoUploadRequestUrl).toURL()

        val boundary = generateBoundary()

        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.doInput = true

        connection.setRequestProperty("Authorization", "Bearer $auth")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        DataOutputStream(connection.outputStream).use { output ->
            writeFile(output, zipFile, boundary)
            output.writeBytes("\r\n--$boundary--\r\n")
        }

        val responseCode = connection.responseCode
        val deploymentId = connection.inputStream.bufferedReader().use { it.readText() }

        if (responseCode in 200..299) {
            println("✅ Sonatype upload successful: $deploymentId")
        } else {
            println("❌ Sonatype upload failed: HTTP $responseCode\n$deploymentId")
        }

    }


}