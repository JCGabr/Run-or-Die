import scala.util.Random
import scala.annotation.tailrec

//TO CHECK
object BiomeZones {
    def biomeAt(y: Int, maxY: Int): String = {
        val ratio = y.toFloat / maxY
        if (ratio >= 0.20f) "platform"
        else "empty"
    }
}

case class SegmentFamily
(
    val id: String,
    children: Map[String, Segment],
    right_weights: Map[String, Float],
    bottom_weights: Map[String, Float]
)

case class Segment(
    pattern: Vector[Vector[String]],
)

case class GenerationState(
    rows: Vector[Vector[Segment]],
    checkpointCooldown: Int
)

case class ColumnState(
    rows: Vector[Vector[Segment]],
    checkpointPlaced: Boolean
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
    private final def weightedRandomAuxiliar[T](random: Float, map_probability: Seq[(T, Float)]): T = {
        map_probability match{
            case Nil =>
                throw new IllegalArgumentException(
                    "No probabilities available."
                )
            case (name, _) :: Nil => name
            case (name, weight) :: tail => 
                if (random <= weight) 
                    name
                else
                    weightedRandomAuxiliar(random - weight, tail)
        }
    }

    def weightedRandom[T](map_probability: Map[T, Float]): T = {
        val filtered = map_probability.filter { case (_, v) => v > 0.0f }
        if (filtered.isEmpty)
            throw new IllegalArgumentException(
                "No positive probabilities available."
            )
        val totalWeight: Float = map_probability.values.sum
        val random: Float = Random.between(0.0f, totalWeight)
        weightedRandomAuxiliar(random, map_probability.toSeq)
    }

    def adjustWeightByY(map_probability: Map[String, Float], y: Int): Map[String, Float] = {
        val biome = BiomeZones.biomeAt(y, max_y)

        val platformFamilies = Set(
            "platform_top", "platform_bottom", "platform_left",
            "platform_right", "platform_full", "platform_diag1", "platform_diag2",
            "platform_cube_top_right", "platform_cube_top_left",
            "platform_cube_bottom_right", "platform_cube_bottom_left"
        )

        map_probability.map { case (k, v) =>
            val multiplier = biome match {
                case "empty" =>
                    if (k == "empty") 1.0f
                    else if (platformFamilies(k)) 0.0f
                    else 0.0f
                case "platform" =>
                    if (platformFamilies(k)) 5.0f
                    else if (k == "empty") 1.0f
                    else 0.0f
                case _ => 1.0f
            }
            k -> (v * multiplier)
        }
    }

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

    def isCheckpoint(segment: Segment): Boolean = {
        val name = segmentName(segment)
        name.endsWith("_checkpoint")
        /*name == "platform_bottom_checkpoint"  ||
        name == "platform_diag1_checkpoint"   ||
        name == "platform_diag2_checkpoint"   ||
        name == "platform_cube_bottom_right_checkpoint" ||
        name == "platform_cube_bottom_left_checkpoint"*/
    }

    def isSpike(segment: Segment): Boolean = {
        val name = segmentName(segment)
        name.endsWith("_spike")
    }

    def isEmpty(segment: Segment): Boolean =
        getFamily(segment).id == "empty"

    def selectValidSegment(
        familyId: String, 
        up: Segment, 
        checkpointCooldown: Int, 
        checkpointPlaced: Boolean,
        spikeMultiplier: Float
    ): Segment = {
        val candidates =
            Constants.FAMILIES(familyId)
                .children
                .values
                .filter { candidate =>
                    (
                        !isCheckpoint(candidate) ||
                        (
                            checkpointCooldown == 0 &&
                            !checkpointPlaced
                        )
                    )
                }
                .toVector

        if (candidates.nonEmpty) {
            val weightedCandidates =
                candidates.map { candidate =>
                    val weight =
                        if (isSpike(candidate))
                            spikeMultiplier
                        else
                            1.0f

                    candidate -> weight
                }.toMap

            weightedRandom(weightedCandidates)

        } else {
            Constants.FAMILIES("empty").children("empty_default")
        }
    }


    def generate():  Vector[Vector[Segment]] = {
        val root: Segment = Constants.FAMILIES("empty").children("empty_default")
        val reserved: Map[(Int, Int), Segment] = Map.empty
        val protectedCells: Set[(Int, Int)] = Set.empty

        val initialState =
            GenerationState(
                rows = Vector.fill(max_y + 1)(Vector.empty[Segment]),
                checkpointCooldown = 0
            )
        val finalState =
            (0 to max_x).foldLeft(initialState) { (state, x) =>
                val progress = x.toFloat / max_x
                val spikeMultiplier =
                    if (progress < 0.2f)
                        0.0f
                    else
                        0.2f + ((progress - 0.2f) / 0.8f) * 1.8f

                val columnState =
                    (0 to max_y).foldLeft(
                        ColumnState(
                            rows = state.rows,
                            checkpointPlaced = false
                        )
                    ) { (columnState, y) =>
                    val currentRows = columnState.rows
                    val segment: Segment =
                        if (reserved.contains((x, y))) {
                            reserved((x, y))
                        }
                        else if (protectedCells.contains((x,y))) {
                            Constants.FAMILIES("empty")
                                .children("empty_default")
                        }
                        else {
                            if (y == 0 && x == 0)
                                root
                            else if (y == 0) {
                                val left = currentRows(y)(x - 1)
                                val leftFamily = getFamily(left)
                                val nextFamily = weightedRandom(adjustWeightByY(leftFamily.right_weights,y))
                                selectValidSegment(nextFamily, left, state.checkpointCooldown, columnState.checkpointPlaced, spikeMultiplier)
                            } else if (x == 0) {
                                val up = currentRows(y - 1)(0)
                                val upFamily = getFamily(up)
                                val nextFamily = weightedRandom(adjustWeightByY(upFamily.bottom_weights,y))
                                selectValidSegment(nextFamily, up, state.checkpointCooldown, columnState.checkpointPlaced, spikeMultiplier)
                            } else {
                                val left  = currentRows(y)(x - 1)
                                val up = currentRows(y - 1)(x)
                                val leftFamily = getFamily(left)
                                val upFamily = getFamily(up)

                                val new_weights =
                                    upFamily.bottom_weights.keySet
                                        .intersect(leftFamily.right_weights.keySet)
                                        .map(k => k -> ((upFamily.bottom_weights(k) + leftFamily.right_weights(k)) / 2))
                                        .toMap
                               
                                if (new_weights.isEmpty) 
                                    up
                                else 
                                    val nextFamily = weightedRandom(adjustWeightByY(new_weights, y))
                                    selectValidSegment(nextFamily, up, state.checkpointCooldown, columnState.checkpointPlaced, spikeMultiplier)
                            }
                        }

                    ColumnState(
                        rows =
                            currentRows.updated(
                                y,
                                currentRows(y) :+ segment
                            ),
                        checkpointPlaced = columnState.checkpointPlaced || isCheckpoint(segment)
                    )
                }

                GenerationState(
                    rows = columnState.rows,
                    checkpointCooldown =
                        if (columnState.checkpointPlaced)
                            10
                        else
                            math.max(0, state.checkpointCooldown - 1)
                )
            }
        finalState.rows
    }
}

