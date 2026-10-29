plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    implementation("io.quarkus:quarkus-arc")
    implementation("jakarta.transaction:jakarta.transaction-api")
    api("io.quarkus:quarkus-hibernate-orm")
    api("io.quarkus:quarkus-jdbc-postgresql")
    api("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testFixturesApi(enforcedPlatform(libs.quarkus.bom))
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesApi("io.quarkus:quarkus-junit")
    testFixturesApi("io.quarkus:quarkus-narayana-jta")
    testFixturesApi("org.testcontainers:testcontainers-postgresql")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation(libs.archunit)
}

tasks.register<JavaExec>("migrate") {
    group = "database"
    description = "Apply Flyway migrations without starting Quarkus"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.zhyou.starter.maintenance.MigrationRunner"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

tasks.register("javaPath") {
    doLast { println("STARTER_JAVA_PATH=" + javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.absolutePath) }
}
for ((taskName, operation) in mapOf("sampleExport" to "export", "sampleImport" to "import")) {
    tasks.register<JavaExec>(taskName) {
        group = "database"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = "io.github.zhyou.starter.maintenance.SampleDataTransfer"
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
        doFirst { args(operation, providers.gradleProperty("dataFile").get()) }
    }
}

tasks.named<ProcessResources>("processTestFixturesResources") {
    from(rootProject.file("infra/citus/init/002_topology.sql")) { rename { "citus-topology.sql" } }
}
