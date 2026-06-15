import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document
import scala.util.Random

object Drawer:
    case class Cloud(
        x: Float,
        y: Float,
        size: Float
    )

    val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]        
    val bg = document.getElementById("bg").asInstanceOf[html.Image]

    def generateClouds(
        worldWidth: Int,
        worldHeight: Int
    ): List[Cloud] =
        val random = Random(42)

        (0 until 120).map { _ =>
            Cloud(
                random.between(0f, worldWidth.toFloat),
                random.between(0f, worldHeight.toFloat * 0.15f),
                random.between(20f, 60f)
            )
        }.toList

    def drawCloud(
        cloud: Cloud,
        cameraX: Float,
        cameraY: Float
    ): Unit =
        val x = cloud.x - cameraX * 0.2f
        val y = cloud.y - cameraY * 0.05f

        ctx.fillStyle = "rgba(255,255,255,0.3)"
        ctx.beginPath()
        // Inferior izquierda
        ctx.moveTo(
            x + cloud.size * 0.45f,
            y
        )

        ctx.arc(
            x,
            y,
            cloud.size * 0.45f,
            0,
            Math.PI * 2
        )

        // Inferior centro
        ctx.moveTo(
            x + cloud.size,
            y
        )

        ctx.arc(
            x + cloud.size * 0.45f,
            y,
            cloud.size * 0.55f,
            0,
            Math.PI * 2
        )

        // Inferior derecha
        ctx.moveTo(
            x + cloud.size * 1.35f,
            y
        )

        ctx.arc(
            x + cloud.size * 0.9f,
            y,
            cloud.size * 0.45f,
            0,
            Math.PI * 2
        )

        // Superior izquierda
        ctx.moveTo(
            x + cloud.size * 0.65f,
            y - cloud.size * 0.3f
        )

        ctx.arc(
            x + cloud.size * 0.25f,
            y - cloud.size * 0.3f,
            cloud.size * 0.4f,
            0,
            Math.PI * 2
        )

        // Superior derecha
        ctx.moveTo(
            x + cloud.size * 1.1f,
            y - cloud.size * 0.35f
        )

        ctx.arc(
            x + cloud.size * 0.65f,
            y - cloud.size * 0.35f,
            cloud.size * 0.45f,
            0,
            Math.PI * 2
        )
        ctx.fill()

    def render(map: Vector[Vector[String]], players: List[PlayerSnap], myId: String, delta_time: Double): Unit = {
        canvas.width = canvas.clientWidth
        canvas.height = canvas.clientHeight

        val me = players.find(_.id == myId)
        val cellPx =
            (Constants.BLOCK_SIZE * Constants.SEGMENT_SIZE) / 2

        val visibleCols = 50.0

        val scale = Math.floor(canvas.width.toDouble / (visibleCols * cellPx)).max(1.0)

        val worldWidth = map.head.length * cellPx

        val worldHeight = map.length * cellPx

        val viewWidth = canvas.width / scale

        val viewHeight = canvas.height / scale

        val clouds = generateClouds(worldWidth, worldHeight)

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
        val drawCameraX = Math.floor(cameraX).toFloat
        val drawCameraY = Math.floor(cameraY).toFloat
        ctx.save()
        ctx.scale(scale, scale)
        
        val gradient = ctx.createLinearGradient(
            0,
            -drawCameraY,
            0,
            worldHeight - drawCameraY
        )

        gradient.addColorStop(0.0, "#87CEEB")  // celeste
        gradient.addColorStop(0.4, "#FFB6C1")  // rosa suave
        gradient.addColorStop(0.7, "#9370DB")  // violeta
        gradient.addColorStop(1.0, "#0A0A28")  // noche

        ctx.fillStyle = gradient
        ctx.fillRect(
            -drawCameraX,
            -drawCameraY,
            worldWidth,
            worldHeight
        )

        clouds.foreach { cloud =>
            drawCloud(
                cloud,
                drawCameraX,
                drawCameraY
            )
        }

        map.zipWithIndex.foreach { case (row, ry) =>
            row.zipWithIndex.foreach { case (cell, cx) =>
                ctx.fillStyle = cell match {
                    case "G" => "#8B4513"
                    case "P" => "#228B22"
                    case "S" => "#FF0000"
                    case "C" => "#FFFF00"
                    case "." => "rgba(0,0,0,0)"
                }
                ctx.fillRect(
                    cx * cellPx - drawCameraX,
                    ry * cellPx - drawCameraY,
                    cellPx,
                    cellPx
                )
            }
        }

        players.foreach { p =>
            ctx.fillStyle = "#1534e0"
            ctx.fillRect(
                p.x - drawCameraX,
                p.y - drawCameraY,
                9,
                9
            )
        }
        
        ctx.restore()
    }