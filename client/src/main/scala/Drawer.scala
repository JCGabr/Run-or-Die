import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document

object Drawer:

    val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]        

    def render(map: Vector[Vector[String]], players: List[PlayerSnap], delta_time: Double): Unit = {
        canvas.width = canvas.clientWidth
        canvas.height = canvas.clientHeight

        val totalCols = map.head.length
        val totalRows = map.length

        val tileSizeX = canvas.width / totalCols
        val tileSizeY = canvas.height / totalRows

        val tileSize = math.min(tileSizeX, tileSizeY)
        
        val cellPx = (Constants.BLOCK_SIZE * Constants.SEGMENT_SIZE) / 2

        val scale = tileSize.toDouble / cellPx

        ctx.save()
        ctx.scale(scale, scale) 

        map.zipWithIndex.foreach { case (row, ry) =>
            row.zipWithIndex.foreach { case (cell, cx) =>
                ctx.fillStyle = cell match {
                    case "G" => "#8B4513"
                    case "P" => "#228B22"
                    case "S" => "#FF0000"
                    case "C" => "#FFFF00"
                    case _   => "#87CEEB"
                }
                ctx.fillRect(
                    cx * cellPx,
                    ry * cellPx,
                    cellPx,
                    cellPx
                )
            }
        }

        players.foreach { p =>
            ctx.fillStyle = "#1534e0"
            ctx.fillRect(
                p.x,
                p.y,
                p.sizeX,
                p.sizeY
            )
        }
        
        ctx.restore()
    }