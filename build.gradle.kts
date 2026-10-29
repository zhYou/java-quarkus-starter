import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    base
    alias(libs.plugins.quarkus) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
    repositories { mavenCentral() }
}

val quarkusBom = libs.quarkus.bom
val javaFormatVersion = libs.versions.google.java.format.get()

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.ADOPTIUM
        }
    }
    dependencies {
        add("implementation", enforcedPlatform(quarkusBom))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all")
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform { excludeTags("sharding", "recovery", "packaged") }
        maxParallelForks = 1
        testLogging { events("failed", "skipped") }
    }
    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat(javaFormatVersion)
        }
    }
}

tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }

subprojects {
    if (name != "core") {
        val testSource = extensions.getByType<SourceSetContainer>()["test"]
        tasks.register<Test>("shardingTest") {
            group = "verification"
            testClassesDirs = testSource.output.classesDirs
            classpath = testSource.runtimeClasspath
            useJUnitPlatform { setExcludeTags(java.util.HashSet<String>()); includeTags("sharding") }
            dependsOn(tasks.named("testClasses"))
        }
    }
}
project(":cli").tasks.named("shardingTest") { mustRunAfter(":server:shardingTest") }
tasks.register("shardingCheck") { group = "verification"; dependsOn(":server:shardingTest", ":cli:shardingTest") }

project(":server") {
    val tests = extensions.getByType<SourceSetContainer>()["test"]
    tasks.register<Test>("recoveryTest") {
        group = "verification"
        testClassesDirs = tests.output.classesDirs
        classpath = tests.runtimeClasspath
        useJUnitPlatform { setExcludeTags(java.util.HashSet<String>()); includeTags("recovery") }
        dependsOn(tasks.named("testClasses"))
        mustRunAfter(":cli:shardingTest")
    }
}
tasks.named("shardingCheck") { dependsOn(":server:recoveryTest") }

project(":server") {
    val tests = extensions.getByType<SourceSetContainer>()["test"]
    tasks.register<Test>("packageTest") {
        group = "verification"
        testClassesDirs = tests.output.classesDirs
        classpath = tests.runtimeClasspath
        useJUnitPlatform { setExcludeTags(java.util.HashSet<String>()); includeTags("packaged") }
        systemProperty("starter.projectRoot", rootProject.projectDir.absolutePath)
        dependsOn(tasks.named("testClasses"), ":cli:quarkusBuild", ":server:quarkusBuild")
        mustRunAfter(":server:recoveryTest")
    }
}
tasks.register("packageCheck") { group = "verification"; dependsOn(":server:packageTest") }
tasks.register<Exec>("pythonCheck") {
    group = "verification"
    commandLine("python3", "-m", "unittest", "discover", "-s", "scripts/tests", "-v")
}
tasks.named("check") { dependsOn("pythonCheck") }
subprojects {
    tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Werror") }
    dependencyLocking { lockAllConfigurations() }
}

tasks.register<Exec>("assembleRelease") {
    group = "distribution"
    dependsOn(":cli:quarkusBuild", ":server:quarkusBuild")
    commandLine("python3", "scripts/release.py")
}
