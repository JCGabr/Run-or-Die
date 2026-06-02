import org.scalajs.dom
import org.scalajs.dom.document

object Main:
    def main(args: Array[String]): Unit =
        document.addEventListener("DOMContentLoaded", {_ => 
            val p = document.createElement("p")
            p.textContent = "Hello World!"
            document.body.appendChild(p)
        })