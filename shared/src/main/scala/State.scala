import scala.util.Random
import scala.annotation.tailrec

case class GameLoopInfo(
    current_time: Double,
    last_time: Double
)

// Familia por segmentos (distintas variaciones)
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
            left_weights = Map(
                "empty"           -> 0.60f,
                "ground"          -> 0.05f,
                "platform_top"    -> 0.10f,
                "platform_diag1"  -> 0.10f,
                "platform_diag2"  -> 0.10f,
                "platform_right"  -> 0.05f
            ),
            top_weights = Map(
                "empty"           -> 0.80f,
                "platform_top"    -> 0.15f,
                "platform_diag1"  -> 0.025f,
                "platform_diag2"  -> 0.025f
            )
        ),
        "ground_full" -> SegmentFamily(
            id = "ground_full",
            children = Map(
                "ground_full" -> Segment(
                    pattern = Vector(
                        Vector("G", "G"),
                        Vector("G", "G")
                    )
                )
            ),
            left_weights = Map(
                "ground_full"   -> 0.50f,
                "ground_diag1"  -> 0.20f,  // diag1 termina con columna derecha llena
                "ground_diag2"  -> 0.10f,
                "empty"         -> 0.20f
            ),
            top_weights = Map(
                "ground_full"   -> 0.70f,
                "ground_diag1"  -> 0.15f,
                "ground_diag2"  -> 0.15f
            )
        ),

        "ground_diag1" -> SegmentFamily(
            id = "ground_diag1",
            children = Map(
                "ground_diag1" -> Segment(
                    pattern = Vector(
                        Vector("G", "."),
                        Vector("G", "G")
                    )
                )
            ),
            left_weights = Map(
                "ground_full"   -> 0.50f,  // a su izquierda tiene sentido ground sólido
                "ground_diag2"  -> 0.20f,
                "empty"         -> 0.30f
            ),
            top_weights = Map(
                "empty"         -> 0.80f,  // arriba de una diagonal suele haber aire
                "ground_full"   -> 0.20f
            )
        ),

        "ground_diag2" -> SegmentFamily(
            id = "ground_diag2",
            children = Map(
                "ground_diag2" -> Segment(
                    pattern = Vector(
                        Vector(".", "G"),
                        Vector("G", "G")
                    )
                )
            ),
            left_weights = Map(
                "empty"         -> 0.50f,  // viene de aire, es el inicio de una subida
                "ground_full"   -> 0.30f,
                "ground_diag2"  -> 0.20f
            ),
            top_weights = Map(
                "empty"         -> 0.80f,
                "ground_full"   -> 0.20f
            )
        ),

        "ground_bottom" -> SegmentFamily(
            id = "ground_bottom",
            children = Map(
                "ground_bottom" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector("G", "G")
                    )
                ),
                "ground_bottom_spike" -> Segment(
                    pattern = Vector(
                        Vector("S", "."),
                        Vector("G", "G")
                    )
                ),
                "ground_bottom_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."),
                        Vector("G", "G")
                    )
                )
            ),
            left_weights = Map(
                "ground_full"    -> 0.40f,
                "ground_bottom"  -> 0.40f,
                "empty"          -> 0.20f
            ),
            top_weights = Map(
                "empty"          -> 0.50f,  // arriba hay aire, es el borde superior del suelo
                "ground_bottom"  -> 0.10f,
                "top_bottom" -> 0.40f
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
            left_weights = Map(
                "platform_top"     -> 0.50f,  // continuación natural
                "platform_full"    -> 0.25f,  // viene de bloque sólido
                "platform_left"    -> 0.15f,  // viene del borde izquierdo
                "empty"            -> 0.10f   // poco probable que arranque de aire
            ),
            top_weights = Map(
                "empty"            -> 0.85f,  // arriba siempre casi aire
                "platform_bottom"     -> 0.15f   // puede apilarse
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
                        Vector("S", "."), 
                        Vector("P", "P"))
                ),
                "platform_bottom_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."), 
                        Vector("P", "P"))
                )
            ),
            left_weights = Map(
                "platform_bottom"  -> 0.50f,  // continuación natural
                "platform_full"    -> 0.25f,
                "platform_left"    -> 0.15f,
                "empty"            -> 0.10f   // poco probable que arranque de aire
            ),
            top_weights = Map(
                "empty"            -> 0.55f,
                "platform_top"     -> 0.35f,  // encima puede haber techo
                "platform_full"    -> 0.10f
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
            left_weights = Map(
                "empty"            -> 0.70f,  // es el borde izquierdo, viene de aire
                "platform_top"     -> 0.15f,
                "platform_bottom"  -> 0.15f
            ),
            top_weights = Map(
                "empty"            -> 0.50f,
                "platform_left"    -> 0.50f   // se apila verticalmente
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
            left_weights = Map(
                "platform_full"    -> 0.45f,  // viene de bloque sólido
                "platform_top"     -> 0.30f,
                "platform_bottom"  -> 0.25f
                // sin empty: platform_right no debería arrancar de aire
            ),
            top_weights = Map(
                "empty"            -> 0.50f,
                "platform_right"   -> 0.50f   // se apila verticalmente
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
            left_weights = Map(
                "platform_full"    -> 0.45f,
                "platform_left"    -> 0.35f,
                "platform_bottom"  -> 0.20f
                // sin empty: un bloque sólido no aparece de la nada
            ),
            top_weights = Map(
                "platform_full"    -> 0.55f,
                "platform_bottom"  -> 0.45f
                // sin empty: siempre tiene algo encima
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
            left_weights = Map(
                "platform_bottom"  -> 0.45f,  // transición natural desde plataforma
                "platform_top"     -> 0.35f,
                "empty"            -> 0.20f
            ),
            top_weights = Map(
                "empty"            -> 0.65f,
                "platform_top"     -> 0.35f
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
            left_weights = Map(
                "empty"            -> 0.45f,  // es el inicio de una transición
                "platform_top"     -> 0.35f,
                "platform_diag1"   -> 0.20f
            ),
            top_weights = Map(
                "empty"            -> 0.65f,
                "platform_top"     -> 0.35f
            )
        )
    )

    object BiomeZones {
        def biomeAt(y: Int, maxY: Int): String = {
            val ratio = y.toFloat / maxY
            if (ratio >= 0.85f)     "ground"
            else if (ratio >= 0.20f) "platform"
            else                    "empty"
        }
    }
    val CHARACTERS: Map[String, PlayerStats] =
    Map(
        "Crumb" -> PlayerStats(speed = 100f, jumpForce = -200f, size = Vector2(10f, 10f)),
        "Spider" -> PlayerStats(speed = 100, jumpForce = -250f, size = Vector2(10f, 20f)),
        "Gnome" -> PlayerStats(speed = 120, jumpForce = -200, size = Vector2(9f, 9f))
        // gnome solo sufrira el 50% de las penalizaciones de tiempo?
    )
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
)

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
    left_weights: Map[String, Float],
    top_weights: Map[String, Float]
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

case class PathNode(
    x: Int,
    y: Int
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
        val biome = Constants.BiomeZones.biomeAt(y, max_y)

        val platformFamilies = Set(
            "platform_top", "platform_bottom", "platform_left",
            "platform_right", "platform_full", "platform_diag1", "platform_diag2"
        )
        val groundFamilies = Set(
            "ground", "ground_full", "ground_diag1", "ground_diag2", "ground_bottom"
        )

        map_probability.map { case (k, v) =>
            val multiplier = biome match {
                case "empty" =>
                    if (k == "empty")                1.0f
                    else if (platformFamilies(k))    0.0f
                    else if (groundFamilies(k))      0.0f
                    else                             0.0f
                case "platform" =>
                    if (platformFamilies(k))         5.0f
                    else if (k == "empty")           1.0f
                    else if (groundFamilies(k))      0.0f
                    else                             0.0f
                case "ground" =>
                    if (groundFamilies(k))           5.0f
                    else if (k == "empty")           0.5f
                    else if (platformFamilies(k))    0.0f
                    else                             0.0f
                case _ => 1.0f
            }
            k -> (v * multiplier)
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
        name == "ground_bottom_spike"        ||
        name == "ground_bottom_checkpoint"   ||
        name == "platform_bottom_spike"      ||
        name == "platform_bottom_checkpoint" ||
        name == "platform_diag1_spike"       ||
        name == "platform_diag1_checkpoint"  ||
        name == "platform_diag2_spike"       ||
        name == "platform_diag2_checkpoint"
    }

    def isCheckpoint(segment: Segment): Boolean = {
        val name = segmentName(segment)
        name == "ground_bottom_checkpoint"    ||
        name == "platform_bottom_checkpoint"  ||
        name == "platform_diag1_checkpoint"   ||
        name == "platform_diag2_checkpoint"
    }

    def isEmpty(segment: Segment): Boolean =
        getFamily(segment).id == "empty"

    def isDiagonal(segment: Segment): Boolean = {
        val name = segmentName(segment)
        name == "platform_diag1" ||
        name == "platform_diag2" ||
        name == "ground_diag1"   ||
        name == "ground_diag2"
    }

//----------------------Métricas-----------------------
    def consecutiveEmptyAtEnd(row: Vector[Segment]): Int = {
        row.reverse
            .takeWhile(isEmpty)
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

    def selectValidSegment(
        familyId: String, 
        up: Segment, 
        checkpointCooldown: Int, 
        checkpointPlaced: Boolean,
        allowCheckpoints: Boolean = false  // ← nuevo parámetro
    ): Segment = {
        val candidates =
            Constants.FAMILIES(familyId)
                .children
                .values
                .filter { candidate =>
                    isValidSegment(candidate, up) &&
                    (
                        !isCheckpoint(candidate) ||
                        (
                            allowCheckpoints &&
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

    def generatePath(startY: Int): Vector[PathNode] = {
        (1 to max_x).foldLeft(
            Vector(
                PathNode(0, startY)
            )
        ) { (path, x) =>

            val currentY =
                path.last.y

            val possibleMoves =
                Vector(-1, 0, 1)
                    .filter(move =>
                        currentY + move >= min_y &&
                        currentY + move <= max_y
                    )
            val moveWeights =
                Map(
                    -1 -> 0.15f,
                    0 -> 0.70f,
                    1 -> 0.15f
                )
            val validWeights =
                moveWeights.filter {
                    case (move, _) =>
                        possibleMoves.contains(move)
                }

            val move =
                weightedRandom(validWeights)

            val nextY =
                currentY + move

            path :+ PathNode(x, nextY)
        }
    }

    def segmentForTransition(
        current: PathNode,
        next: PathNode,
        checkpointCooldown: Int,
        checkpointPlaced: Boolean
    ): Segment = {

        val deltaY = next.y - current.y
        val biome  = Constants.BiomeZones.biomeAt(current.y, max_y)

        (biome, deltaY) match {

            case ("ground", -1) =>
                Constants.FAMILIES("ground_diag2").children("ground_diag2")

            case ("ground", 0) =>
                selectValidSegment("ground_bottom",
                    Constants.FAMILIES("empty").children("empty_default"),
                    checkpointCooldown, checkpointPlaced,
                    allowCheckpoints = true)

            case ("ground", 1) =>
                Constants.FAMILIES("ground_diag1").children("ground_diag1")

            case ("platform", -1) =>
                Constants.FAMILIES("platform_diag2").children("platform_diag2")

            case ("platform", 0) =>
                selectValidSegment("platform_bottom",
                    Constants.FAMILIES("empty").children("empty_default"),
                    checkpointCooldown, checkpointPlaced,
                    allowCheckpoints = true)

            case ("platform", 1) =>
                Constants.FAMILIES("platform_diag1").children("platform_diag1")

            case ("empty", _) =>
                Constants.FAMILIES("empty").children("empty_default")

            case _ =>
                throw new IllegalArgumentException(
                    s"Combinación no manejada: biome=$biome, deltaY=$deltaY"
                )
        }
    }

    def buildReservedPath(
        path: Vector[PathNode],
        checkpointCooldown: Int = 0
    ): (Map[(Int, Int), Segment], Int) = {

        val first = path.head
        val firstBiome = Constants.BiomeZones.biomeAt(first.y, max_y)
        val firstSegment = firstBiome match {
            case "ground"  => Constants.FAMILIES("ground_bottom").children("ground_bottom_checkpoint")
            case _         => Constants.FAMILIES("platform_bottom").children("platform_bottom_checkpoint")
        }

        val (base, finalCooldown) = path
            .tail
            .sliding(2)
            .collect {
                case Vector(current, next) => (current, next)
            }
            .foldLeft((Map[(Int, Int), Segment](((first.x, first.y) -> firstSegment)), 10)) {
                // ← mapa inicial ya tiene el primer checkpoint, cooldown arranca en 10
                case ((acc, cooldown), (current, next)) =>
                    val segment = segmentForTransition(
                        current, next, cooldown,
                        checkpointPlaced = false
                    )
                    val newCooldown =
                        if (isCheckpoint(segment)) 10
                        else math.max(0, cooldown - 1)
                    (acc + ((current.x, current.y) -> segment), newCooldown)
            }

        val last = path.last
        val lastBiome = Constants.BiomeZones.biomeAt(last.y, max_y)
        val lastSegment = lastBiome match {
            case "ground" => Constants.FAMILIES("ground_full").children("ground_full")
            case "empty"  => Constants.FAMILIES("empty").children("empty_default")
            case _        => Constants.FAMILIES("platform_bottom").children("platform_bottom")
        }

        (base + ((last.x, last.y) -> lastSegment), finalCooldown)
    }

    def buildProtectedCells(
        reserved: Map[(Int, Int), Segment]
    ): Set[(Int, Int)] = {

        reserved.toVector.flatMap {
            case ((x, y), segment) =>
                if (isDiagonal(segment))
                    Vector(
                        (x, y - 1),
                        (x, y - 2)
                    )
                else
                    Vector(
                        (x, y - 1)
                    )
        }
        .filter {
            case (cx, cy) =>
                cy >= min_y &&
                !reserved.contains((cx, cy)) 
        }
        .toSet
    }

    def buildForcedCells(
        reserved: Map[(Int, Int), Segment]
    ): Map[(Int, Int), Segment] = {
        reserved.toVector.flatMap {
            case ((x, y), segment) =>
                val name = segmentName(segment)
                if (name == "ground_diag1" || name == "ground_diag2")
                    Vector(
                        (x, y + 1) -> Constants.FAMILIES("ground_full").children("ground_full"),
                        (x, y + 2) -> Constants.FAMILIES("ground_full").children("ground_full")
                    )
                else
                    Vector.empty
        }
        .filter { case ((_, cy), _) => cy <= max_y }
        .toMap
    }

    def generate():  Vector[Vector[Segment]] = {
        // checkpoint, trampas
        val root: Segment = Constants.FAMILIES("empty").children("empty_default")
        val path = generatePath(max_y)
        val (reserved, pathCheckpointCooldown) = buildReservedPath(path)
        val protectedCells = buildProtectedCells(reserved)
        val forcedCells = buildForcedCells(reserved) 

        val initialState =
            GenerationState(
                rows = Vector.fill(max_y + 1)(Vector.empty[Segment]),
                checkpointCooldown = pathCheckpointCooldown
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
                        else if (forcedCells.contains((x, y)) && !reserved.contains((x, y)))
                            forcedCells((x, y))
                        else if (protectedCells.contains((x,y))) {
                            Constants.FAMILIES("empty")
                                .children("empty_default")
                        }
                        else {
                            if (y == 0 && x == 0)
                                root
                            else if (y == 0) {
                                val left = currentRows(y)(x - 1)
                                //Constants.SEGMENTS(weightedRandom(adjustWeightByY(left.left_weights, y, left.id))) //platform
                                // funcion(id, val1, val2 )
                                val leftFamily = getFamily(left)
                                val nextFamily = weightedRandom(adjustWeightByY(leftFamily.left_weights,y))
                                randomSegmentFromFamily(nextFamily)

                            } else if (x == 0) {
                                val up = currentRows(y - 1)(0)
                                //Constants.SEGMENTS(weightedRandom(adjustWeightByY(up.top_weights, y, up.id)))
                                val upFamily = getFamily(up)
                                val nextFamily = weightedRandom(adjustWeightByY(upFamily.top_weights,y))
                                selectValidSegment(nextFamily, up, state.checkpointCooldown, columnState.checkpointPlaced)

                            } else {
                                val left  = currentRows(y)(x - 1)
                                val up = currentRows(y - 1)(x)
                                val leftFamily = getFamily(left)
                                val upFamily = getFamily(up)

                                val new_weights =
                                    upFamily.top_weights.keySet
                                        .intersect(leftFamily.left_weights.keySet)
                                        .map(k => k -> ((upFamily.top_weights(k) + leftFamily.left_weights(k)) / 2))
                                        .toMap
                                val currentRow = currentRows(y)
                                val emptyCount = consecutiveEmptyAtEnd(currentRow)

                                if(shouldForceGround(y, emptyCount))
                                    Constants.FAMILIES("ground_full").children("ground_full")
                                    //randomSegmentFromFamily("ground")
                                else if (new_weights.isEmpty) 
                                    left
                                else 
                                    /*val nextFamily = weightedRandom(adjustWeightByY(new_weights,y))
                                    selectValidSegment(nextFamily, up, state.checkpointCooldown, columnState.checkpointPlaced)*/
                                    val nextFamily = weightedRandom(adjustWeightByY(new_weights, y))
                                    val chosen = selectValidSegment(nextFamily, up, state.checkpointCooldown, columnState.checkpointPlaced)
                                    if (nextFamily == "platform_bottom")
                                        println(s"($x,$y) nextFamily=platform_bottom, up=${segmentName(up)}, elegido=${segmentName(chosen)}")
                                    chosen
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
                    checkpointCooldown = state.checkpointCooldown
                )
            }
        reserved.foreach {
            case ((x, y), segment) =>

                println(
                    s"($x,$y) -> ${segmentName(segment)}"
                )
        }
        finalState.rows.foreach { row =>
            println(row.map(segmentName).mkString(" | "))
        }
        finalState.rows
    }
}

