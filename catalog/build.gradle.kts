plugins {
    id("com.android.library")
    id("kotlin-android")
    alias(libs.plugins.ksp) 
    id ("com.vanniktech.maven.publish")
}

android {
    namespace = "aman.catalog" 
    compileSdk = 36 

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
  
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation ("javax.inject:javax.inject:1")

  
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // The annotation processor

   
    implementation("io.github.amanrajaryan:TagLib:1.0.0")
}



mavenPublishing {
    coordinates("io.github.amanrajaryan", "Catalog", "1.1.0")

    pom {
        name.set("catalog")
        description.set("rich media scanning library for android")
        inceptionYear.set("2026")
        url.set("https://github.com/AmanRajAryan/catalog") 

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("AmanRajAryan") 
                name.set("Aman Raj Aryan")
                url.set("https://github.com/AmanRajAryan")
            }
        }
        scm {
            url.set("https://github.com/AmanRajAryan/catalog")
            connection.set("scm:git:git://github.com/AmanRajAryan/catalog.git")
            developerConnection.set("scm:git:ssh://git@github.com/AmanRajAryan/catalog.git")
        }
    }

    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}