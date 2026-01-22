plugins {
    id("java")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// utf-8
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

repositories {
    maven("https://repo.mikeprimm.com/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    implementation(project(":sdk"))
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}
