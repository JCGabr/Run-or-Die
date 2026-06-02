import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document

object Main:
    def main(args: Array[String]): Unit =
        document.addEventListener("DOMContentLoaded", {_ => 
            val canvas = document.getElementById("window-game").asInstanceOf[html.Canvas]
            val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
        })