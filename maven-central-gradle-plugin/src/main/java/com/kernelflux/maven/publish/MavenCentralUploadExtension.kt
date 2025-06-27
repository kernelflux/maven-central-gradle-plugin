package com.kernelflux.maven.publish

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom
import javax.inject.Inject


/**
 * `MavenCentralUploadExtension` is a extension config class of MavenCentralPlugin.
 *
 * ## config:
 * - uploadBundleName: sonatype bundle name of uploading.
 * - username: sonatype auth user name
 * - password: sonatype auth user password
 * - signingKeyFile: gpg signing key file path
 * - signingPass: gpg signing password
 */

open class MavenCentralUploadExtension @Inject constructor(objects: ObjectFactory) {
    val uploadBundleName: Property<String> =
        objects.property(String::class.java).convention("maven-central-upload-bundle")

    val username: Property<String> = objects.property(String::class.java).convention("")
    val password: Property<String> = objects.property(String::class.java).convention("")

    val signingKeyFile: Property<String> = objects.property(String::class.java).convention("")
    val signingPass: Property<String> = objects.property(String::class.java).convention("")

    val groupId: Property<String> = objects.property(String::class.java).convention("")
    val artifactId: Property<String> = objects.property(String::class.java).convention("")
    val version: Property<String> = objects.property(String::class.java).convention("")

    private fun <T> propertyOf(objects: ObjectFactory): Property<Action<T>> {
        @Suppress("UNCHECKED_CAST")
        return objects.property(Action::class.java) as Property<Action<T>>
    }

    val pom: Property<Action<MavenPom>> = propertyOf(objects)
}