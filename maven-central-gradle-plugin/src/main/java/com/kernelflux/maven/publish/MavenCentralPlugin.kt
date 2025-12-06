package com.kernelflux.maven.publish

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
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
        // Enable Dokka V2 mode early to avoid deprecation warnings
        // This must be set before the Dokka plugin is applied
        // Set as system property which Gradle will pick up
        val dokkaPluginMode = "org.jetbrains.dokka.experimental.gradle.pluginMode"
        if (System.getProperty(dokkaPluginMode) == null &&
            project.findProperty(dokkaPluginMode) == null
        ) {
            System.setProperty(dokkaPluginMode, "V2EnabledWithHelpers")
        }

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

        // 1. Configure maven-publish plugin
        if (!project.pluginManager.hasPlugin("maven-publish")) {
            project.pluginManager.apply("maven-publish")
        }
        // 2. Configure signing plugin
        if (!project.pluginManager.hasPlugin("signing")) {
            project.pluginManager.apply("signing")
        }

        // 3. Configure Dokka plugin (V2)
        if (!project.pluginManager.hasPlugin("org.jetbrains.dokka")) {
            project.pluginManager.apply("org.jetbrains.dokka")
        }
        // Apply dokka-javadoc plugin for Javadoc format output (required for Maven Central)
        // This is needed to generate Javadoc format documentation
        if (!project.pluginManager.hasPlugin("org.jetbrains.dokka-javadoc")) {
            try {
                project.pluginManager.apply("org.jetbrains.dokka-javadoc")
            } catch (e: Exception) {
                // Plugin may not be available in older Dokka versions, ignore
                project.logger.debug("dokka-javadoc plugin not available: ${e.message}")
            }
        }

        // 4. Create plugin extension
        val extensionInput =
            project.extensions.create("mavenCentralUpload", MavenCentralUploadExtension::class.java)

        // 5. Configure maven-publish
        project.afterEvaluate {
            project.extensions.configure(PublishingExtension::class.java) {
                publications {
                    // Check if release publication already exists, configure it if exists, otherwise create
                    val existingPublication = findByName("release") as? MavenPublication
                    if (existingPublication != null) {
                        // If already exists (may be auto-created by singleVariant), configure it
                        existingPublication.groupId = extensionInput.groupId.get()
                        existingPublication.version = extensionInput.version.get()
                        existingPublication.artifactId = extensionInput.artifactId.get()
                        // Check if sourcesJar and javadocJar artifacts are already added
                        // This happens when singleVariant("release") { withSourcesJar() } is configured
                        // We need to check both classifier and extension to avoid duplicates
                        fun getArtifactProperty(property: Any?): String? {
                            return when (property) {
                                is String -> property
                                is org.gradle.api.provider.Provider<*> -> {
                                    try {
                                        @Suppress("UNCHECKED_CAST")
                                        (property as? org.gradle.api.provider.Provider<String>)?.getOrNull()
                                            ?: property.getOrNull()?.toString()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                else -> property?.toString()
                            }
                        }
                        
                        // Check if sources jar already exists
                        val hasSourcesJar = existingPublication.artifacts.any { artifact ->
                            try {
                                val classifier = getArtifactProperty(artifact.classifier)
                                val extension = getArtifactProperty(artifact.extension)
                                classifier == "sources" && extension == "jar"
                            } catch (e: Exception) {
                                // Fallback: check file name
                                try {
                                    val fileName = artifact.file?.name ?: ""
                                    fileName.contains("sources") && fileName.endsWith(".jar")
                                } catch (ex: Exception) {
                                    false
                                }
                            }
                        }
                        
                        // Check if javadoc jar already exists
                        val hasJavadocJar = existingPublication.artifacts.any { artifact ->
                            try {
                                val classifier = getArtifactProperty(artifact.classifier)
                                val extension = getArtifactProperty(artifact.extension)
                                classifier == "javadoc" && extension == "jar"
                            } catch (e: Exception) {
                                // Fallback: check file name
                                try {
                                    val fileName = artifact.file?.name ?: ""
                                    fileName.contains("javadoc") && fileName.endsWith(".jar")
                                } catch (ex: Exception) {
                                    false
                                }
                            }
                        }
                        
                        // Only add sourcesJar if publication doesn't already have a sources jar artifact
                        // This prevents duplicate when singleVariant withSourcesJar() is used
                        if (!hasSourcesJar && project.tasks.findByName("sourcesJar") != null) {
                            try {
                                existingPublication.artifact(tasks["sourcesJar"])
                            } catch (e: Exception) {
                                // Ignore exception if already added or other error
                                project.logger.debug("Failed to add sourcesJar to publication: ${e.message}")
                            }
                        }
                        if (!hasJavadocJar && project.tasks.findByName("javadocJar") != null) {
                            try {
                                existingPublication.artifact(tasks["javadocJar"])
                            } catch (e: Exception) {
                                // Ignore exception if already added
                                project.logger.debug("Failed to add javadocJar to existing publication: ${e.message}")
                            }
                        }
                        extensionInput.pom.orNull?.let {
                            existingPublication.pom(it)
                        }
                    } else {
                        // If not exists, create new publication
                        create("release", MavenPublication::class.java) {
                            groupId = extensionInput.groupId.get()
                            version = extensionInput.version.get()
                            artifactId = extensionInput.artifactId.get()
                            if (isAndroid) {
                                from(components["release"])
                            } else {
                                from(components["java"])
                            }
                            if (project.tasks.findByName("sourcesJar") != null) {
                                artifact(tasks["sourcesJar"])
                            }
                            if (project.tasks.findByName("javadocJar") != null) {
                                artifact(tasks["javadocJar"])
                            }
                            extensionInput.pom.orNull?.let {
                                pom(it)
                            }
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

            // Ensure generateMetadataFileForReleasePublication task is executed after sourcesJar task
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


        // 6. Register tasks for uploading to Maven Central, documentation, and source tasks
        project.tasks.register("deployToMavenCentral") {
            group = "Maven Central"
            description = "Uploads artifacts to Maven Central Repository."
            dependsOn("publishReleasePublicationToMavenLocal")
            doLast {
                uploadToMavenCentral(project, extensionInput)
            }
        }

        // Register Sources JAR task
        // Note: For Android projects using singleVariant withSourcesJar(),
        // the sources jar is automatically created and added to publication.
        // We only create sourcesJar task if it doesn't exist and is needed.
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

        // Disable Dokka V1 tasks if they exist (we're using V2 mode)
        // With V2 mode enabled via system property, Dokka plugin will create V2 tasks automatically
        // IMPORTANT: Disable dokkaJavadoc BEFORE registering javadocJar to avoid dependency issues
        project.afterEvaluate {
            // Disable all dokkaJavadoc tasks (V1 tasks are disabled in V2 mode)
            project.tasks.matching { it.name == "dokkaJavadoc" }.configureEach {
                enabled = false
            }
            
            // Remove existing javadocJar task if it depends on dokkaJavadoc
            val existingJavadocJar = project.tasks.findByName("javadocJar")
            if (existingJavadocJar != null) {
                // Check if it depends on dokkaJavadoc and remove that dependency
                val dokkaJavadocTask = project.tasks.findByName("dokkaJavadoc")
                if (dokkaJavadocTask != null && existingJavadocJar.dependsOn.contains(dokkaJavadocTask)) {
                    existingJavadocJar.dependsOn.remove(dokkaJavadocTask)
                }
            }
            
            // Register or reconfigure Javadoc JAR task
            val javadocJarTask = if (existingJavadocJar != null) {
                existingJavadocJar as? Jar
            } else {
                project.tasks.register("javadocJar", Jar::class.java) {
                    archiveClassifier.set("javadoc")
                }.get()
            }
            
            if (javadocJarTask != null) {
                javadocJarTask.archiveClassifier.set("javadoc")
                
                var taskFound = false
                
                // For Kotlin JVM projects, use standard javadoc task (most reliable)
                if (!isAndroid && (isKotlin || isJava)) {
                    try {
                        val javadocTask = project.tasks.named("javadoc", org.gradle.api.tasks.javadoc.Javadoc::class.java)
                        // Use the output directory of javadoc task
                        javadocJarTask.from(javadocTask.get().destinationDir)
                        javadocJarTask.dependsOn(javadocTask)
                        taskFound = true
                    } catch (e: Exception) {
                        // Try Dokka V2 Javadoc task (dokkaGeneratePublicationJavadoc)
                        try {
                            val dokkaV2Task = project.tasks.named("dokkaGeneratePublicationJavadoc")
                            javadocJarTask.from(dokkaV2Task)
                            javadocJarTask.dependsOn(dokkaV2Task)
                            taskFound = true
                        } catch (e2: Exception) {
                            project.logger.warn("Neither javadoc nor dokkaGeneratePublicationJavadoc task found: ${e2.message}")
                        }
                    }
                } else {
                    // For Android projects, try Dokka V2 task first
                    try {
                        val dokkaV2Task = project.tasks.named("dokkaGeneratePublicationJavadoc")
                        javadocJarTask.from(dokkaV2Task)
                        javadocJarTask.dependsOn(dokkaV2Task)
                        taskFound = true
                    } catch (e: Exception) {
                        project.logger.warn("dokkaGeneratePublicationJavadoc task not found for Android project: ${e.message}")
                    }
                }
                
                if (!taskFound) {
                    project.logger.warn("javadocJar will be empty. For Kotlin JVM projects, ensure javadoc task exists. For Android projects, apply dokka-javadoc plugin.")
                }
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
     * Upload to Maven Central
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
            // Only copy necessary files: artifacts, signature files (.asc), checksum files (.md5, .sha1, .sha256)
            val fileName = file.name
            val isArtifact = file.extension in listOf("jar", "pom", "aar", "module")
            val isSignature = fileName.endsWith(".asc")
            val isChecksum =
                fileName.endsWith(".md5") || fileName.endsWith(".sha1") || fileName.endsWith(".sha256")

            if (file.isDirectory || isArtifact || isSignature || isChecksum) {
                val relativeFilePath = file.relativeTo(localRepo).path
                println("tempDir : $relativeFilePath")
                val targetFile = File(tempDir, relativeFilePath)
                if (file.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    println("artifactFile copy: ${targetFile.absolutePath}")
                    Files.copy(
                        file.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
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