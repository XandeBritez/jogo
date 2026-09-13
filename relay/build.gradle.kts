plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
}

application {
    mainClass.set("fodinha.relay.MainKt")
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test { useJUnit() }

// Um jar so, com o stdlib dentro: `java -jar relay.jar` na VPS e pronto.
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("fodinha-relay")
    archiveClassifier.set("")
    manifest { attributes["Main-Class"] = "fodinha.relay.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
}
