import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document

object Drawer:

    val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]        

    def render(delta_time: Double): Unit = {


    }