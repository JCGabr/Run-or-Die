import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document
import upickle.default.*

sealed trait ClientPhase
case object InLobby extends ClientPhase
case class InGame(map: Vector[Vector[String]], myId: String) extends ClientPhase

object Main {

    def main(args: Array[String]): Unit = {
        val name = dom.window.prompt("Tu nombre:").nn
        val ip = dom.window.prompt("Ip a conectar: ").nn
        val lobby = new dom.WebSocket("ws://"+ ip +":9000/lobby")

        lobby.onopen = _ => lobby.send(write[ClientMsg](JoinLobby(name)))
        lobby.onmessage = e => dispatch(lobby, read[ServerMsg](e.data.toString), InLobby, List.empty)

        InputHandler.init()
        loop(lobby, InLobby, List.empty, 0, 0)
    }

    def loop(lobby: dom.WebSocket, phase: ClientPhase, players: List[PlayerSnap], current: Double, last: Double): Unit = {
        phase match {
            case InGame(map, myId) =>
                val dt = (current - last) / 1000.0
                Drawer.render(map, players, myId, dt)
                lobby.send(write[ClientMsg](SendInput(InputHandler.getInput())))
            case InLobby => ()
        }
        dom.window.requestAnimationFrame { t =>
            loop(lobby, current_phase, current_players, t, current)
        }
    }

    private var current_phase: ClientPhase = InLobby
    private var current_players: List[PlayerSnap] = List.empty

    def dispatch(lobby: dom.WebSocket,msg: ServerMsg, phase: ClientPhase, players: List[PlayerSnap]): Unit = {
        msg match {
            case LobbyUpdate(ps) =>
                current_phase = InLobby
                renderLobby(lobby, ps)

            case GameStarted(map, myId) =>
                //val myId = "local"
                current_phase = InGame(map, myId)
                hideLobby()

            case GameTick(ps) =>
                current_players = ps

            case GameEnded() =>
                current_phase   = InLobby
                current_players = List.empty
        }
    }

    def renderLobby(lobby: dom.WebSocket, ps: List[LobbyPlayer]): Unit = {
        val element = getOrCreate("lobby-overlay",
            "position:fixed;top:0;left:0;width:100%;height:100%;" +
            "background:#111;color:#fff;display:flex;flex-direction:column;" +
            "align-items:center;justify-content:center;gap:12px;z-index:10")

        element.innerHTML =
            "<h2>Lobby</h2>" +
            ps.map(p => s"<div>${p.name} | ${p.char.getOrElse("-")} | ${if(p.ready) "Ready" else "Pendiente"}</div>").mkString +
            "<hr/>" +
            Constants.CHARACTERS.keys.map(client =>
            s"""<button id="char-$client" style="margin:4px;padding:8px 16px">$client</button>"""
            ).mkString(" ") +
            """<button id="ready-btn" style="margin-top:8px;padding:12px 32px">¡Listo!</button>"""

        Constants.CHARACTERS.keys.foreach { 
            client =>
                Option(document.getElementById(s"char-$client")).foreach {
                    _.asInstanceOf[html.Button].onclick = _ => 
                        lobby.send(write[ClientMsg](SelectCharacter(client)))
            }
        }

        Option(document.getElementById("ready-btn")).foreach {
            _.asInstanceOf[html.Button].onclick = _ => 
                lobby.send(write[ClientMsg](SetReady(true)))
        }
    }

    def hideLobby(): Unit ={
        Option(document.getElementById("lobby-overlay")).foreach(_.remove())
    }

    def getOrCreate(id: String, style: String): html.Div ={
        Option(document.getElementById(id))
            .map(_.asInstanceOf[html.Div])
            .getOrElse {
                val d = document.createElement("div").asInstanceOf[html.Div]
                d.id = id
                d.style.cssText = style
                document.body.appendChild(d)
                d
            }
    }
}