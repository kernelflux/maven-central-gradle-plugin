package com.kernelflux.maven.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.signing.SigningExtension
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SonatypeUploadPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val isApplication = project.plugins.hasPlugin("com.android.application")
        if (isApplication) {
            error("⚠️ Application Module (${project.name}) Publishing to Maven is not supported, configuration skipped.")
        }

        // 1. config maven-publish plugin
        if (!project.pluginManager.hasPlugin("maven-publish")) {
            project.pluginManager.apply("maven-publish")
        }

        val isAndroid = project.plugins.hasPlugin("com.android.library")
        val isKotlin =
            project.plugins.hasPlugin("kotlin") || project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")
        val isJava = project.plugins.hasPlugin("java") || project.plugins.hasPlugin("java-library")
        val isPlugin =
            project.plugins.hasPlugin("java-gradle-plugin") || project.plugins.hasPlugin("kotlin-dsl")

        // 2. config signing plugin
        if (!project.pluginManager.hasPlugin("signing")) {
            project.pluginManager.apply("signing")
        }

        // 3. create plugin extension
        val extensionInput =
            project.extensions.create("sonatypeUpload", SonatypeUploadExtension::class.java)

        // 4. config maven-publish
        project.afterEvaluate {
            project.extensions.configure(PublishingExtension::class.java) {
                publications {
                    create("release", MavenPublication::class.java) {
                        groupId = extensionInput.groupId.get()
                        version = extensionInput.version.get()
                        artifactId = extensionInput.artifactId.get()
                        if (isAndroid) {
                            from(components["release"])
                        } else if (isPlugin || isKotlin || isJava) {
                            from(components["java"])
                        }
                        artifact(tasks["sourcesJar"])
                        artifact(tasks["javadocJar"])

                        extensionInput.pom.orNull?.let {
                            pom(it)
                        }
                    }
                }

                repositories {
                    mavenLocal()
                }
            }


            project.extensions.configure<SigningExtension>("signing") {
                val signingKeyFilePath = extensionInput.signingKeyFile.orNull
                    ?: project.findProperty("signingKeyFilePath") as? String
                val signingPass = extensionInput.signingPass.orNull
                    ?: project.findProperty("signingPassword") as? String
                val secretKey = signingKeyFilePath?.let { readFile(it) }
                useGpgCmd()
                useInMemoryPgpKeys(secretKey, signingPass)
                sign(project.extensions.getByType(PublishingExtension::class.java).publications)
            }

        }


        // 5. register tasks for uploading to Sonatype, documentation, and source tasks
        project.tasks.register("deployToSonatype") {
            group = "Publishing"
            description = "Uploads artifacts to Sonatype Central Repository."
            dependsOn("publishReleasePublicationToMavenLocal")
            doLast {
                uploadToSonatype(project, extensionInput, isPlugin)
            }
        }
    }


    private fun readFile(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) {
            return null
        }
        return try {
            file.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * upload to Sonatype
     */
    private fun uploadToSonatype(
        project: Project,
        extension: SonatypeUploadExtension,
        isPlugin: Boolean
    ) {
        val usernameValue = extension.username.orNull
        val passwordValue = extension.password.orNull

        val groupId = extension.groupId.orNull
        val artifactId = extension.artifactId.orNull
        val version = extension.version.orNull

        println("groupId:$groupId\nartifactId:$artifactId\nversion:$version")

        if (usernameValue.isNullOrEmpty() ||
            passwordValue.isNullOrEmpty() ||
            groupId.isNullOrEmpty() ||
            artifactId.isNullOrEmpty() ||
            version.isNullOrEmpty()
        ) {
            error("sonatypeUpload is not configured...")
        }

        val localRepo = File(System.getProperty("user.home"), ".m2/repository")
        val artifactFile = File(localRepo, "${groupId.replace(".", "/")}/$artifactId/$version")
        println("artifactFile : ${artifactFile.absolutePath}")

        if (!artifactFile.exists() || !artifactFile.isDirectory) {
            error("❌ local artifact directory for upload not found")
        }

        artifactFile.listFiles()?.forEach { file ->
            if (file.extension in listOf("jar", "pom", "aar", "module")) {
                generateChecksum(file, "md5")
                generateChecksum(file, "sha1")
                generateChecksum(file, "sha256")
            }
        }

        val tempDir = Files.createTempDirectory("sonatype").toFile()
        artifactFile.walkTopDown().forEach { file ->
            val relativeFilePath = file.relativeTo(localRepo).path
            println("tempDir : $relativeFilePath")
            val targetFile = File(tempDir, relativeFilePath)
            if (file.isDirectory) {
                targetFile.mkdirs()
            } else {
                targetFile.parentFile?.mkdirs()
                println("artifactFile copy: ${targetFile.absolutePath}")
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        val zipFile = File(
            project.layout.buildDirectory.get().asFile.absolutePath,
            "${extension.uploadBundleName.get()}.zip"
        )
        createZipBundle(tempDir, zipFile)

        SonatypeUploader.uploadArtifact(
            zipFile,
            usernameValue,
            passwordValue
        )
    }

    private fun createZipBundle(sourceDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
            sourceDir.walkTopDown().forEach { file ->
                val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                if (file.isDirectory) {
                    zipOut.putNextEntry(ZipEntry("$entryName/"))
                    zipOut.closeEntry()
                } else {
                    zipOut.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun generateChecksum(file: File, algorithm: String) {
        val checksum = file.inputStream().use { input ->
            MessageDigest.getInstance(algorithm).digest(input.readBytes())
                .joinToString("") { "%02x".format(it) }
        }
        File(file.absolutePath + ".$algorithm").writeText(checksum)
    }
}