import scala.util.Random
import scala.annotation.tailrec

case class GameLoopInfo(
    current_time: Double,
    last_time: Double
)


object Constants{
    val BLOCK_SIZE = 10
    val SEGMENT_SIZE = 30

    val SEGMENTS: Map[String, Segment] =
    Map(
        "empty" -> Segment(
            id = "empty",
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
        "ground" -> Segment(
            id = "ground",
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
        "platform" -> Segment(
            id = "platform",
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
        ),
        "platform_checkpoint" -> Segment(
            id = "platform",
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

case class Segment
(
    val id: String,
    val right_weights: Map[String, Float],
    val bottom_weights: Map[String, Float]
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

    def adjustWeightByY(map_probability: Map[String, Float], y: Int, fromTile: String): Map[String, Float] = {
        val multipliers: Map[String,Float] =
            if (y <= (max_y - 5) || y == 8) {
                Map("empty" -> 20.0f, "ground" -> 0.0f, "platform" -> 0.0f)
            } else if (y == 6 || y == 7) {
                Map("empty" -> 3.0f, "ground" -> 0.0f, "platform" -> 4.0f)
            } else {
                Map("empty" -> 0.0f, "ground" -> 15.0f, "platform" -> 0.0f)
            }
        map_probability.map{
            case (k, v) =>
                k -> (v * multipliers.getOrElse(k, 1.0f))
        }
    }

    def generate():  Vector[Vector[Segment]] = {
        // checkpoint, trampas
        val root: Segment = Constants.SEGMENTS("empty")

        val matriz: Vector[Vector[Segment]] =
            (0 to max_y).foldLeft(Vector.empty[Vector[Segment]]) { (rows, y) =>

                val row: Vector[Segment] =
                (0 to max_x).foldLeft(Vector.empty[Segment]) { (currentRow, x) =>

                    val segment: Segment =
                        if (y == 0 && x == 0)
                            root
                        else if (y == 0) {
                            val left = currentRow(x - 1)
                            Constants.SEGMENTS(weightedRandom(adjustWeightByY(left.right_weights, y, left.id))) //platform
                            // funcion(id, val1, val2 )

                        } else if (x == 0) {
                            val up = rows(y - 1)(0)
                            Constants.SEGMENTS(weightedRandom(adjustWeightByY(up.bottom_weights, y, up.id)))

                        } else {
                            val left  = currentRow(x - 1)
                            val up = rows(y - 1)(x)

                            val new_weights: Map[String, Float] =
                                up.bottom_weights.keySet
                                    .intersect(left.right_weights.keySet)
                                    .map(k => k -> ((up.bottom_weights(k) + left.right_weights(k)) / 2))
                                    .toMap

                            if (new_weights.isEmpty) 
                                left
                            else 
                                Constants.SEGMENTS(weightedRandom(adjustWeightByY(new_weights, y, up.id)))
                        }

                    currentRow :+ segment
                }

                rows :+ row
            }

        /*matriz.foreach { row =>
            println(row.map(_.id).mkString(" | "))
        }*/
        matriz
    }
}

