
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
    is_alive: Boolean,

    last_checkpoint : Vector2 = Vector2(0,0)
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
                        c == "P"
                    }
                }
            }
        }

        def cellAt(from_x: Float, from_y: Float): Option[String] = {
            val col_left  = (from_x / cell_size).toInt
            val col_right = ((from_x + stats.size.x - 1) / cell_size).toInt
            val row_top   = (from_y / cell_size).toInt
            val row_bot   = ((from_y + stats.size.y - 1) / cell_size).toInt

            (for {
                row <- row_top to row_bot
                col <- col_left to col_right
                if row >= 0 && row < celdas.length && col >= 0 && col < celdas(row).length
                c = celdas(row)(col)
                if c == "S" || c == "C"
            } yield c).headOption
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

        val resolved = this.copy(
            coords      = Vector2(final_x, final_y),
            velocity    = Vector2(final_vx, final_vy),
            is_grounded = is_grounded2
        )

        cellAt(final_x, final_y) match {
            case Some("S") => resolved.copy(coords = last_checkpoint, velocity = Vector2(0f, 0f))
            case Some("C") => resolved.copy(last_checkpoint = Vector2(final_x, final_y))
            case _ => resolved
        }
    }
}
