import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be`
import org.amshove.kluent.shouldBeInRange
import org.openrndr.math.Vector2
import org.openrndr.shape.Segment
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.Math.sqrt

infix fun Vector2.`should be near`(other: Vector2) {
    x shouldBeInRange (other.x - 0.00001 .. other.x + 0.00001)
    y shouldBeInRange (other.y - 0.00001 .. other.y + 0.00001)
}

object TestSegment : Spek({
    describe("a linear segment") {
        val segment = Segment(Vector2(0.0, 0.0), Vector2(100.0, 100.0))
        it("has evaluable and correct bounds") {
            val bounds = segment.bounds
            bounds.x `should be equal to` segment.start.x
            bounds.y `should be equal to` segment.start.y
            bounds.width `should be equal to` 100.0
            bounds.height `should be equal to` 100.0
        }

        it("can be split in half") {
            val sides = segment.split(0.5)
            sides.size `should be equal to` 2
        }

        it("can be split at 0.0, but result in 1 part") {
            val sides = segment.split(0.0)
            sides.size `should be equal to` 1
        }

        it("can be split at 1.0, but result in 1 part") {
            val sides = segment.split(1.0)
            sides.size `should be equal to` 1
        }
        it("can be subbed from 0.0 to 1.0") {
            val sub = segment.sub(0.0, 1.0)
            sub `should be` segment
        }
        it("can be subbed from 0.0 to 0.5") {
            val sub = segment.sub(0.0, 0.5)
            (sub.start - segment.start).squaredLength `should be equal to` 0.0
        }
        it("can be subbed from 0.5 to 1.0") {
            val sub = segment.sub(0.5, 1.0)
            (sub.end - segment.end).squaredLength `should be equal to` 0.0
        }

        it("has a length") {
            segment.length `should be equal to` sqrt(100.0 * 100.0 * 2.0)
        }

        it("has a normal") {
            segment.normal(0.0)
        }

        it("can be offset") {
            val offset = segment.offset(10.0)
            offset.size `should be equal to` 1
        }

        it("can be promoted to a quadratic segment") {
            val quadratic = segment.quadratic
            quadratic.position(0.0) `should be equal to` segment.position(0.0)
            quadratic.position(0.25) `should be equal to` segment.position(0.25)
            quadratic.position(0.5) `should be equal to` segment.position(0.5)
            quadratic.position(0.75) `should be equal to` segment.position(0.75)
            quadratic.position(1.0) `should be equal to` segment.position(1.0)
        }

        it("can be promoted to a cubic segment") {
            val cubic = segment.cubic
            cubic.position(0.0) `should be near` segment.position(0.0)
            cubic.position(0.25) `should be near` segment.position(0.25)
            cubic.position(0.5) `should be near` segment.position(0.5)
            cubic.position(0.75) `should be near` segment.position(0.75)
            cubic.position(1.0) `should be near` segment.position(1.0)
        }
    }

    describe("a cubic segment") {
        val segment = Segment(Vector2(0.0, 0.0), Vector2(100.0, 100.0), Vector2(50.0, 100.0), Vector2(0.0, 100.0))

        it("has evaluable bounds") {
            segment.bounds
        }

        it("has evaluable extrema") {
            segment.extrema()
        }

        describe("has evaluable reduction") {
            val reduced = segment.reduced()

            println("number of segments in reduction: ${segment.reduced().size}")

            it("can be scaled") {

            }
        }

        it("can be split in half") {
            val sides = segment.split(0.5)
            sides.size `should be equal to` 2
        }

        it("can be split at 0.0, but result in 1 part") {
            val sides = segment.split(0.0)
            sides.size `should be equal to` 1
        }

        it("can be split at 1.0, but result in 1 part") {
            val sides = segment.split(1.0)
            sides.size `should be equal to` 1
        }

        it("can be subbed from 0.0 to 1.0") {
            val sub = segment.sub(0.0, 1.0)
            sub `should be` segment
        }
        it("can be subbed from 0.0 to 0.5") {
            val sub = segment.sub(0.0, 0.5)
            (sub.start - segment.start).squaredLength `should be equal to` 0.0
        }
        it("can be subbed from 0.5 to 1.0") {
            val sub = segment.sub(0.5, 1.0)
            (sub.end - segment.end).squaredLength `should be equal to` 0.0
        }
    }
})