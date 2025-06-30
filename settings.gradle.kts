pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.dcm4che.org/")
        }
    }
}

rootProject.name = "My Application"
include(":app")
include(":network")
include(":domain")
include(":protos")
