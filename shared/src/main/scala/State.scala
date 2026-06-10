import scala.util.Random
import scala.annotation.tailrec

case class GameLoopInfo(
    current_time: Double,
    last_time: Double
)

// Familia por segmentos (distintas variaciones)
object Constants{
    val BLOCK_SIZE = 10
    val SEGMENT_SIZE = 30

    val FAMILIES: Map[String, SegmentFamily] =
    Map(
        "empty" -> SegmentFamily(
            id = "empty",
            children = Map(
                "empty_default" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector(".", ".")
                    ),
                    weight = 1.0f
                )
            ),
            right_weights = Map(
                "empty" -> 0.997f,
                "ground" -> 0.002f,
                "platform" -> 0.001f
            ),
            bottom_weights = Map(
                "empty" -> 0.7f,
                "ground" -> 0.2f,
                "platform" -> 0.1f
            )
        ),
        "ground" -> SegmentFamily(
            id = "ground",
            children = Map(
                "ground_full" -> Segment(
                    pattern = Vector(
                        Vector("G", "G"),
                        Vector("G", "G")
                    ),
                    weight = 5.0f
                ),

                "ground_spike" -> Segment(
                    pattern = Vector(
                        Vector("S", "."),
                        Vector("G", "G")
                    ),
                    weight = 1.0f
                ),

                "ground_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."),
                        Vector("G", "G")
                    ),
                    weight = 0.3f
                )
            ),
            right_weights = Map(
                "empty" -> 0.1f,
                "ground" -> 0.8f,
                "platform" -> 0.1f
            ),
            bottom_weights = Map(
                "empty" -> 0.0f,
                "ground" -> 1.0f,
                "platform" -> 0.0f
            )
        ),
        "platform" -> SegmentFamily(
            id = "platform",
            children = Map(
                "platform_empty" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector(".", ".")
                    ),
                    weight = 3.0f
                ),

                "platform_top" -> Segment(
                    pattern = Vector(
                        Vector("P", "P"),
                        Vector(".", ".")
                    ),
                    weight = 2.0f
                ),

                "platform_bottom" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector("P", "P")
                    ),
                    weight = 1.5f
                ),

                "platform_left" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector("P", ".")
                    ),
                    weight = 1.5f
                ),

                "platform_right" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector(".", "P")
                    ),
                    weight = 1.5f
                ),

                "platform_full" -> Segment(
                    pattern = Vector(
                        Vector("P", "P"),
                        Vector("P", "P")
                    ),
                    weight = 1.0f
                ),

                "platform_diag1" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector(".", "P")
                    ),
                    weight = 0.5f
                ),

                "platform_diag2" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector("P", ".")
                    ),
                    weight = 0.5f
                ),

                "platform_spike" -> Segment(
                    pattern = Vector(
                        Vector("S", "."),
                        Vector("P", "P")
                    ),
                    weight = 1.0f
                ),
                
                "platform_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."),
                        Vector("P", "P")
                    ),
                    weight = 0.3f
                )
            ),
            right_weights = Map(
                "empty" -> 0.4f,
                "ground" -> 0.2f,
                "platform" -> 0.4f
            ),
            bottom_weights = Map(
                "empty" -> 0.65f,
                "ground" -> 0.0f,
                "platform" -> 0.05f
            )
        )
    )
}


case class Vector2
(
    val x: Float,
    val y: Float
)

class Player
(
    val coords: Vector2,
    val is_alive: Boolean
)
{
    def move(new_x: Float, new_y: Float): Player ={
        new Player(Vector2(new_x + coords.x, new_y + coords.y), is_alive)
    }

    def changeState(new_is_alive: Boolean): Player ={
        new Player(Vector2(coords.x, coords.y), new_is_alive)
    }
}

case class SegmentFamily
(
    val id: String,
    children: Map[String, Segment],
    val right_weights: Map[String, Float],
    val bottom_weights: Map[String, Float]
)

case class Segment(
    pattern: Vector[Vector[String]],
    weight: Float
)

class MapGame
(
    val max_x: Int,
    val min_x: Int,
    val max_y: Int,
    val min_y: Int
)
{
    @tailrec
    private final def weightedRandomAuxiliar(random: Float, map_probability: Seq[(String, Float)]): String = {
        map_probability match{
            case Nil => "empty"
            case (name, _) :: Nil => name
            case (name, weight) :: tail => 
                if (random <= weight) 
                    name
                else
                    weightedRandomAuxiliar(random - weight, tail)
        }
    }

    def weightedRandom(map_probability: Map[String, Float]): String = {
        val filtered = map_probability.filter { case (_, v) => v > 0.0f }
        if (filtered.isEmpty) return "empty"
        val totalWeight: Float = map_probability.values.sum
        val random: Float = Random.between(0.0f, totalWeight)
        weightedRandomAuxiliar(random, map_probability.toSeq)
    }

    def adjustWeightByY(map_probability: Map[String, Float], y: Int): Map[String, Float] = {
        val multipliers: Map[String,Float] =
            if (y <= (max_y - 5)) {
                Map("empty" -> 20.0f, "ground" -> 0.0f, "platform" -> 0.0f)
            } else if (y >= 6 && y <= 9) {
                Map("empty" -> 3.0f, "ground" -> 0.0f, "platform" -> 20.0f)
            } else {
                Map("empty" -> 1.0f, "ground" -> 15.0f, "platform" -> 0.0f)
            }
        map_probability.map{
            case (k, v) =>
                k -> (v * multipliers.getOrElse(k, 1.0f))
        }
    }

//---------------Sección de utilidades-----------------
    def getFamily(segment: Segment): SegmentFamily = {
        Constants.FAMILIES.values.find(_.children.values.exists(_ == segment)).get
    }

    def segmentName(segment: Segment): String = {
        Constants.FAMILIES.values
            .flatMap(_.children)
            .find(_._2 == segment)
            .map(_._1)
            .get
    }

    def requiresEmptyAbove(segment: Segment): Boolean = {
        val name = segmentName(segment)

        name == "ground_spike" ||
        name == "ground_checkpoint" ||
        name == "platform_spike" ||
        name == "platform_checkpoint"
    }

    def isEmpty(segment: Segment): Boolean =
        getFamily(segment).id == "empty"

//----------------------Métricas-----------------------
    def consecutiveEmptyAtEnd(row: Vector[Segment]): Int = {
        row.reverse
            .takeWhile(isEmpty())
            .length
    }

    def distanceFromLastCheckpoint(row: Vector[Segment]): Int = {
        row.reverse
            .takeWhile(segmentName(_) != "ground_checkpoint")
            .length
    }

//----------------------Validaciones---------------------
    def isValidSegment(candidate: Segment, up: Segment): Boolean = {
        !requiresEmptyAbove(candidate) ||
        isEmpty(up)
    }

    def shouldPlaceCheckpoint(y: Int, checkpointDistance: Int, up: Segment): Boolean = {
        y == max_y &&
        checkpointDistance >= 10 &&
        isEmpty(up)
    }

    def shouldForceGround(y: Int, emptyCount: Int): Boolean = {
        y == max_y &&
        emptyCount >= 2
    }

//---------------------Selección--------------------------
    def randomSegmentFromFamily(familyId: String): Segment = {
        val candidates =
            Constants.FAMILIES(familyId)
                .children
                .values
                .toVector

        candidates(Random.nextInt(candidates.size))
    }

    def selectValidSegment(familyId: String, up: Segment): Segment = {

        val candidates =
            Constants.FAMILIES(familyId)
                .children
                .values
                .filter(candidate => isValidSegment(candidate, up))
                .toVector

        if (candidates.nonEmpty)
            candidates(Random.nextInt(candidates.size))
        else
            Constants.FAMILIES("empty")
                .children("empty_default")
    }

    def generate():  Vector[Vector[Segment]] = {
        // checkpoint, trampas
        val root: Segment = Constants.FAMILIES("empty").children("empty_default")

        val matriz: Vector[Vector[Segment]] =
            (0 to max_y).foldLeft(Vector.empty[Vector[Segment]]) { (rows, y) =>

                val row: Vector[Segment] =
                (0 to max_x).foldLeft(Vector.empty[Segment]) { (currentRow, x) =>

                    val segment: Segment =
                        if (y == 0 && x == 0)
                            root
                        else if (y == 0) {
                            val left = currentRow(x - 1)
                            //Constants.SEGMENTS(weightedRandom(adjustWeightByY(left.right_weights, y, left.id))) //platform
                            // funcion(id, val1, val2 )
                            val leftFamily = getFamily(left)
                            val nextFamily = weightedRandom(adjustWeightByY(leftFamily.right_weights,y))
                            randomSegmentFromFamily(nextFamily)

                        } else if (x == 0) {
                            val up = rows(y - 1)(0)
                            //Constants.SEGMENTS(weightedRandom(adjustWeightByY(up.bottom_weights, y, up.id)))
                            val upFamily = getFamily(up)
                            val nextFamily = weightedRandom(adjustWeightByY(upFamily.bottom_weights,y))
                            selectValidSegment(nextFamily, up)

                        } else {
                            val left  = currentRow(x - 1)
                            val up = rows(y - 1)(x)
                            val leftFamily = getFamily(left)
                            val upFamily = getFamily(up)

                            val new_weights =
                                upFamily.bottom_weights.keySet
                                    .intersect(leftFamily.right_weights.keySet)
                                    .map(k => k -> ((upFamily.bottom_weights(k) + leftFamily.right_weights(k)) / 2))
                                    .toMap
                            val emptyCount = consecutiveEmptyAtEnd(currentRow)
                            val checkpointDistance = distanceFromLastCheckpoint(currentRow)
                            
                            if(shouldPlaceCheckpoint(y, checkpointDistance, up))
                                Constants.FAMILIES("ground").children("ground_checkpoint")
                            else if(shouldForceGround(y, emptyCount))
                                Constants.FAMILIES("ground").children("ground_full")
                                //randomSegmentFromFamily("ground")
                            else if (new_weights.isEmpty) 
                                left
                            else 
                                //Constants.SEGMENTS(weightedRandom(adjustWeightByY(new_weights, y, up.id)))
                                val nextFamily = weightedRandom(adjustWeightByY(new_weights,y))
                                //randomSegmentFromFamily(nextFamily)
                                selectValidSegment(nextFamily, up)
                        }

                    currentRow :+ segment
                }

                rows :+ row
            }

        matriz.foreach { row =>
            println(row.map(segmentName).mkString(" | "))
        }
        matriz
    }
}

