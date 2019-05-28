allprojects {
    group = "org.apache.roller"
    version = "6.0.0-SNAPSHOT"
}

subprojects {

    apply(plugin = "java")
    apply(plugin = "maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
    }

//    publishing {
//        publications {
//            maven(MavenPublication) {
//                from(components.java)
//            }
//        }
//    }

    tasks.withType(JavaCompile::class) {
        options.encoding = "UTF-8"
    }
}
