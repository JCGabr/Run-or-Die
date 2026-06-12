import org.scalajs.dom

object InputHandler:

    var input: InputState = InputState()

    def init(): Unit =
        dom.document.onkeydown = e => {
            e.key match {
                case "a" => input = input.copy(moveLeft  = true)
                case "d" => input = input.copy(moveRight = true)
                case "w" => input = input.copy(jump      = true)
                case _ =>
            }
        }

        dom.document.onkeyup = e => {
            e.key match {
                case "a" => input = input.copy(moveLeft  = false)
                case "d" => input = input.copy(moveRight = false)
                case "w" => input = input.copy(jump      = false)
                case _ =>
            }
        }

    def getInput(): InputState = input