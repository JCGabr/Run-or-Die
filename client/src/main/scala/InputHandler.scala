import org.scalajs.dom

object InputHandler:

  def init(send: ClientMsg => Unit): Unit =
    dom.document.onkeydown = e => {
      e.key match {
        case "a" => send(SendInput(PressLeft))
        case "d" => send(SendInput(PressRight))
        case "w" => send(SendInput(PressJump))
        case _ =>
      }
    }

    dom.document.onkeyup = e => {
      e.key match {
        case "a" => send(SendInput(ReleaseLeft))
        case "d" => send(SendInput(ReleaseRight))
        case "w" => send(SendInput(ReleaseJump))
        case _ =>
      }
    }
