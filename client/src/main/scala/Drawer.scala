import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document

object Drawer:

    val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]        
    val mapgame: MapGame = new MapGame(20, 0, 10, 0)
    val matriz = mapgame.generate()
    def render(delta_time: Double): Unit = {
        // Ajustar el tamaño interno del canvas al tamaño visual
        canvas.width = canvas.clientWidth
        canvas.height = canvas.clientHeight

        // Calcular tileSize para que llene exactamente el canvas
        val tileSizeX = canvas.width / 20
        val tileSizeY = canvas.height / (10 + 1)
        val tileSize = math.min(tileSizeX, tileSizeY)

        matriz.zipWithIndex.foreach { case (row, y) =>
            row.zipWithIndex.foreach { case (segment, x) =>
                ctx.fillStyle = segment.id match {
                    case "ground"   => "#8B4513"
                    case "platform" => "#228B22"
                    case _          => "#87CEEB"
                }
                ctx.fillRect(x * tileSize, y * tileSize, tileSize, tileSize)
            }
        }
    }