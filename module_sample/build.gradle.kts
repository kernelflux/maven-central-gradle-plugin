plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.sonatype.uploader)
}

val sGroupId = "com.kernelflux.sdk"
val sArtifactId = "sample"
val sVersion = "1.0.0"


android {
    namespace = "com.kernelflux.sdk.sample"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}


sonatypeUpload {
    uploadBundleName = "export_kv_bundle_v${sVersion}"
    username = findProperty("sonatypeUsername") as? String
    password = findProperty("sonatypePassword") as? String

    groupId = sGroupId
    artifactId = sArtifactId
    version = sVersion

    signingKeyFile = rootProject.rootDir.absolutePath + findProperty("signingKeyFile") as? String
    signingPass = findProperty("signingPass") as? String

    pom = Action<MavenPom> {
        name.set(sArtifactId)
        description.set("This is a sdk sample module")
        version.set(sVersion)
        url.set("https://github.com/kernelflux")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("kernelflux")
                name.set("0kt12")
            }
        }
        scm {
            url.set("https://github.com/kernelflux")
            connection.set("https://github.com/kernelflux")
            developerConnection.set("https://github.com/kernelflux")
        }
    }
}