import java.io.ByteArrayOutputStream
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.adarshr.test-logger") version "3.2.0"
    `maven-publish`
}

group = "com.reevajs"
version = "1.0.0"
val RELEASE = false

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21")
    implementation("com.github.ReevaJS:mfbt:78080dba4e")
    implementation("com.github.ReevaJS:regexp:78f14e1326")
    implementation("org.ow2.asm:asm:9.3")
    implementation("org.ow2.asm:asm-tree:9.3")
    implementation("org.ow2.asm:asm-commons:9.3")
    implementation("com.github.mattco98:Koffee:3b4b48e9ff")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    testImplementation("com.charleskorn.kaml:kaml:0.47.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.8.21")
    testImplementation("io.strikt:strikt-core:0.34.1")
}

task("runScriptRunner", JavaExec::class) {
    classpath = sourceSets.test.get().runtimeClasspath
    main = "com.reevajs.reeva.test262.DebugScriptRunnerKt"
}

tasks {
    test {
        maxHeapSize = "8G"
        useJUnitPlatform()
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-Xinline-classes",
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    val jarWithSources = withType(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    val (publishingUsername, publishingPassword) = when {
        project.hasProperty("IS_CI") -> System.getenv("REPOSILITE_USER") to System.getenv("REPOSILITE_PASSWORD")
        project.hasProperty("reposilite_user") -> project.property("reposilite_user") to project.property("reposilite_password")
        else -> null to null
    }

    if (publishingUsername != null && publishingPassword != null) {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    groupId = project.group.toString()
                    artifactId = project.name

                    version = if (RELEASE) project.version.toString() else getGitHash()

                    artifact(jarWithSources)

                    // ??
                    // from(components["kotlin"])
                }
            }

            repositories {
                maven {
                    name = "reposilite"
                    url = uri("https://repo.mattco.me/" + if (RELEASE) "releases" else "snapshots")

                    credentials {
                        username = publishingUsername as String
                        password = publishingPassword as String
                    }
                }
            }
        }
    }
}

fun getGitHash(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}
