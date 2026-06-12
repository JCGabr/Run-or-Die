import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document

object Drawer:

    val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]        
    val mapgame: MapGame = new MapGame(20, 0, 10, 0)
    val matriz = mapgame.generate()
    val cells = expand(matriz)

    def expand(matriz: Vector[Vector[Segment]]): Vector[Vector[String]] =
        matriz.flatMap { row =>
            val patterns = row.map(_.pattern)
            (0 until 2).map { py =>
                patterns.flatMap(pattern => pattern(py))
            }
        }
    
    def render(delta_time: Double): Unit = {
        // Ajustar el tamaño interno del canvas al tamaño visual
        canvas.width = canvas.clientWidth
        canvas.height = canvas.clientHeight

        // Calcular tileSize para que llene exactamente el canvas
        /*val tileSizeX = canvas.width / 20
        val tileSizeY = canvas.height / (10 + 1)
        val tileSize = math.min(tileSizeX, tileSizeY)
        */
        val totalCols = matriz.head.length * 2
        val totalRows = matriz.length * 2

        val tileSizeX = canvas.width / totalCols
        val tileSizeY = canvas.height / totalRows

        val tileSize = math.min(tileSizeX, tileSizeY)
        
        val cellPx = (Constants.BLOCK_SIZE * Constants.SEGMENT_SIZE) / 2

        val scale = tileSize.toDouble / cellPx

        ctx.save()
        ctx.scale(scale, scale) 

        matriz.zipWithIndex.foreach { case (row, y) =>
            row.zipWithIndex.foreach { case (segment, x) =>

                segment.pattern.zipWithIndex.foreach {
                    case (patternRow, py) =>

                        patternRow.zipWithIndex.foreach {
                            case (cell, px) =>

                                ctx.fillStyle = cell match {
                                    case "G" => "#8B4513" // ground
                                    case "P" => "#228B22" // platform
                                    case "S" => "#FF0000" // spike
                                    case "C" => "#FFFF00" // checkpoint
                                    case _   => "#87CEEB" // vacío
                                }

                                ctx.fillRect(
                                    (x * 2 + px) * cellPx,
                                    (y * 2 + py) * cellPx,
                                    cellPx,
                                    cellPx
                                )
                        }
                }
            }
        }
        ctx.fillStyle = "#1534e0"
        ctx.fillRect(
            Logic.player.coords.x,
            Logic.player.coords.y,
            Logic.player.stats.size.x,
            Logic.player.stats.size.y
        )
        
        ctx.restore()
        /*
        matriz.zipWithIndex.foreach { case (row, y) =>
            row.zipWithIndex.foreach { case (segment, x) =>
                ctx.fillStyle = segment.familyId match {
                    case "ground"   => "#8B4513"
                    case "platform" => "#228B22"
                    case _          => "#87CEEB"
                }
                //ctx.fillRect(x * tileSize, y * tileSize, tileSize, tileSize)
            }
        }*/
    }