import java.util.Properties

// GitHub Packages kimlik bilgilerini SDK repo'sunun kendisiyle aynı şekilde okur:
// önce env değişkenleri (CI), sonra local.properties (lokal geliştirme, gitignored, asla commit edilmez).
val localProperties = Properties().apply {
    val f = rootDir.resolve("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun credential(envKey: String, propKey: String): String =
    System.getenv(envKey) ?: localProperties.getProperty(propKey) ?: ""

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

        // Identify SDK, GitHub Packages üzerinden private bir paket olarak dağıtılır.
        // `read:packages` scope'lu bir GitHub PAT gerektirir — bkz. README.md.
        maven {
            url = uri("https://maven.pkg.github.com/2sworks/identify-v3")
            credentials {
                username = credential("GPR_USER", "gpr.user")
                password = credential("GPR_TOKEN", "gpr.key")
            }
        }

        // GEÇİCİ — yalnızca lokal test repo'su, SDK repo'sunun kendi "LocalMaven" publish
        // hedefini (D:\IdentifySdkV3\repo) gösteriyor. Yalnızca GitHub Packages'ta bulunmayan
        // sürümleri çözer (örn. "3.4.5-navfix1" test build'leri), yani gerçek yayınlanmış
        // sürümlerin önüne asla geçmez. GitHub Packages'ta gerçek düzeltilmiş sürüm
        // yayınlandığında kaldırılması güvenlidir.
       // maven { url = uri("D:/IdentifySdkV3/repo") }
    }
}

rootProject.name = "identify-v3-sample"
include(":app")
