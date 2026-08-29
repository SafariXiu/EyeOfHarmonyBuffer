
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
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

// ============================================================================
// lwjgl3ify 启动链路修复（classpath 过长触发 Gradle「清单 jar」启动模式时，
// GradleStart 与 RFB 都无法再扫描 classpath 发现 lwjgl3ify，导致启动崩溃）
// ----------------------------------------------------------------------------
// 现象：Minecraft.<clinit> 抛 NoSuchMethodError: DisplayMode.<init>(int,int)。
// 原因链：
//   1. lwjglVersion=3 模式下（runClient17/21/25/X），Minecraft 必须依赖 lwjgl3ify
//      的字节码补丁才能运行；
//   2. GradleStart 通过扫描 java.class.path 发现 lwjgl3ify 的 coremod 与
//      Lwjgl3ifyRelauncherTweaker（jar manifest 的 FMLCorePlugin / TweakClass）；
//   3. RFB 通过扫描「mods 目录 + classpath」找 META-INF/rfb-plugin/ 元数据，
//      加载 Lwjgl3ifyRfbPlugin 设置 Launch.blackboard["lwjgl3ify:rfb-booted"]，
//      coremod 构造时才执行 LateInit（真正的补丁）；
//   4. classpath 超过系统命令行限制后 Gradle 改用清单 jar 启动，
//      java.class.path 只剩一个 jar -> 上述两步扫描全部失效 -> 补丁缺失 -> 崩溃。
// 修复（全部仅对 lwjglVersion>=3 生效）：
//   a) extraJvmArgs 注入 -Dfml.coreMods.load（GradleStartCommon 会合并已有属性，
//      不会覆盖；coremod 3.x 不依赖 location）；
//   b) 同步任务 classpath 的全部 jar 到运行目录 mods/：
//      - RFB 通过扫描 mods 目录发现 META-INF/rfb-plugin/（lwjgl3ify 补丁生效的关键）；
//      - FML 的 mods 扫描会处理 jar manifest 的 FMLCorePlugin（coremod 发现）与
//        mcmod.info/@Mod（普通 mod 发现）——清单 jar 模式下 FML 的
//        ClasspathModCandidateFinder 与 GradleStart 的扫描全部失效，mods 目录是
//        唯一仍有效的发现通道；
//      - 仅在 classpath 估算长度 >= 27000 时同步（task classpath 实测：
//        显式模式约 26688 字符，清单 jar 模式约 27940 字符），避免显式模式下
//        FML 双发现重复 mod；
//      - 同步前清空 mods 根下旧 jar（全部由本逻辑产生，避免依赖变更后残留）。
//   c) 注入 --tweakClass Lwjgl3ifyRelauncherTweaker（等价 GradleStart 的
//      cascadingTweaker 发现；rfb-booted 就绪时它只补 transformer 排除与
//      coremod location）。
// ============================================================================
// 匹配所有 RunMinecraftTask（含 convention 注册的 runClient17/21/25/X、runServer17/21/25/X）
tasks.withType<RunMinecraftTask>().configureEach {
    val lwjgl = getLwjglVersion().getOrElse(2)
    if (lwjgl >= 3) {
        // (a) coremod 属性（保险；mods 同步后 FML 也会从 mods 的 manifest 发现 coremod）
        extraJvmArgs.add("-Dfml.coreMods.load=me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod")
        // (c) relauncher tweaker（GradleStart 的 cascadingTweaker 等价物）
        extraArgs.add("--tweakClass")
        extraArgs.add("me.eigenraven.lwjgl3ify.relauncher.Lwjgl3ifyRelauncherTweaker")

        // (b) 全量 jar 同步到运行目录 mods/（在任务执行前完成）
        doFirst {
            val exec = this as JavaExec
            val cpFiles = exec.classpath.files.filter { it.name.endsWith(".jar") }
            val total = cpFiles.sumOf { it.absolutePath.length + 1 }
            if (total >= 27000) {
                val modsDir = File(workingDir, "mods")
                modsDir.mkdirs()
                // 清空上次同步产生的 jar（mods 根下所有 jar 均来自本同步逻辑）
                modsDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".jar") }
                    ?.forEach { it.delete() }
                var copied = 0
                for (f in cpFiles) {
                    val target = File(modsDir, f.name)
                    // 大小或修改时间不一致即重新同步（jar 重建后大小可能不变但内容已变）
                    if (!target.exists() || target.length() != f.length()
                        || target.lastModified() < f.lastModified()) {
                        f.copyTo(target, overwrite = true)
                        copied++
                    }
                }
                println("[EOHB] classpath=" + total + " chars >= 27000: synced " + cpFiles.size
                    + " jars -> " + modsDir.absolutePath + " (copied " + copied + ")")
            } else {
                println("[EOHB] classpath=" + total + " chars < 27000: explicit classpath, no mods sync")
            }
        }
        println("[EOHB] lwjgl3ify mode (lwjglVersion=" + lwjgl + "): injected coremod + tweaker + classpath mods sync into " + name)
    }
}
