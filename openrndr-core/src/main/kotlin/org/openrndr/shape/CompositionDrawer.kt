package org.openrndr.shape

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.YPolarity
import org.openrndr.math.transforms.rotateZ
import org.openrndr.math.transforms.scale
import org.openrndr.math.transforms.transform
import org.openrndr.math.transforms.translate
import java.util.*

enum class ClipOp {
    DISABLED,
    DIFFERENCE,
    INTERSECT,
    UNION
}

enum class ClipMode(val grouped: Boolean, val op: ClipOp) {
    DISABLED(false, ClipOp.DISABLED),
    DIFFERENCE(false, ClipOp.DIFFERENCE),
    DIFFERENCE_GROUP(true, ClipOp.DIFFERENCE),
    INTERSECT(false, ClipOp.INTERSECT),
    INTERSECT_GROUP(true, ClipOp.INTERSECT),
    UNION(false, ClipOp.UNION),
    UNION_GROUP(true, ClipOp.UNION)
}

private data class CompositionDrawStyle(
        var fill: ColorRGBa? = null,
        var stroke: ColorRGBa? = ColorRGBa.BLACK,
        var strokeWeight: Double = 1.0,
        var clipMode: ClipMode = ClipMode.DISABLED
)

data class ShapeNodeIntersection(val node: ShapeNode, val intersection: ContourIntersection)
data class ShapeNodeNearestContour(val node: ShapeNode, val point: ContourPoint, val distanceDirection:Vector2, val distance: Double)

fun List<ShapeNodeIntersection>.merge(threshold: Double = 0.5): List<ShapeNodeIntersection> {
    val result = mutableListOf<ShapeNodeIntersection>()
    for (i in this) {
        val nearest = result.minByOrNull { it.intersection.position.squaredDistanceTo(i.intersection.position) }
        if (nearest == null) {
            result.add(i)
        } else if (nearest.intersection.position.squaredDistanceTo(i.intersection.position) >= threshold * threshold) {
            result.add(i)
        }
    }
    return result
}


/**
 * A Drawer-like interface for the creation of Compositions
 * This should be easier than creating Compositions manually
 */
class CompositionDrawer(documentBounds: Rectangle = DefaultCompositionBounds,
                        composition: Composition? = null) {
    val root = GroupNode()
    val composition = composition ?: Composition(root, documentBounds)

    private var cursor = root
    private val modelStack = Stack<Matrix44>()
    private val styleStack = Stack<CompositionDrawStyle>().apply { }
    private var drawStyle = CompositionDrawStyle()

    var model = Matrix44.IDENTITY

    var fill
        get() = drawStyle.fill
        set(value) = run { drawStyle.fill = value }

    var stroke
        get() = drawStyle.stroke
        set(value) = run { drawStyle.stroke = value }

    var strokeWeight
        get() = drawStyle.strokeWeight
        set(value) = run { drawStyle.strokeWeight = value }

    var clipMode
        get() = drawStyle.clipMode
        set(value) = run { drawStyle.clipMode = value }

    fun pushModel() {
        modelStack.push(model)
    }

    fun popModel() {
        model = modelStack.pop()
    }

    fun pushStyle() {
        styleStack.push(drawStyle.copy())
    }

    fun popStyle() {
        drawStyle = styleStack.pop()
    }

    fun isolated(draw: CompositionDrawer.() -> Unit) {
        pushModel()
        pushStyle()
        draw()
        popModel()
        popStyle()
    }

    operator fun GroupNode.invoke(builder: CompositionDrawer.() -> Unit): GroupNode {
        val oldCursor = cursor
        cursor = this
        builder()
        cursor = oldCursor
        return this
    }

    /**
     * Create a group node and run `builder` inside its context
     * @param id an optional identifier
     * @param builder the function that is executed inside the group context
     */
    fun group(id: String? = null, builder: CompositionDrawer.() -> Unit): GroupNode {
        val g = GroupNode()
        g.id = id
        val oldCursor = cursor

        cursor.children.add(g)
        cursor = g
        builder()

        cursor = oldCursor
        return g
    }

    fun translate(x: Double, y: Double) = translate(Vector2(x, y))

    fun rotate(rotationInDegrees: Double) {
        model *= Matrix44.rotateZ(rotationInDegrees)
    }

    fun scale(s: Double) {
        model *= Matrix44.scale(s, s, s)
    }

    fun scale(x: Double, y: Double) {
        model *= Matrix44.scale(x, y, 1.0)
    }

    fun translate(t: Vector2) {
        model *= Matrix44.translate(t.vector3())
    }

    fun contour(contour: ShapeContour): ShapeNode? {
        val shape = Shape(listOf(contour))
        return shape(shape)
    }

    fun contours(contours: List<ShapeContour>) = contours.map { contour(it) }

    /**
     * Search for a point on a contour in the composition tree that's nearest to `point`
     * @param point the query point
     * @param searchFrom a node from which the search starts, defaults to composition root
     * @return an optional ShapeNodeNearestContour instance
     */
    fun nearest(
            point: Vector2,
            searchFrom: CompositionNode = composition.root as GroupNode
    ): ShapeNodeNearestContour? {
        return searchFrom.findShapes().flatMap { node ->
            node.shape.contours
                    .map { it.nearest(point) }
                    .map { ShapeNodeNearestContour(node, it, point - it.position, it.position.distanceTo(point)) }
        }.minByOrNull { it.distance }
    }

    /**
     * Test a given `contour` against contours in the composition tree
     * @param contour the query contour
     * @param searchFrom a node from which the search starts, defaults to composition root
     * @param mergeThreshold minimum distance between intersections before they are merged together,
     * 0.0 or lower means no merge
     * @return a list of `ShapeNodeIntersection`
     */
    fun intersections(
            contour: ShapeContour,
            searchFrom: CompositionNode = composition.root as GroupNode,
            mergeThreshold: Double = 0.5
    ): List<ShapeNodeIntersection> {
        return searchFrom.findShapes().flatMap { node ->
            node.shape.contours.flatMap {
                intersections(contour, it).map {
                    ShapeNodeIntersection(node, it)
                }
            }
        }.let {
            if (mergeThreshold > 0.0) {
                it.merge(mergeThreshold)
            } else {
                it
            }
        }
    }

    /**
     * Test a given `shape` against contours in the composition tree
     * @param shape the query shape
     * @param searchFrom a node from which the search starts, defaults to composition root
     * @return a list of `ShapeNodeIntersection`
     */
    fun intersections(
            shape: Shape,
            searchFrom: CompositionNode = composition.root as GroupNode
    ): List<ShapeNodeIntersection> {
        return shape.contours.flatMap {
            intersections(it, searchFrom)
        }
    }

    fun shape(shape: Shape): ShapeNode? {
        // only use clipping for open shapes
        val clipMode = if (shape.topology == ShapeTopology.CLOSED) clipMode else ClipMode.DISABLED

        return when (clipMode) {
            ClipMode.DISABLED -> {
                val shapeNode = ShapeNode(shape)
                shapeNode.transform = model
                shapeNode.fill = Color(fill)
                shapeNode.stroke = Color(stroke)
                shapeNode.strokeWeight = StrokeWeight(strokeWeight)
                cursor.children.add(shapeNode)
                shapeNode.parent = cursor
                shapeNode
            }
            else -> {
                val shapeNodes = (if (!clipMode.grouped) composition.findShapes() else cursor.findShapes())

                shapeNodes.forEach { shapeNode ->
                    val transform = shapeNode.effectiveTransform
                    val inverse = if (transform === Matrix44.IDENTITY) Matrix44.IDENTITY else transform.inversed
                    val transformedShape = if (inverse === Matrix44.IDENTITY) shape else shape.transform(inverse)
                    val operated =
                            when (clipMode.op) {
                                ClipOp.INTERSECT -> intersection(shapeNode.shape, transformedShape)
                                ClipOp.UNION -> union(shapeNode.shape, transformedShape).take(1)
                                ClipOp.DIFFERENCE -> difference(shapeNode.shape, transformedShape)
                                else -> error("unsupported base op ${clipMode.op}")
                            }

                    when (operated.size) {
                        0 -> {
                            shapeNode.remove()
                        }
                        1 -> {
                            shapeNode.shape = operated.first()
                        }
                        else -> {
                            shapeNode.shape = Shape.compound(operated)
//                            val groupNode = GroupNode(operated.map { ShapeNode(it) }.toMutableList())
//                            (shapeNode.parent as? GroupNode)?.children?.replace(shapeNode, groupNode)
                        }
                    }
                }
                null
            }
        }
    }

    fun shapes(shapes: List<Shape>) = shapes.map { shape(it) }

    fun rectangle(rectangle: Rectangle) = contour(rectangle.contour)

    fun rectangle(x: Double, y: Double, width: Double, height: Double) = rectangle(Rectangle(x, y, width, height))

    fun rectangles(rectangles: List<Rectangle>) = rectangles.map { rectangle(it) }

    fun rectangles(positions: List<Vector2>, width: Double, height: Double) = rectangles(positions.map {
        Rectangle(it, width, height)
    })

    fun rectangles(positions: List<Vector2>, dimensions: List<Vector2>) = rectangles((positions zip dimensions).map {
        Rectangle(it.first, it.second.x, it.second.y)
    })

    fun circle(x: Double, y: Double, radius: Double) = circle(Circle(Vector2(x, y), radius))

    fun circle(position: Vector2, radius: Double) = circle(Circle(position, radius))

    fun circle(circle: Circle) = contour(circle.contour)

    fun circles(circles: List<Circle>) = circles.map { circle(it) }

    fun circles(positions: List<Vector2>, radius: Double) = circles(positions.map { Circle(it, radius) })

    fun circles(positions: List<Vector2>, radii: List<Double>) = circles((positions zip radii).map { Circle(it.first, it.second) })

    fun lineSegment(start: Vector2, end: Vector2) = lineSegment(LineSegment(start, end))

    fun lineSegment(lineSegment: LineSegment) = contour(lineSegment.contour)

    fun lineSegments(lineSegments: List<LineSegment>) = lineSegments.map {
        lineSegment(it)
    }

    fun lineStrip(points: List<Vector2>) = contour(ShapeContour.fromPoints(points, false, YPolarity.CW_NEGATIVE_Y))

    fun lineLoop(points: List<Vector2>) = contour(ShapeContour.fromPoints(points, true, YPolarity.CW_NEGATIVE_Y))

    fun text(text: String, position: Vector2): TextNode {
        val g = GroupNode()
        g.transform = transform { translate(position.xy0) }
        val textNode = TextNode(text, null).apply {
            this.fill = Color(this@CompositionDrawer.fill)
        }
        g.children.add(textNode)
        cursor.children.add(g)
        return textNode
    }

    fun textOnContour(text: String, path: ShapeContour) {
        cursor.children.add(TextNode(text, path))
    }

    fun texts(text: List<String>, positions: List<Vector2>) =
            (text zip positions).map {
                text(it.first, it.second)
            }

    /**
     * Adds an image to the composition tree
     */
    fun image(image: ColorBuffer, x: Double = 0.0, y: Double = 0.0): ImageNode {
        val node = ImageNode(image, x, y, width = image.width.toDouble(), height = image.height.toDouble())
        node.transform = this.model
        cursor.children.add(node)
        return node
    }
}

private fun <E> MutableList<E>.replace(search: E, replace: E) {
    val index = this.indexOf(search)
    if (index != -1) {
        this[index] = replace
    }
}

fun drawComposition(
        documentBounds: Rectangle = DefaultCompositionBounds,
        composition: Composition? = null,
        drawFunction: CompositionDrawer.() -> Unit
): Composition = CompositionDrawer(documentBounds, composition).apply { drawFunction() }.composition