import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// La firma solo se configura si el taller de compilación (GitHub Actions) dejó
// disponible el keystore descifrado. Sin él se compila igual, pero sin firmar.
val rutaKeystore: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() && File(it).exists() }

android {
    namespace = "io.github.pvicen.avisos"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.pvicen.avisos"
        minSdk = 26
        targetSdk = 35
        // Cada compilación en GitHub Actions sube el número, así que una versión
        // nueva siempre se instala encima de la anterior.
        val compilacion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = compilacion
        versionName = "1.0.$compilacion"
    }

    signingConfigs {
        val ruta = rutaKeystore
        if (ruta != null) {
            create("publicacion") {
                storeFile = File(ruta)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "avisos"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (rutaKeystore != null) {
                signingConfig = signingConfigs.getByName("publicacion")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // HttpURLConnection no admite PATCH, que es lo que usa Supabase para
    // marcar un aviso como hecho.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
