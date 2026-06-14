import scala.util.Random
import scala.annotation.tailrec
import upickle.default.*

case class GameLoopInfo(
    current_time: Double,
    last_time: Double
)

object Constants{
    val BLOCK_SIZE = 10
    val SEGMENT_SIZE = 2

    val FAMILIES: Map[String, SegmentFamily] =
    Map(
        "empty" -> SegmentFamily(
            id = "empty",
            children = Map(
                "empty_default" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector(".", ".")
                    )
                )
            ),
            right_weights = Map(
            "empty"          -> 1.00f,
            "platform_top"   -> 0.20f,
            "platform_left"  -> 0.08f,   
            "platform_full"  -> 0.05f,
            "platform_bottom"-> 0.03f
            ),
            bottom_weights = Map(
                "empty"           -> 2.00f,
                "platform_bottom" -> 0.30f,
                "platform_diag1"  -> 0.05f,
                "platform_diag2"  -> 0.05f
            )
        ),

        "platform_top" -> SegmentFamily(
            id = "platform_top",
            children = Map(
                "platform_top" -> Segment(
                    pattern = Vector(
                        Vector("P", "P"),
                        Vector(".", "."))
                )
            ),
            right_weights = Map(
                "empty"               -> 1.00f,
                "platform_top"        -> 0.08f,
                "platform_cube_top_right" -> 0.05f,
                "platform_cube_top_left"  -> 0.05f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.20f,
                "platform_top"    -> 0.00f
            )
        ),

        "platform_bottom" -> SegmentFamily(
            id = "platform_bottom",
            children = Map(
                "platform_bottom" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector("P", "P"))
                ),
                "platform_bottom_spike" -> Segment(
                    pattern = Vector(
                        Vector(".", "S"),
                        Vector("P", "P"))
                ),
                "platform_bottom_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"                  -> 1.00f,
                "platform_bottom"        -> 0.05f,
                "platform_cube_bottom_right" -> 0.08f,
                "platform_cube_bottom_left"  -> 0.08f,
                "platform_diag2"         -> 0.15f,
                "platform_full"          -> 0.05f,
                "platform_left"          -> 0.05f,
                "platform_right"         -> 0.05f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_top"   -> 0.03f
            )
        ),

        "platform_left" -> SegmentFamily(
            id = "platform_left",
            children = Map(
                "platform_left" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector("P", "."))
                )
            ),
            right_weights = Map(
                "empty"                  -> 1.00f,
                "platform_cube_bottom_left" -> 0.08f,
                "platform_top"           -> 0.10f,
                "platform_bottom"        -> 0.10f,
                "platform_diag1"         -> 0.10f
            ),
            bottom_weights = Map(
                "platform_top"   -> 0.30f,
                "empty"          -> 1.00f
            )
        ),

        "platform_right" -> SegmentFamily(
            id = "platform_right",
            children = Map(
                "platform_right" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector(".", "P"))
                )
            ),
            right_weights = Map(
                "empty"                     -> 1.00f,
                "platform_top"              -> 0.30f,
                "platform_bottom"           -> 0.25f,
                "platform_diag1"            -> 0.20f,
                "platform_cube_bottom_left" -> 0.15f
            ),
            bottom_weights = Map(
                "empty"        -> 1.00f,
                "platform_top" -> 0.30f
            )
        ),

        "platform_full" -> SegmentFamily(
            id = "platform_full",
            children = Map(
                "platform_full" -> Segment(
                    pattern = Vector(
                        Vector("P", "P"),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"          -> 1.00f,
                "platform_left"  -> 0.20f,
                "platform_bottom"-> 0.10f,
                "platform_diag1" -> 0.10f,
                "platform_full"  -> 0.08f
            ),
            bottom_weights = Map(
                "empty"          -> 1.00f,
                "platform_full"  -> 0.05f
            )
        ),

        "platform_diag1" -> SegmentFamily(
            id = "platform_diag1",
            children = Map(
                "platform_diag1" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector("P", "P"))
                ),
                "platform_diag1_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("P", "C"),
                        Vector("P", "P"))
                ),
                "platform_diag1_spike" -> Segment(
                    pattern = Vector(
                        Vector("P", "S"),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"                     -> 1.00f,
                "platform_bottom"           -> 0.50f,
                "platform_diag2"            -> 0.35f,
                "platform_cube_bottom_left" -> 0.25f
            ),
            bottom_weights = Map(
                "empty"        -> 1.00f,
                "platform_top" -> 0.20f
            )
        ),

        "platform_diag2" -> SegmentFamily(
            id = "platform_diag2",
            children = Map(
                "platform_diag2" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector("P", "P"))
                ),
                "platform_diag2_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "P"),
                        Vector("P", "P"))
                ),
                "platform_diag2_spike" -> Segment(
                    pattern = Vector(
                        Vector("S", "P"),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"                   -> 1.00f,
                "platform_left"           -> 0.20f,
                "platform_full"           -> 0.20f,
                "platform_top"            -> 0.15f,
                "platform_diag1"          -> 0.20f,
                "platform_cube_top_left"  -> 0.15f,
                "platform_cube_top_right" -> 0.15f
            ),
            bottom_weights = Map(
                "empty"        -> 1.00f,
                "platform_top" -> 0.20f
            )
        ),

        "platform_cube_top_right" -> SegmentFamily(
            id = "platform_cube_top_right",
            children = Map(
                "platform_cube_top_right" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector(".", "."))
                )
            ),
            right_weights = Map(
                "empty"                   -> 1.00f,
                "platform_top"            -> 0.30f,
                "platform_diag1"          -> 0.15f,
                "platform_cube_top_left"  -> 0.20f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.30f,
                "platform_top"    -> 0.10f
            )
        ),

        "platform_cube_top_left" -> SegmentFamily(
            id = "platform_cube_top_left",
            children = Map(
                "platform_cube_top_left" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector(".", "."))
                )
            ),
            right_weights = Map(
                "empty"          -> 1.00f,
                "platform_top"   -> 0.30f,
                "platform_diag1" -> 0.15f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.35f,
                "platform_top"    -> 0.10f
            )
        ),

        "platform_cube_bottom_right" -> SegmentFamily(
            id = "platform_cube_bottom_right",
            children = Map(
                "platform_cube_bottom_right" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector(".", "P"))
                ),
                "platform_cube_bottom_right_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector(".", "C"),
                        Vector(".", "P"))
                ),
                "platform_cube_bottom_right_spike" -> Segment(
                    pattern = Vector(
                        Vector(".", "S"),
                        Vector(".", "P"))
                )
            ),
            right_weights = Map(
                "empty"                     -> 1.00f,
                "platform_bottom"           -> 0.30f,
                "platform_diag2"            -> 0.20f,
                "platform_full"             -> 0.05f,
                "platform_cube_bottom_left" -> 0.10f
            ),
            bottom_weights = Map(
                "empty"                   -> 1.00f,
                "platform_top"            -> 0.30f,
                "platform_cube_top_right" -> 0.25f
            )
        ),

        "platform_cube_bottom_left" -> SegmentFamily(
            id = "platform_cube_bottom_left",
            children = Map(
                "platform_cube_bottom_left" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector("P", "."))
                ),
                "platform_cube_bottom_left_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."),
                        Vector("P", "."))
                ),
                "platform_cube_bottom_left_spike" -> Segment(
                    pattern = Vector(
                        Vector("S", "."),
                        Vector("P", "."))
                )
            ),
            right_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.30f,
                "platform_diag2"  -> 0.20f
            ),
            bottom_weights = Map(
                "empty"                   -> 1.00f,
                "platform_top"            -> 0.30f,
                "platform_cube_top_left"  -> 0.20f,
                "platform_diag1"          -> 0.15f
            )
        )
    )

    val CHARACTERS: Map[String, PlayerStats] =
    Map(
        "Crumb" -> PlayerStats(speed = 120f, jumpForce = -200f, size = Vector2(9f, 9f)),
        "Spider" -> PlayerStats(speed = 120, jumpForce = -250f, size = Vector2(9f, 18f)),
        "Gnome" -> PlayerStats(speed = 120, jumpForce = -200, size = Vector2(9f, 9f))
    )
}

object BiomeZones {
    def biomeAt(y: Int, maxY: Int): String = {
        val ratio = y.toFloat / maxY
        if (ratio >= 0.20f) "platform"
        else "empty"
    }
}

case class Vector2
(
    val x: Float,
    val y: Float
)

case class InputState(
    moveLeft: Boolean = false,
    moveRight: Boolean = false,
    jump: Boolean = false
) derives ReadWriter

case class PlayerStats(
    speed: Float,
    jumpForce: Float,
    size: Vector2
)

case class Player
(
    coords: Vector2,
    velocity: Vector2, 
    
    current_time: Float,
    max_time: Float,
    
    stats: PlayerStats,
    is_grounded: Boolean,
    is_alive: Boolean
)
{
    def update(input: InputState, dt: Float, map: Vector[Vector[String]]): Player = {
        if(!is_alive) return this
        
        val gravity = 800f

        val new_time = current_time - dt
        if (new_time <= 0f) return this.copy(current_time = 0f, is_alive = false)

        val vx = if(input.moveLeft) -stats.speed
                 else if(input.moveRight) stats.speed
                 else 0f
        val vy = if(input.jump && is_grounded) stats.jumpForce
                 else if(is_grounded) 0f
                 else velocity.y + gravity * dt
    
        val new_velocity = Vector2(vx,vy)
        val proposed = Vector2(coords.x + vx * dt, coords.y + vy * dt)
        val after_physics = resolveCollisions(proposed, new_velocity, map)

        val cell_size  = Constants.BLOCK_SIZE
        val map_width  = map(0).length * cell_size
        val map_height = map.length    * cell_size

        val clamped = after_physics.copy(
            coords = Vector2(
                after_physics.coords.x.max(0f).min(map_width  - stats.size.x),
                after_physics.coords.y.max(0f).min(map_height - stats.size.y)
            )
        )
        /* println(s"""
                coords: ${coords}
                proposed: ${proposed}
                velocity: ${new_velocity}
                col/row: (${proposed.x / 20}, ${proposed.y / 20})
                grounded: $is_grounded
                time: $new_time
                """) */
        clamped.copy(current_time = new_time)
    }
    
    def resolveCollisions(proposed: Vector2, vel: Vector2, celdas: Vector[Vector[String]]): Player = {
        val cell_size = Constants.BLOCK_SIZE

        def bodyOverlaps(from_x: Float, from_y: Float): Boolean = {
            val col_left  = (from_x / cell_size).toInt
            val col_right = ((from_x + stats.size.x - 1) / cell_size).toInt
            val row_top   = (from_y / cell_size).toInt
            val row_bot   = ((from_y + stats.size.y - 1) / cell_size).toInt

            (row_top to row_bot).exists { row =>
                (col_left to col_right).exists { col =>
                    row >= 0 && row < celdas.length &&
                    col >= 0 && col < celdas(row).length && {
                        val c = celdas(row)(col)
                        c == "G" || c == "P"
                    }
                }
            }
        }

        // ── Eje Y suelo
        val foot_y     = proposed.y + stats.size.y
        val snap_row_y = (foot_y / cell_size).toInt

        val (mid_y, mid_vy, is_grounded) =
            if (vel.y >= 0 && bodyOverlaps(coords.x, proposed.y + 1f))
                ((snap_row_y * cell_size - stats.size.y).toFloat, 0f, true)
            else
                (proposed.y, vel.y, false)

        // ── Eje Y techo
        val head_y     = proposed.y
        val snap_row_h = (head_y / cell_size).toInt

        val (final_y, final_vy, is_grounded2) =
            if (vel.y < 0 && bodyOverlaps(coords.x, proposed.y))
                (((snap_row_h + 1) * cell_size).toFloat, 0f, is_grounded)
            else
                (mid_y, mid_vy, is_grounded)

        // ── Eje X
        val proposed_x = if (vel.x > 0) proposed.x else proposed.x
        val snap_col_x = if (vel.x > 0)
            ((proposed.x + stats.size.x) / cell_size).toInt
        else
            (proposed.x / cell_size).toInt

        val (final_x, final_vx) =
            if (vel.x != 0 && bodyOverlaps(proposed.x, final_y))
                if (vel.x > 0)
                    ((snap_col_x * cell_size - stats.size.x).toFloat, 0f)
                else
                    (((snap_col_x + 1) * cell_size).toFloat, 0f)
            else
                (proposed.x, vel.x)

        this.copy(
            coords      = Vector2(final_x, final_y),
            velocity    = Vector2(final_vx, final_vy),
            is_grounded = is_grounded2
        )
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
        name == "platform_bottom_checkpoint"  ||
        name == "platform_diag1_checkpoint"   ||
        name == "platform_diag2_checkpoint"   ||
        name == "platform_cube_bottom_right_checkpoint" ||
        name == "platform_cube_bottom_left_checkpoint"
    }

    def isEmpty(segment: Segment): Boolean =
        getFamily(segment).id == "empty"

    def selectValidSegment(
        familyId: String, 
        up: Segment, 
        checkpointCooldown: Int, 
        checkpointPlaced: Boolean,
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

        if (candidates.nonEmpty)
            candidates(Random.nextInt(candidates.size))
        else
            Constants.FAMILIES("empty").children("empty_default")
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
                                selectValidSegment(nextFamily, left, state.checkpointCooldown, columnState.checkpointPlaced)
                            } else if (x == 0) {
                                val up = currentRows(y - 1)(0)
                                val upFamily = getFamily(up)
                                val nextFamily = weightedRandom(adjustWeightByY(upFamily.bottom_weights,y))
                                selectValidSegment(nextFamily, up, state.checkpointCooldown, columnState.checkpointPlaced)
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
                                    selectValidSegment(nextFamily, up, state.checkpointCooldown, columnState.checkpointPlaced)
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

