extension {
    name = "extensions/messenger.mpe"
}

android {
    namespace = "app.hushmessenger.extension"
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        versionCode = 10
        versionName = project.version.toString()
    }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

dependencyLocking { lockAllConfigurations() }
