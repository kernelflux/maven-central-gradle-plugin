package com.kernelflux.maven.publish

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


/**
 * `MavenCentralPlugin` is a Gradle plugin for simplifying the publishing process
 * of Kotlin/Java/Android libraries to Maven Central.
 *
 * ## Features:
 * - Auto-configures `maven-publish` and `signing`
 * - Compatible with AGP 8.x
 * - One-click publishing to Maven Central
 *
 * ## Usage:
 * ```kotlin
 * plugins {
 *     id("com.kernelflux.maven.publish") version "1.0.1"
 * }
 * ```
 */
class MavenCentralPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val isApplication = project.plugins.hasPlugin("com.android.application")
        if (isApplication) {
            error("⚠️ Application Module (${project.name}) Publishing to Maven is not supported, configuration skipped.")
        }
        val isAndroid = project.plugins.hasPlugin("com.android.library")
        val isKotlin =
            project.plugins.hasPlugin("kotlin") || project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")
        val isJava = project.plugins.hasPlugin("java") || project.plugins.hasPlugin("java-library")
        val isPlugin =
            project.plugins.hasPlugin("java-gradle-plugin") || project.plugins.hasPlugin("kotlin-dsl")

        if (!isAndroid && !isKotlin && !isJava && !isPlugin) {
            error("⚠️ Only support Android/Kotlin/Java/Plugin Modules, configuration skipped.")
        }

        // 1. config maven-publish plugin
        if (!project.pluginManager.hasPlugin("maven-publish")) {
            project.pluginManager.apply("maven-publish")
        }
        // 2. config signing plugin
        if (!project.pluginManager.hasPlugin("signing")) {
            project.pluginManager.apply("signing")
        }

        // 3. config Dokka plugin
        if (!project.pluginManager.hasPlugin("org.jetbrains.dokka")) {
            project.pluginManager.apply("org.jetbrains.dokka")
        }

        // 4. create plugin extension
        val extensionInput =
            project.extensions.create("mavenCentralUpload", MavenCentralUploadExtension::class.java)

        // 5. config maven-publish
        project.afterEvaluate {
            project.extensions.configure(PublishingExtension::class.java) {
                publications {
                    create("release", MavenPublication::class.java) {
                        groupId = extensionInput.groupId.get()
                        version = extensionInput.version.get()
                        artifactId = extensionInput.artifactId.get()
                        if (isAndroid) {
                            from(components["release"])
                        } else {
                            from(components["java"])
                        }
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

            // ensure generateMetadataFileForReleasePublication task is executed after sourcesJar task
            project.tasks.named("generateMetadataFileForReleasePublication") {
                println("generateMetadataFileForReleasePublication...")

                if (project.tasks.findByName("sourcesJar") != null) {
                    dependsOn(tasks["sourcesJar"])
                }
                if (project.tasks.findByName("javadocJar") != null) {
                    dependsOn(tasks["javadocJar"])
                }
            }
        }


        // 6. register tasks for uploading to Maven Central, documentation, and source tasks
        project.tasks.register("deployToMavenCentral") {
            group = "Publishing"
            description = "Uploads artifacts to Maven Central Repository."
            dependsOn("publishReleasePublicationToMavenLocal")
            doLast {
                uploadToMavenCentral(project, extensionInput)
            }
        }

        // register Sources JAR task
        if (project.tasks.findByName("sourcesJar") == null) {
            project.tasks.register("sourcesJar", Jar::class.java) {
                archiveClassifier.set("sources")
                if (isAndroid) {
                    val android = project.extensions.findByType(LibraryExtension::class.java)
                        ?: throw IllegalStateException("Android Library Plugin not applied")
                    from(android.sourceSets.findByName("main")?.java?.srcDirs)
                } else {
                    from(
                        project.the(SourceSetContainer::class)
                            .findByName("main")?.allSource?.srcDirs
                    )
                }
            }
        }

        // register dokkaJavadoc task
        val dokkaJavadoc = project.tasks.findByName("dokkaJavadoc") ?: project.tasks.register(
            "dokkaJavadoc",
            DokkaTaskPartial::class.java
        ) {
            outputDirectory.set(project.layout.buildDirectory.dir("dokkaJavadoc"))
        }

        // register Javadoc JAR task
        project.tasks.register("javadocJar", Jar::class.java) {
            archiveClassifier.set("javadoc")
            from(dokkaJavadoc)
            dependsOn(dokkaJavadoc)
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
     * upload to Maven Central
     */
    private fun uploadToMavenCentral(
        project: Project,
        extension: MavenCentralUploadExtension,
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
            error("mavenCentralUpload is not configured...")
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

        val tempDir = Files.createTempDirectory("mavenCentral").toFile()
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

        MavenCentralUploader.uploadArtifact(
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