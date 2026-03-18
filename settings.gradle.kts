pluginManagement {
    repositories {
        // AliCloud Mirrors for China
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Your AliCloud Mirrors
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }

        // Add the Hammerhead SDK Vault
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("GITHUB_USER").getOrNull()
                password = providers.gradleProperty("GITHUB_TOKEN").getOrNull()
            }
        }

        google()
        mavenCentral()
    }
}

rootProject.name = "Karoo peanut locator"
include(":app")