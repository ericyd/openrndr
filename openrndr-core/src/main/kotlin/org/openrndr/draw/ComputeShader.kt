package org.openrndr.draw

import org.openrndr.color.ColorRGBa
import org.openrndr.internal.Driver
import org.openrndr.math.*
import java.io.File

enum class ImageAccess {
    READ,
    WRITE,
    READ_WRITE
}


interface ComputeShader:ShaderImageBindings {
    companion object {
        /**
         * Create a compute shader from (GLSL) code as a String
         */
        fun fromCode(code: String): ComputeShader = Driver.instance.createComputeShader(code)

        /**
         * Create a compute shader from file
         */
        fun fromFile(file: File): ComputeShader {
            return fromCode(file.readText())
        }
    }


    fun buffer(name:String, vertexBuffer: VertexBuffer)
    fun counters(bindingIndex: Int, counterBuffer: AtomicCounterBuffer)


    fun uniform(name: String, value: Matrix33)
    fun uniform(name: String, value: Matrix44)

    fun uniform(name: String, value: ColorRGBa)
    fun uniform(name: String, value: Vector4)
    fun uniform(name: String, value: Vector3)
    fun uniform(name: String, value: Vector2)
    fun uniform(name: String, x: Float, y: Float, z: Float, w: Float)
    fun uniform(name: String, x: Float, y: Float, z: Float)
    fun uniform(name: String, x: Float, y: Float)
    fun uniform(name: String, value: Double)
    fun uniform(name: String, value: Float)
    fun uniform(name: String, value: Int)
    fun uniform(name: String, value: Boolean)

    fun uniform(name: String, value: Array<Vector4>)
    fun uniform(name: String, value: Array<Vector3>)
    fun uniform(name: String, value: Array<Vector2>)
    fun uniform(name: String, value: FloatArray)


    /**
     * Execute the compute shader
     * @param width the global width
     * @param height the global height
     * @param depth the global depth
     */
    fun execute(width: Int = 1, height: Int = 1, depth: Int = 1)

    /**
     * Destroy the compute shader
     */
    fun destroy()

}