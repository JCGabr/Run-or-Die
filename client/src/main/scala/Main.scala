import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document
import upickle.default.*

sealed trait ClientPhase
case object InLobby extends ClientPhase
case class InGame(map: Vector[Vector[String]], myId: String) extends ClientPhase

object Main {

    private var current_phase: ClientPhase = InLobby
    private var current_players: List[PlayerSnap] = List.empty

    def main(args: Array[String]): Unit = {
        InputHandler.init()
        Option(document.getElementById("join-btn")).foreach {
            _.asInstanceOf[html.Button].onclick = _ => connect()
        }
    }

    def connect(): Unit = {
        val name = document.getElementById("input-name").asInstanceOf[html.Input].value.trim
        val ip = document.getElementById("input-ip").asInstanceOf[html.Input].value.trim

        if (name.isEmpty || ip.isEmpty) {
            showLoginError("Completa todos los campos")
            return
        }

        val ws = new dom.WebSocket(s"ws://$ip:9000/lobby")

        ws.onopen = _ => {
            hideLogin()
            ws.send(write[ClientMsg](JoinLobby(name)))
        }

        ws.onmessage = e => dispatch(ws, read[ServerMsg](e.data.toString))

        loop(ws, 0, 0)
    }

    def loop(ws: dom.WebSocket, current: Double, last: Double): Unit = {
        current_phase match {
            case InGame(map, myId) =>
                val dt = (current - last) / 1000.0
                Drawer.render(map, current_players, myId, dt)
                ws.send(write[ClientMsg](SendInput(InputHandler.getInput())))
            case InLobby => ()
        }
        dom.window.requestAnimationFrame { t =>
            loop(ws, t, current)
        }
    }

    def dispatch(ws: dom.WebSocket, msg: ServerMsg): Unit = {
        msg match {
            case LobbyUpdate(ps) =>
                current_phase = InLobby
                showLobby()
                showCanvas(false)
                renderLobby(ws, ps)

            case GameStarted(map, myId) =>
                current_phase = InGame(map, myId)
                hideLobby()
                showCanvas(true)

            case GameTick(ps) =>
                current_players = ps

            case GameEnded() =>
                current_phase = InLobby
                current_players = List.empty
                showCanvas(false)
        }
    }

    def renderLobby(ws: dom.WebSocket, ps: List[LobbyPlayer]): Unit = {
        Option(document.getElementById("player-list")).foreach { el =>
            el.innerHTML = ps.map(p =>
                s"<div>${p.name} | ${p.char.getOrElse("-")} | ${if (p.ready) "Ready" else "Pendiente"}</div>"
            ).mkString
        }

        Option(document.getElementById("char-buttons")).foreach { container =>
            if (container.innerHTML.trim.isEmpty) {
                container.innerHTML = Constants.CHARACTERS.keys.map(c =>
                    s"""<button id="char-$c">$c</button>"""
                ).mkString
                Constants.CHARACTERS.keys.foreach { c =>
                    Option(document.getElementById(s"char-$c")).foreach {
                        _.asInstanceOf[html.Button].onclick = _ =>
                            ws.send(write[ClientMsg](SelectCharacter(c)))
                    }
                }
            }
        }

        Option(document.getElementById("ready-btn")).foreach {
            _.asInstanceOf[html.Button].onclick = _ =>
                ws.send(write[ClientMsg](SetReady(true)))
        }
    }


    def hideLogin(): Unit =
        Option(document.getElementById("login-overlay"))
            .map(_.asInstanceOf[html.Element])
            .foreach(_.style.display = "none")

    def showLoginError(msg: String): Unit =
        Option(document.getElementById("login-error"))
            .foreach(_.textContent = msg)

    def hideLobby(): Unit =
        Option(document.getElementById("lobby-overlay"))
            .map(_.asInstanceOf[html.Element])
            .foreach(_.style.display = "none")

    def showLobby(): Unit =
        Option(document.getElementById("lobby-overlay"))
            .map(_.asInstanceOf[html.Element])
            .foreach(_.style.display = "flex")

    def showCanvas(visible: Boolean): Unit =
        Option(document.getElementById("window-game"))
            .map(_.asInstanceOf[html.Canvas])
            .foreach(_.style.display = if (visible) "block" else "none")
}