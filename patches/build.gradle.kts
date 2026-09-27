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
