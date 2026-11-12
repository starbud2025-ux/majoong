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
        // Pleos Maven(공개 읽기 가능 확인, 2026-11-09). pleos 변형만 사용한다.
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}
rootProject.name = "pleos-app"
include(":core", ":app")
