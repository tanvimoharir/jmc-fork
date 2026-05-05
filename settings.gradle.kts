plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "jmc"
include("gradle-plugin")
include("agent")
include("core")
include("integration-test")
include("j2k-eval")
