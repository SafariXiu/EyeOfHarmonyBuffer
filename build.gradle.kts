
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.ow2.asm:asm:9.9.1")
        classpath("org.ow2.asm:asm-tree:9.9.1")
    }
}

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.named<Jar>("downgradeJar") {
    doLast {
        val jarFile = archiveFile.get().asFile
        val nestDesc = "Lxyz/wagyourtail/jvmdg/j11/NestMembers;"
        val nestHostDesc = "Lxyz/wagyourtail/jvmdg/j11/NestHost;"
        val entries = LinkedHashMap<String, ByteArray>()
        val changed = booleanArrayOf(false)
        ZipFile(jarFile).use { zip ->
            zip.entries()
                .asSequence()
                .forEach { entry ->
                    zip.getInputStream(entry)
                        .use { input ->
                            val bytes = input.readBytes()
                            entries[entry.name] =
                                if (entry.name.startsWith("com/EyeOfHarmonyBuffer/Mixins/")
                                    && entry.name.endsWith(".class")) {
                                    val node = ClassNode()
                                    ClassReader(bytes).accept(node, 0)
                                    val before = (node.visibleAnnotations?.size ?: 0)
                                        + (node.invisibleAnnotations?.size ?: 0)
                                    node.visibleAnnotations?.removeAll { it.desc == nestDesc || it.desc == nestHostDesc }
                                    node.invisibleAnnotations?.removeAll { it.desc == nestDesc || it.desc == nestHostDesc }
                                    val after = (node.visibleAnnotations?.size ?: 0)
                                        + (node.invisibleAnnotations?.size ?: 0)
                                    if (after < before) changed[0] = true
                                    val writer = ClassWriter(0)
                                    node.accept(writer)
                                    writer.toByteArray()
                                } else {
                                    bytes
                                }
                        }
                }
        }
        if (changed[0]) {
            val tmp = File(jarFile.parentFile, jarFile.name + ".stripping")
            ZipOutputStream(tmp.outputStream())
                .use { out ->
                    entries.forEach { (name, bytes) ->
                        out.putNextEntry(ZipEntry(name))
                        out.write(bytes)
                        out.closeEntry()
                    }
                }
            Files.move(tmp.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

afterEvaluate {
    println("=== Plugins applied to root project '${project.name}' ===")
    plugins.forEach { plugin ->
        println(" - ${plugin.javaClass.name}")
    }
    println("===============================================")
}
