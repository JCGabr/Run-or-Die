
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

    last_checkpoint : Vector2 = Vector2(0,0),

    claimed_checkpoint: Boolean = false
)
{
    def update(input: InputState, dt: Float, map: Vector[Vector[String]]): Player = {
        if(!is_alive) return this
        
        val gravity = 800f

        val new_time = current_time - dt
        if (new_time <= 0f) return this.copy(current_time = 0f, is_alive = false)

        val vx = if(input.moveLeft) 
                    -stats.speed
                 else if(input.moveRight) 
                    stats.speed
                 else 
                    0f

        val vy = if(input.jump && is_grounded) 
                    stats.jumpForce
                 else if(is_grounded) 
                    0f
                 else 
                    velocity.y + gravity * dt
    
        val new_velocity = Vector2(vx,vy)
        val proposed = Vector2(coords.x + vx * dt, coords.y + vy * dt)
        val after_physics = resolveCollisions(proposed, new_velocity, map)

        val cell_size = Constants.BLOCK_SIZE
        val map_width = map(0).length * cell_size
        val map_height = map.length * cell_size

        val clamped = after_physics.copy(
            coords = Vector2(
                after_physics.coords.x.max(0f).min(map_width  - stats.size.x),
                after_physics.coords.y.max(0f).min(map_height - stats.size.y)
            )
        )

        val final_time = 
            if (after_physics.current_time == current_time)
                new_time
            else
                after_physics.current_time
        /* println(s"""
                coords: ${coords}
                proposed: ${proposed}
                velocity: ${new_velocity}
                col/row: (${proposed.x / 20}, ${proposed.y / 20})
                grounded: $is_grounded
                time: $new_time
                """) */
        clamped.copy(current_time = final_time)
    }
    
    def cellsOverlapping(x: Float, y: Float, width: Float, height: Float, cellSize: Int, cells: Vector[Vector[String]]): Seq[String] = {
        val colLeft = (x / cellSize).toInt
        val colRight = ((x + width - 1) / cellSize).toInt
        val rowTop = (y / cellSize).toInt
        val rowBottom = ((y + height - 1) / cellSize).toInt

        for {
            row <- rowTop to rowBottom
            col <- colLeft to colRight
            if row >= 0 && row < cells.length
            if col >= 0 && col < cells(row).length
        } yield cells(row)(col)
    }

    def isBlocked(x: Float, y: Float, width: Float, height: Float, cellSize: Int, cells: Vector[Vector[String]]): Boolean =
        cellsOverlapping(x, y, width, height, cellSize, cells).contains("P")

    def findHazard(x: Float, y: Float, width: Float, height: Float, cellSize: Int, cells: Vector[Vector[String]]): Option[String] =
        cellsOverlapping(x, y, width, height, cellSize, cells).find(c => c == "S" || c == "C")

    def resolveCollisions(proposed: Vector2, vel: Vector2, cells: Vector[Vector[String]]): Player = {
        val cellSize = Constants.BLOCK_SIZE
        val width = stats.size.x
        val height = stats.size.y

        val footY = proposed.y + height
        val snapRowFoot = (footY / cellSize).toInt
        val touchesFloor = vel.y >= 0 && isBlocked(coords.x, proposed.y + 1f, width, height, cellSize, cells)

        val groundedY = if (touchesFloor) (snapRowFoot * cellSize - height).toFloat else proposed.y
        val groundedVy = if (touchesFloor) 0f else vel.y
        val isGrounded = touchesFloor

        val headY = proposed.y
        val snapRowHead = (headY / cellSize).toInt
        val touchesCeiling = vel.y < 0 && isBlocked(coords.x, proposed.y, width, height, cellSize, cells)

        val finalY = if (touchesCeiling) ((snapRowHead + 1) * cellSize).toFloat else groundedY
        val finalVy = if (touchesCeiling) 0f else groundedVy

        val snapColX =
            if (vel.x > 0) ((proposed.x + width) / cellSize).toInt
            else (proposed.x / cellSize).toInt

        val touchesWall = vel.x != 0 && isBlocked(proposed.x, finalY, width, height, cellSize, cells)

        val finalX =
            if (touchesWall && vel.x > 0) (snapColX * cellSize - width).toFloat
            else if (touchesWall && vel.x < 0) ((snapColX + 1) * cellSize).toFloat
            else proposed.x

        val finalVx = if (touchesWall) 0f else vel.x

        val resolved = this.copy(
            coords = Vector2(finalX, finalY),
            velocity = Vector2(finalVx, finalVy),
            is_grounded = isGrounded
        )

        findHazard(finalX, finalY, width, height, cellSize, cells) match {
            case Some("S") =>
                resolved.copy(coords = last_checkpoint, velocity = Vector2(0f, 0f), current_time = current_time - 1)
            case Some("C") if finalX > last_checkpoint.x + cellSize * 3 =>
                resolved.copy(last_checkpoint = Vector2(finalX, finalY), current_time = max_time, claimed_checkpoint = true)
            case _ =>
                resolved
        }
    }
}
