
object Logic:

    var player: Player = _
    var map: Vector[Vector[String]] = _

    def init(p: Player, m: Vector[Vector[String]]): Unit = {
        player = p
        map = m
    }

    def update(input: InputState, delta_time: Double): Unit = {
        player = player.update(input, delta_time.toFloat, map)
    }
