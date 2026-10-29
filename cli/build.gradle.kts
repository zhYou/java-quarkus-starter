plugins { id("io.quarkus") }

dependencies {
    implementation(project(":core"))
    implementation("io.quarkus:quarkus-cyclonedx")
    implementation("io.quarkus:quarkus-picocli")
    implementation("io.quarkus:quarkus-jackson")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation(testFixtures(project(":core")))
}
