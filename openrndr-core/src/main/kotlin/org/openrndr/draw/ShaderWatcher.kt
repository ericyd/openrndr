package org.openrndr.draw

import java.io.File
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import kotlin.concurrent.thread

private val watching = mutableMapOf<Path, MutableList<ShaderWatcher>>()
private val pathKeys = mutableMapOf<Path, WatchKey>()
private val keyPaths = mutableMapOf<WatchKey, Path>()

private val watchService by lazy {
    FileSystems.getDefault().newWatchService()
}

private val watchThread by lazy {
    thread {
        while (true) {
            val key = watchService.take()
            val path = keyPaths[key]
            key.pollEvents().forEach {
                val contextPath = it.context() as Path
                val fullPath = path?.resolve(contextPath)
                watching[fullPath]?.forEach {
                    println("informing $it of change in $fullPath")
                    it.changed = true
                }
            }
            key.reset()
        }
    }
}

class ShaderWatcher private constructor(
        val vsUrl: String? = null,
        val vsCode: String? = null,
        val fsUrl: String? = null,
        val fsCode: String? = null,
        var currentShader: Shader) {

    internal var changed = false

    companion object {
        fun fromUrls(vsCode: String? = null,
                     vsUrl: String? = null,
                     fsCode: String? = null,
                     fsUrl: String? = null): ShaderWatcher {
            val vsFile = vsUrl?.let { URL(it).toFile().let { File(it) } }
            val fsFile = fsUrl?.let { URL(it).toFile().let { File(it) } }
            val vsPath = vsFile?.toPath()
            val fsPath = fsFile?.toPath()
            val vsParent = vsPath?.parent
            val fsParent = fsPath?.parent

            val effectiveVsCode = vsCode ?: codeFromURL(vsUrl ?: throw RuntimeException("no code or url for vertex shader"))
            val effectiveFsCode = fsCode ?: codeFromURL(fsUrl ?: throw RuntimeException("no code or url for fragment shader"))
            val shader = Shader.createFromCode(effectiveVsCode, effectiveFsCode)

            val watcher = ShaderWatcher(
                    vsCode = vsCode,
                    vsUrl = vsUrl,
                    fsCode = fsCode,
                    fsUrl = fsUrl,
                    currentShader = shader)

            listOfNotNull(vsParent, fsParent).forEach {
                val key = pathKeys.getOrPut(it) {
                    it.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
                }
                keyPaths.getOrPut(key) { it }
            }
            if (vsPath != null) {
                watching.getOrPut(vsPath) { mutableListOf() }.add(watcher)
            }

            if (fsPath != null) {
                watching.getOrPut(fsPath) { mutableListOf() }.add(watcher)
            }
            watchThread
            return watcher
        }
    }

    val shader: Shader?
        get() {
            if (changed) {
                try {
                    val effectiveVsCode = vsCode ?: codeFromURL(vsUrl ?: throw RuntimeException("no code or url for vertex shader"))
                    val effectiveFsCode = fsCode ?: codeFromURL(fsUrl ?: throw RuntimeException("no code or url for fragment shader"))
                    currentShader = Shader.createFromCode(effectiveVsCode, effectiveFsCode)
                } catch (exception: Throwable) {
                    exception.printStackTrace()
                }
                changed = false
            }
            return currentShader
        }
}

class ShaderWatcherBuilder {
    var vertexShaderCode: String? = null
    var vertexShaderUrl: String? = null
    var fragmentShaderCode: String? = null
    var fragmentShaderUrl: String? = null

    fun build(): ShaderWatcher {
        return ShaderWatcher.fromUrls(vsCode = vertexShaderCode, vsUrl = vertexShaderUrl, fsCode = fragmentShaderCode, fsUrl = fragmentShaderUrl)
    }
}

private fun URL.toFile(): String? {
    return if (protocol == "file") {
        path
    } else {
        null
    }
}

fun shaderWatcher(init: ShaderWatcherBuilder.() -> Unit):ShaderWatcher {
    val swb = ShaderWatcherBuilder()
    swb.init()
    return swb.build()
}

