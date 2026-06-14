import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document

object Drawer:

    val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]        

    def render(map: Vector[Vector[String]], players: List[PlayerSnap], myId: String, delta_time: Double): Unit = {
        canvas.width = canvas.clientWidth
        canvas.height = canvas.clientHeight

        val me = players.find(_.id == myId)
        val cellPx =
            (Constants.BLOCK_SIZE * Constants.SEGMENT_SIZE) / 2

        val visibleCols = 50.0

        val scale =
            canvas.width.toDouble /
            (visibleCols * cellPx)

        val visibleRows =
            canvas.height.toDouble /
            (cellPx * scale)

        val worldWidth =
            map.head.length * cellPx

        val worldHeight =
            map.length * cellPx

        val viewWidth =
            canvas.width / scale

        val viewHeight =
            canvas.height / scale

        val rawCameraX =
            me.map(_.x - (viewWidth / 2).toFloat)
                .getOrElse(0f)

        val rawCameraY =
            me.map(_.y - (viewHeight / 2).toFloat)
                .getOrElse(0f)

        val cameraX =
            rawCameraX
                .max(0f)
                .min((worldWidth - viewWidth).toFloat)

        val cameraY =
            rawCameraY
                .max(0f)
                .min((worldHeight - viewHeight).toFloat)

        /*val cameraX = me.map(_.x - 100f).getOrElse(0f)
        val cameraY = me.map(_.y - 75f).getOrElse(0f)

        val totalCols = map.head.length
        val totalRows = map.length

        val tileSizeX = canvas.width / totalCols
        val tileSizeY = canvas.height / totalRows

        val tileSize = math.min(tileSizeX, tileSizeY)
        
        val cellPx = (Constants.BLOCK_SIZE * Constants.SEGMENT_SIZE) / 2

        val scale = tileSize.toDouble / cellPx*/

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
                    cx * cellPx - cameraX,
                    ry * cellPx - cameraY,
                    cellPx,
                    cellPx
                )
            }
        }

        players.foreach { p =>
            ctx.fillStyle = "#1534e0"
            ctx.fillRect(
                p.x - cameraX,
                p.y - cameraY,
                9,
                9
            )
        }
        
        ctx.restore()
    }