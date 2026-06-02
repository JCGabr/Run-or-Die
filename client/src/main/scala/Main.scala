import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document

import scala.annotation.tailrec

case class LoopInfo(
    current_time: Double,
    last_time: Double
)

object Main:

    val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]        

    //renderizado
    def render(delta_time: Double): Unit = {

    }

    //logica de juego
    def update(delta_time: Double): Unit = {

    }

    def gameLoop(loop_info: LoopInfo): Unit ={
        // Genera independencia de los fps del navegador    
        val delta_time = (loop_info.current_time - loop_info.last_time) / 1000.0
        
        render(delta_time)
        
        update(delta_time)

        //llamada cuando el frame se encuentre disponible
        dom.window.requestAnimationFrame( dt => { gameLoop(LoopInfo(dt, loop_info.current_time)) })
    }

    def main(args: Array[String]): Unit = {
        dom.window.requestAnimationFrame(startTime =>
            dom.window.requestAnimationFrame(dt =>
                gameLoop(LoopInfo(dt, startTime))
            )
        )
    }