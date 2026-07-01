plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.timefix.xposed"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.timefix.xposed"
    minSdk = 19
    targetSdk = 19
    versionCode = 1
    versionName = "1.0"
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

dependencies {
  compileOnly("de.robv.android.xposed:api:82")
}
