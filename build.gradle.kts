plugins {
    id("jfmod") version "1.8-SNAPSHOT"
}

loom {
    accessWidenerPath.set(file("src/main/resources/google-chat.accesswidener"))
    runConfigs {
        named("server") {
            runDir = "run-server"
        }
    }
}

allprojects { group = "io.gitlab.jfronny" }
base.archivesName = "google-chat"

jfMod {
    minecraftVersion = "1.21.11"
    mojmap()
    loaderVersion = "0.18.2"
    libJfVersion = "3.19.8"
    fabricApiVersion = "0.139.4+1.21.11"

    modrinth {
        projectId = "google-chat"
        requiredDependencies.add("libjf")
        optionalDependencies.add("modmenu")
    }
    curseforge {
        projectId = "574331"
        requiredDependencies.add("libjf")
        optionalDependencies.add("modmenu")
    }
}

dependencies {
    modImplementation("io.gitlab.jfronny.libjf:libjf-config-core-v2")
    modImplementation("io.gitlab.jfronny.libjf:libjf-translate-v1")
    include(modImplementation("net.fabricmc.fabric-api:fabric-message-api-v1")!!)
    // Keybind
    modCompileOnly("net.fabricmc.fabric-api:fabric-key-binding-api-v1")
    modCompileOnly("net.fabricmc.fabric-api:fabric-lifecycle-events-v1")

    // Dev env
    modLocalRuntime("io.gitlab.jfronny.libjf:libjf-config-ui-tiny")
    modLocalRuntime("io.gitlab.jfronny.libjf:libjf-devutil")
    modLocalRuntime("net.fabricmc.fabric-api:fabric-resource-loader-v1")
    modLocalRuntime("com.terraformersmc:modmenu:17.0.0-alpha.1")
    // for modmenu
    modLocalRuntime("net.fabricmc.fabric-api:fabric-screen-api-v1")
    modLocalRuntime("net.fabricmc.fabric-api:fabric-key-binding-api-v1")
    modLocalRuntime("net.fabricmc.fabric-api:fabric-resource-loader-v0")
}
