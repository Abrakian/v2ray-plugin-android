import com.android.build.VariantOutput
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.apache.tools.ant.taskdefs.condition.Os
import java.util.*

plugins {
    id("com.android.application")
    kotlin("android")
}

val flavorRegex = "(assemble|generate)\\w*(Release|Debug)".toRegex()
val currentFlavor
    get() = gradle.startParameter.taskRequests.toString().let { task ->
        flavorRegex.find(task)?.groupValues?.get(2)?.toLowerCase(Locale.ROOT) ?: "debug".also {
            println("Warning: No match found for $task")
        }
    }

val minSdk = 21

android {
    val javaVersion = JavaVersion.VERSION_1_8
    compileSdkVersion(30)
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    kotlinOptions.jvmTarget = javaVersion.toString()
    defaultConfig {
        applicationId = "io.nekohasekai.shadowsocks.plugin.v2ray"
        minSdkVersion(23)
        targetSdkVersion(30)
        versionCode = 1030300
        versionName = "1.3.3-git-ddd7ab46b4"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")

            val properties = Properties()
            properties.load(project.rootProject.file("local.properties").bufferedReader())
            storePassword = properties["KEYSTORE_PASS"] as String
            keyAlias = properties["ALIAS_NAME"] as String
            keyPassword = properties["ALIAS_PASS"] as String
        }
    }
    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    splits {
        abi {
            isEnable = true
            isUniversalApk = false
        }
    }
    sourceSets.getByName("main") {
        jniLibs.setSrcDirs(jniLibs.srcDirs + files("$projectDir/build/go"))
    }
}

tasks.register<Exec>("goBuild") {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        println("Warning: Building on Windows is not supported")
    } else {
        executable("/bin/bash")
        args("go-build.bash", minSdk)
        environment("ANDROID_HOME", android.sdkDirectory)
        environment("ANDROID_NDK_HOME", android.ndkDirectory)
    }
}

tasks.whenTaskAdded {
    when (name) {
        "mergeDebugJniLibFolders", "mergeReleaseJniLibFolders" -> dependsOn("goBuild")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8", rootProject.extra.get("kotlinVersion").toString()))
    implementation("androidx.preference:preference:1.1.1")
    implementation("com.github.shadowsocks:plugin:2.0.1")
    implementation(project(":preferencex-simplemenu"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
if (currentFlavor == "release") android.applicationVariants.all {
    for (output in outputs) {
        abiCodes[(output as ApkVariantOutputImpl).getFilter(VariantOutput.ABI)]?.let { offset ->
            output.versionCodeOverride = versionCode + offset
        }
        output.outputFileName = output.outputFileName
            .replace("app", "v2ray-plugin-$versionName")
            .replace("-release", "")
    }
}
