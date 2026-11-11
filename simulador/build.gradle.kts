import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// App auxiliar usado só nos testes no emulador: faz o papel do WhatsApp,
// publicando e fechando notificações de mensagem sob comando do teste.
// Não é distribuído.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.plantaoseguro.simulador"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.plantaoseguro.simulador"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
