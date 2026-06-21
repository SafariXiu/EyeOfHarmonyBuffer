
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

afterEvaluate {
    println("=== Plugins applied to root project '${project.name}' ===")
    plugins.forEach { plugin ->
        println(" - ${plugin.javaClass.name}")
    }
    println("===============================================")
}
