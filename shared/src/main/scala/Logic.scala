import scala.compiletime.uninitialized

object Logic:

    var player: Player = uninitialized
    var map: Vector[Vector[String]] = uninitialized

    def init(p: Player, m: Vector[Vector[String]]): Unit = {
        player = p
        map = m
    }

    def update(input: InputState, delta_time: Double): Unit = {
        player = player.update(input, delta_time.toFloat, map)
    }
