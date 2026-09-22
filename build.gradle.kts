plugins {
    java
    application
}

group = "io.github.ctbot000"
version = "1.0.0"
description = "A zero-dependency inspector that reports every detail a JVM will tell you about itself."

val mainClassName = "io.github.ctbot000.jvminspector.Main"
val agentClassName = "io.github.ctbot000.jvminspector.agent.InspectorAgent"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    // -this-escape only exists from JDK 21 onwards, and javac rejects an unknown lint key outright.
    val lint = mutableListOf("all", "-serial")
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
        lint += "-this-escape"
    }
    options.compilerArgs.addAll(listOf("-Xlint:" + lint.joinToString(","), "-parameters"))
}

application {
    mainClass = mainClassName
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to mainClassName,
            "Premain-Class" to agentClassName,
            "Agent-Class" to agentClassName,
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true",
            "Can-Set-Native-Method-Prefix" to "true",
            "Launcher-Agent-Class" to agentClassName,
            "Implementation-Title" to "jvm-inspector",
            "Implementation-Version" to project.version.toString(),
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

/** Convenience: `./gradlew report` prints a full self-inspection to the console. */
tasks.register<JavaExec>("report") {
    group = "application"
    description = "Runs the inspector against the Gradle-launched JVM itself."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = mainClassName
    args = (project.findProperty("args") as String?)?.split(" ") ?: listOf()
}
