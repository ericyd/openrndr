package org.openrndr.shape

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ShadeStyle
import org.openrndr.math.Matrix44
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/**
 * Describes a node in a composition
 */
sealed class CompositionNode {
    var id: String? = null
    var parent: CompositionNode? = null
    open var transform = Matrix44.IDENTITY
    var fill: CompositionColor = InheritColor
    var stroke: CompositionColor = InheritColor
    var strokeWeight: CompositionStrokeWeight = InheritStrokeWeight
    var attributes = mutableMapOf<String, String?>()
    var shadeStyle: CompositionShadeStyle = InheritShadeStyle

    /**
     * A map that stores user data
     */
    val userData = mutableMapOf<String, Any>()

    open val bounds: Rectangle
        get() = TODO("can't have it")


    val effectiveShadeStyle: ShadeStyle?
        get() {
            return shadeStyle.let {
                when (it) {
                    is InheritShadeStyle -> parent?.effectiveShadeStyle
                    is CShadeStyle -> it.shadeStyle
                }
            }
        }

    val effectiveStroke: ColorRGBa?
        get() {
            return stroke.let {
                when (it) {
                    is InheritColor -> parent?.effectiveStroke
                    is Color -> it.color
                }
            }
        }

    val effectiveFill: ColorRGBa?
        get() {
            return fill.let {
                when (it) {
                    is InheritColor -> parent?.effectiveFill ?: ColorRGBa.BLACK
                    is Color -> it.color
                }
            }
        }

    val effectiveTransform: Matrix44
        get() {
            return if (transform === Matrix44.IDENTITY) {
                parent?.effectiveTransform ?: Matrix44.IDENTITY
            } else {
                transform * (parent?.effectiveTransform ?: Matrix44.IDENTITY)
            }
        }
}

infix fun KMutableProperty0<CompositionShadeStyle>.`=`(shadeStyle: ShadeStyle?) = this.set(CShadeStyle(shadeStyle))
infix fun KMutableProperty0<CompositionColor>.`=`(color: ColorRGBa?) = this.set(Color(color))
infix fun KMutableProperty0<CompositionStrokeWeight>.`=`(weight: Double) = this.set(StrokeWeight(weight))

operator fun KMutableProperty0<CompositionShadeStyle>.setValue(thisRef: Any?, property: KProperty<*>, value: ShadeStyle) {
    this.set(CShadeStyle(value))
}

sealed class CompositionColor
object InheritColor : CompositionColor()
data class Color(val color: ColorRGBa?) : CompositionColor()

sealed class CompositionShadeStyle
object InheritShadeStyle : CompositionShadeStyle()
data class CShadeStyle(val shadeStyle: ShadeStyle?) : CompositionShadeStyle()

sealed class CompositionStrokeWeight
object InheritStrokeWeight : CompositionStrokeWeight()
data class StrokeWeight(val weight: Double) : CompositionStrokeWeight()


private fun transform(node: CompositionNode): Matrix44 =
        (node.parent?.let { transform(it) } ?: Matrix44.IDENTITY) * node.transform

class ImageNode(var image: ColorBuffer, var x: Double, var y: Double, var width: Double, var height: Double) : CompositionNode() {
    override val bounds: Rectangle
        get() = Rectangle(0.0, 0.0, width, height).contour.transform(transform(this)).bounds
}

class ShapeNode(var shape: Shape) : CompositionNode() {
    override val bounds: Rectangle
        get() {
            val t = effectiveTransform
            return if (t === Matrix44.IDENTITY) {
                shape.bounds
            } else {
                shape.bounds.contour.transform(t).bounds
            }
        }

    /**
     * Applies transforms of all ancestor nodes and returns a new detached ShapeNode with conflated transform
     */
    fun conflate(): ShapeNode {
        return ShapeNode(shape).also {
            it.fill = fill
            it.stroke = stroke
            it.transform = transform(this)
            it.id = id
        }
    }

    /**
     * Applies transforms of all ancestor nodes and returns a new detached shape node with identity transform and transformed Shape
     */
    fun flatten(): ShapeNode {
        return ShapeNode(shape.transform(transform(this))).also {
            it.fill = fill
            it.stroke = stroke
            it.transform = Matrix44.IDENTITY
            it.id = id
        }
    }

    fun copy(id: String? = this.id, parent: CompositionNode? = null, transform: Matrix44 = this.transform, fill: CompositionColor = this.fill, stroke: CompositionColor = this.stroke, shape: Shape = this.shape): ShapeNode {
        return ShapeNode(shape).also {
            it.id = id
            it.parent = parent
            it.transform = transform
            it.fill = fill
            it.stroke = stroke
            it.shape = shape
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShapeNode) return false

        if (shape != other.shape) return false

        return true
    }

    override fun hashCode(): Int {
        return shape.hashCode()
    }


    val effectiveShape
        get() = shape.transform(effectiveTransform)

}

data class TextNode(var text: String, var contour: ShapeContour?) : CompositionNode()

open class GroupNode(val children: MutableList<CompositionNode> = mutableListOf()) : CompositionNode() {
    override val bounds: Rectangle
        get() {
            val b = rectangleBounds(children.map { it.bounds })
            return b
        }

    fun copy(id: String? = this.id, parent: CompositionNode? = null, transform: Matrix44 = this.transform, fill: CompositionColor = this.fill, stroke: CompositionColor = this.stroke, children: MutableList<CompositionNode> = this.children): GroupNode {
        return GroupNode(children).also {
            it.id = id
            it.parent = parent
            it.transform = transform
            it.fill = fill
            it.stroke = stroke
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupNode) return false

        if (children != other.children) return false
        return true
    }

    override fun hashCode(): Int {
        return children.hashCode()
    }

}

val DefaultCompositionBounds = Rectangle(0.0, 0.0, 2676.0, 2048.0)

class GroupNodeStop(children: MutableList<CompositionNode>) : GroupNode(children)
class Composition(val root: CompositionNode, var documentBounds: Rectangle = DefaultCompositionBounds) {
    val namespaces = mutableMapOf<String, String>()

    fun findShapes() = root.findShapes()
    fun findShape(id: String): ShapeNode? {
        return (root.findTerminals { it is ShapeNode && it.id == id }).firstOrNull() as? ShapeNode
    }

    fun findImages() = root.findImages()
    fun findImage(id: String): ImageNode? {
        return (root.findTerminals { it is ImageNode && it.id == id }).firstOrNull() as? ImageNode
    }

    fun findGroups(): List<GroupNode> = root.findGroups()
    fun findGroup(id: String): GroupNode? {
        return (root.findTerminals { it is GroupNode && it.id == id }).firstOrNull() as? GroupNode
    }
}

fun CompositionNode.remove() {
    require(parent != null) { "parent is null" }
    (parent as? GroupNode)?.children?.remove(this)
    parent = null
}

fun CompositionNode.findTerminals(filter: (CompositionNode) -> Boolean): List<CompositionNode> {
    val result = mutableListOf<CompositionNode>()
    fun find(node: CompositionNode) {
        when (node) {
            is GroupNode -> node.children.forEach { find(it) }
            else -> if (filter(node)) {
                result.add(node)
            }
        }
    }
    find(this)
    return result
}

fun CompositionNode.findAll(filter: (CompositionNode) -> Boolean): List<CompositionNode> {
    val result = mutableListOf<CompositionNode>()
    fun find(node: CompositionNode) {
        if (filter(node)) {
            result.add(node)
        }
        if (node is GroupNode) {
            node.children.forEach { find(it) }
        }
    }
    find(this)
    return result
}

fun CompositionNode.findShapes(): List<ShapeNode> = findTerminals { it is ShapeNode }.map { it as ShapeNode }
fun CompositionNode.findImages(): List<ImageNode> = findTerminals { it is ImageNode }.map { it as ImageNode }
fun CompositionNode.findGroups(): List<GroupNode> = findAll { it is GroupNode }.map { it as GroupNode }

fun CompositionNode.visitAll(visitor: (CompositionNode.() -> Unit)) {
    visitor()
    if (this is GroupNode) {
        for (child in children) {
            child.visitAll(visitor)
        }
    }
}

/**
 * UserData delegate
 */
class UserData<T : Any>(
        val name: String, val initial: T
) {
    @Suppress("USELESS_CAST", "UNCHECKED_CAST")
    operator fun getValue(node: CompositionNode, property: KProperty<*>): T {
        val value: T? = node.userData[name] as? T
        return value ?: initial
    }

    operator fun setValue(stylesheet: CompositionNode, property: KProperty<*>, value: T) {
        stylesheet.userData[name] = value
    }
}

@Deprecated("complicated semantics")
fun CompositionNode.filter(filter: (CompositionNode) -> Boolean): CompositionNode? {
    val f = filter(this)

    if (!f) {
        return null
    }

    if (this is GroupNode) {
        val copies = mutableListOf<CompositionNode>()
        children.forEach {
            val filtered = it.filter(filter)
            if (filtered != null) {
                when (filtered) {
                    is ShapeNode -> {
                        copies.add(filtered.copy(parent = this))
                    }
                    is GroupNode -> {
                        copies.add(filtered.copy(parent = this))
                    }
                }
            }
        }
        return GroupNode(children = copies)
    } else {
        return this
    }
}

@Deprecated("complicated semantics")
fun CompositionNode.map(mapper: (CompositionNode) -> CompositionNode): CompositionNode {
    val r = mapper(this)
    return when (r) {
        is GroupNodeStop -> {
            r.copy().also { copy ->
                copy.children.forEach {
                    it.parent = copy
                }
            }
        }
        is GroupNode -> {
            val copy = r.copy(children = r.children.map { it.map(mapper) }.toMutableList())
            copy.children.forEach {
                it.parent = copy
            }
            copy
        }
        else -> r
    }
}

