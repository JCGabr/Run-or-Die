import org.scalajs.dom

object Main:

    def gameLoop(loop_info: GameLoopInfo): Unit ={
        // Genera independencia de los fps del navegador    
        val delta_time = (loop_info.current_time - loop_info.last_time) / 1000.0
        
        Drawer.render(delta_time)
        
        Logic.update(InputHandler.getInput(), delta_time)

        //llamada cuando el frame se encuentre disponible
        dom.window.requestAnimationFrame( dt => { gameLoop(GameLoopInfo(dt, loop_info.current_time)) })
    }

    def main(args: Array[String]): Unit = {
        val initialPlayer = Player(
            coords = Vector2(50f, 100f),
            velocity = Vector2(0f, 0f),
            current_time = 300f,
            max_time = 30f,
            stats = Constants.CHARACTERS("Gnome"),
            is_grounded = false,
            is_alive = true
        )
        Logic.init(initialPlayer, Drawer.cells)
        
        InputHandler.init()
        dom.window.requestAnimationFrame(startTime =>
            dom.window.requestAnimationFrame(dt =>
                gameLoop(GameLoopInfo(dt, startTime))
            )
        )
    }