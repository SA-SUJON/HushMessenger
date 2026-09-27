import org.gradle.jvm.tasks.Jar

group = "com.sysadmindoc.hushmessenger"

patches {
    about {
        name = "HushMessenger"
        description = "Messenger patches with exact-version compatibility checks."
        source = "https://github.com/SysAdminDoc/HushMessenger"
        author = "SysAdminDoc"
        contact = "https://github.com/SysAdminDoc/HushMessenger/issues"
        website = "https://github.com/SysAdminDoc/HushMessenger"
        license = "GPLv3"
    }
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    manifest.attributes["Timestamp"] = providers.gradleProperty("bundleTimestampMillis").get()
    from(listOf(rootProject.file("LICENSE"), rootProject.file("NOTICE"))) { into("META-INF") }
}

dependencyLocking {
    lockAllConfigurations()
}
