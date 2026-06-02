import org.scalajs.dom

object Main:

    //renderizado


    //logica de juego
    def update(delta_time: Double): Unit = {

    }

    def gameLoop(loop_info: GameLoopInfo): Unit ={
        // Genera independencia de los fps del navegador    
        val delta_time = (loop_info.current_time - loop_info.last_time) / 1000.0
        
        Drawer.render(delta_time)
        
        update(delta_time)

        //llamada cuando el frame se encuentre disponible
        dom.window.requestAnimationFrame( dt => { gameLoop(GameLoopInfo(dt, loop_info.current_time)) })
    }

    def main(args: Array[String]): Unit = {
        dom.window.requestAnimationFrame(startTime =>
            dom.window.requestAnimationFrame(dt =>
                gameLoop(GameLoopInfo(dt, startTime))
            )
        )
    }