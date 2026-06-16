import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document
import upickle.default.*

sealed trait ClientPhase
case object InLobby extends ClientPhase
case class InGame(map: Vector[Vector[String]], myId: String) extends ClientPhase

object Main {

    private var current_phase: ClientPhase = InLobby
    private var current_players: List[PlayerMemento] = List.empty

    def main(args: Array[String]): Unit = {
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

        InputHandler.init(msg => ws.send(write[ClientMsg](msg)))

        ws.onopen = _ => {
            hideLogin()
            ws.send(write[ClientMsg](JoinLobby(name)))
        }

        ws.onmessage = e => dispatch(ws, read[ServerMsg](e.data.toString))

        loop(ws, 0, 0, 0f)
    }

    def loop(ws: dom.WebSocket, current: Double, last: Double, animTime: Float): Unit = {
        println("LOOP TICK")
        val dt = ((current - last) / 1000.0).toFloat
        val newAnimTime = animTime + dt
        
        current_phase match {
            case InGame(map, myId) =>
                val dt = (current - last) / 1000.0
                Drawer.render(map, current_players, myId, dt, newAnimTime)
            case InLobby => ()
        }
        dom.window.requestAnimationFrame { t =>
            loop(ws, t, current, newAnimTime)
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

        Option(document.getElementById("player-list")).foreach { element =>
            element.innerHTML = ""
            ps.foreach { player =>
                val card = document.createElement("div").asInstanceOf[html.Element]
                card.className = "player-card" + (if (player.ready) " ready" else "")

                val charArea = document.createElement("div").asInstanceOf[html.Element]
                charArea.className = "char-area"

                player.char match {
                    case Some(client) =>
                        val img = document.createElement("img").asInstanceOf[html.Image]
                        img.src = s"$client.png"
                        img.alt = client
                        charArea.appendChild(img)
                    case None =>
                        val placeholder = document.createElement("span").asInstanceOf[html.Element]
                        placeholder.className = "no-char"
                        placeholder.textContent = "?"
                        charArea.appendChild(placeholder)
                }

                val info = document.createElement("div").asInstanceOf[html.Element]
                info.className = "player-info"

                val nameEl = document.createElement("span").asInstanceOf[html.Element]
                nameEl.className = "player-name"
                nameEl.textContent = player.name

                val charEl = document.createElement("span").asInstanceOf[html.Element]
                charEl.className = "player-char"
                charEl.textContent = player.char.getOrElse("—")

                val statusEl = document.createElement("span").asInstanceOf[html.Element]
                statusEl.className = "player-status"
                statusEl.textContent = if (player.ready) "Listo" else "Pendiente"

                info.appendChild(nameEl)
                info.appendChild(charEl)
                info.appendChild(statusEl)
                card.appendChild(charArea)
                card.appendChild(info)
                element.appendChild(card)
            }
        }

        Option(document.getElementById("char-buttons")).foreach { container =>
            container.innerHTML = ""
            Constants.CHARACTERS.keys.foreach { client =>
                val btn = document.createElement("button").asInstanceOf[html.Button]
                btn.textContent = client
                btn.onclick = _ => ws.send(write[ClientMsg](SelectCharacter(client)))
                container.appendChild(btn)
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