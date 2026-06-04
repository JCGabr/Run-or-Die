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
            "sky" -> Segment(
            id = "sky",
            right_weights = Map(
                "sky" -> 0.995f,
                "grass" -> 0.001f,
                "forest" -> 0.002f,
                "water" -> 0.001f
            ),
            bottom_weights = Map(
                "sky" -> 0.8f,
                "grass" -> 0.3f,
                "forest" -> 0.3f,
                "water" -> 0.2f
            )
            ),
            "grass" -> Segment(
            id = "grass",
            right_weights = Map(
                "sky" -> 0.2f,
                "grass" -> 0.7f,
                "forest" -> 0.2f,
                "water" -> 0.1f
            ),
            bottom_weights = Map(
                "sky" -> 0.2f,
                "grass" -> 0.6f,
                "forest" -> 0.3f,
                "water" -> 0.1f
            )
            ),

            "forest" -> Segment(
            id = "forest",
            right_weights = Map(
                "sky" -> 0.2f,
                "forest" -> 0.6f,
                "grass" -> 0.3f,
                "mountain" -> 0.1f
            ),
            bottom_weights = Map(
                "sky" -> 0.2f,
                "forest" -> 0.7f,
                "grass" -> 0.2f,
                "mountain" -> 0.1f
            )
            ),

            "water" -> Segment(
            id = "water",
            right_weights = Map(
                "sky" -> 0.2f,
                "water" -> 0.8f,
                "grass" -> 0.2f
            ),
            bottom_weights = Map(
                "sky" -> 0.2f,
                "water" -> 0.8f,
                "grass" -> 0.2f
            )
            ),

            "mountain" -> Segment(
            id = "mountain",
            right_weights = Map(
                "sky" -> 0.2f,
                "mountain" -> 0.7f,
                "forest" -> 0.3f
            ),
            bottom_weights = Map(
                "sky" -> 0.2f,
                "mountain" -> 0.8f,
                "forest" -> 0.2f
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
            case Nil => "sky"
            case (name, _) :: Nil => name
            case (name, weight) :: tail => 
                if (random <= weight) 
                    name
                else
                    weightedRandomAuxiliar(random - weight, tail)
        }
    }

    def weightedRandom(map_probability: Map[String, Float]): String = {
        val totalWeight: Float = map_probability.values.sum
        val random: Float = Random.between(0.0f, totalWeight)
        weightedRandomAuxiliar(random, map_probability.toSeq)
    }

    def adjustWeightByY(map_probability: Map[String,Float], y: Int): Map[String, Float] ={
        val multipliers: Map[String,Float] =
            if (y <= 5){
                Map(
                    "sky" -> 15.75f, 
                    "grass" -> 0.75f, 
                    "forest" -> 0.6f, 
                    "water" -> 0.5f, 
                    "mountain" -> 0.25f
                    )
            }else if(y >= 6){
                Map(
                    "sky" -> 0.1f,
                    "grass" -> 4.0f, 
                    "forest" -> 3.0f, 
                    "water" -> 2.0f, 
                    "mountain" -> 1.5f
                    )
            }else{
                map_probability.map { case (k, _) => k -> 1.0f }
            }

        map_probability.map{
            case (k, v) =>
                k -> (v * multipliers.getOrElse(k, 1.0f))
        }
    }

    def generate(): Unit = {
        val root: Segment = Constants.SEGMENTS("sky")

        val matriz: Vector[Vector[Segment]] =
            (0 to max_y).foldLeft(Vector.empty[Vector[Segment]]) { (rows, y) =>

                val row: Vector[Segment] =
                (0 to max_x).foldLeft(Vector.empty[Segment]) { (currentRow, x) =>

                    val segment: Segment =
                        if (y == 0 && x == 0)
                            root
                        else if (y == 0) {
                            val left = currentRow(x - 1)
                            Constants.SEGMENTS(weightedRandom(adjustWeightByY(left.right_weights, y)))

                        } else if (x == 0) {
                            val up = rows(y - 1)(0)
                            Constants.SEGMENTS(weightedRandom(adjustWeightByY(up.bottom_weights, y)))

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
                                Constants.SEGMENTS(weightedRandom(adjustWeightByY(new_weights, y)))
                        }

                    currentRow :+ segment
                }

                rows :+ row
            }

        matriz.foreach { row =>
            println(row.map(_.id).mkString(" | "))
        }
    }

}

