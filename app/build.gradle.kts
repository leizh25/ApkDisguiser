import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.KeyStore

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseStore = providers.environmentVariable("RELEASE_STORE_FILE")
val releasePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD")
val releaseAlias = providers.environmentVariable("RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD")

android {
    namespace = "me.leizh25.apkdisguiser"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.leizh25.apkdisguiser"
        ndk { abiFilters.add("arm64-v8a") }
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStore.orNull?.let(::file)
            storePassword = releasePassword.orNull
            keyAlias = releaseAlias.orNull
            keyPassword = releaseKeyPassword.orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

abstract class ExportShellDebugKey : DefaultTask() {
    @get:InputFile abstract val storeFile: RegularFileProperty
    @get:Input abstract val storePassword: Property<String>
    @get:Input abstract val keyPassword: Property<String>
    @get:Input abstract val keyAlias: Property<String>
    @get:OutputDirectory abstract val destination: DirectoryProperty

    @TaskAction
    fun export() {
        val store = KeyStore.getInstance(storeFile.get().asFile, storePassword.get().toCharArray())
        val key = store.getKey(keyAlias.get(), keyPassword.get().toCharArray())
        val directory = destination.get().asFile.apply { mkdirs() }
        directory.resolve("shell-key.pk8").writeBytes(key.encoded)
        directory.resolve("shell-cert.der").writeBytes(store.getCertificate(keyAlias.get()).encoded)
    }
}

val exportShellDebugKey by tasks.registering(ExportShellDebugKey::class) {
    storeFile.set(layout.projectDirectory.file("signing/shell-debug.keystore"))
    storePassword.set("android")
    keyPassword.set("android")
    keyAlias.set("androiddebugkey")
    destination.set(layout.buildDirectory.dir("generated/shellDebugAssets"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(exportShellDebugKey, ExportShellDebugKey::destination)
}
class CreateShellDexDirectory(private val directory: Provider<Directory>) : Action<Task> {
    override fun execute(task: Task) {
        directory.get().asFile.mkdirs()
    }
}

val releaseDate = providers.gradleProperty("releaseDate").orElse(
    LocalDate.now(ZoneId.of("Asia/Shanghai"))
        .format(DateTimeFormatter.BASIC_ISO_DATE)
)
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    variant.outputs.forEach { output ->
        output.outputFileName.set(output.versionName.zip(releaseDate) { version, date ->
            "ApkDisguiser-v$version-$date.apk"
        })
    }
}

val shellAndroidJar = androidComponents.sdkComponents.bootClasspath.map { it.first() }
val shellJava = tasks.register<JavaCompile>("compileShellActivity") {
    source(fileTree("src/shell/java"))
    classpath = files(shellAndroidJar)
    options.release.set(8)
    options.encoding = "UTF-8"
    destinationDirectory.set(layout.buildDirectory.dir("intermediates/shellJava"))
}
abstract class ShellDex : JavaExec() {
    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty
}

val shellDex = tasks.register<ShellDex>("dexShellActivity") {
    val sdk = androidComponents.sdkComponents.sdkDirectory
    val r8 = sdk.map { it.file("build-tools/37.0.0/lib/d8.jar") }
    val classes = shellJava.flatMap { it.destinationDirectory }
    destinationDirectory.set(layout.buildDirectory.dir("generated/shellDexAssets"))
    val output = destinationDirectory
    classpath = files(r8)
    mainClass.set("com.android.tools.r8.D8")
    inputs.files(classes, shellAndroidJar)
    doFirst(CreateShellDexDirectory(output))
    run {
        val destination = output.get().asFile
        val compiled = classes.get().file("me/leizh25/apkdisguiser/shell/UninstallActivity.class")
        args("--min-api", "24", "--lib", shellAndroidJar.get().asFile.absolutePath,
            "--output", destination.absolutePath, compiled.asFile.absolutePath)
    }
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(shellDex, ShellDex::destinationDirectory)
}

dependencies {
    implementation("com.android.tools.build:apksig:9.3.3")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
