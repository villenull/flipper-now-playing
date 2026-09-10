plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test")) }
tasks.register<Jar>("codecJar") {
 archiveBaseName.set("np-codec")
 duplicatesStrategy = DuplicatesStrategy.EXCLUDE
 manifest { attributes["Main-Class"] = "io.github.flippernowplaying/core/CodecCliKt".replace('/', '.') }
 from(sourceSets.main.get().output)
 dependsOn(configurations.runtimeClasspath)
 from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
}
