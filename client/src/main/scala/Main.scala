import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.document
import upickle.default.*
import cats.effect.*
import cats.effect.unsafe.implicits.global

sealed trait ClientPhase
case object InLobby extends ClientPhase
case class InGame(map: Vector[Vector[String]], myId: String) extends ClientPhase

private case class ClientState(
    phase: ClientPhase = InLobby,
    players: List[PlayerMemento] = List.empty
)

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    IO {
      Option(document.getElementById("join-btn")).foreach {
        _.asInstanceOf[html.Button].onclick =
          _ => connect().unsafeRunAndForget()
      }
    }

  def connect(): IO[Unit] =
    for {
      name <- IO(
        document
          .getElementById("input-name")
          .asInstanceOf[html.Input]
          .value
          .trim
      )
      ip <- IO(
        document.getElementById("input-ip").asInstanceOf[html.Input].value.trim
      )
      _ <-
        if (name.isEmpty || ip.isEmpty)
          IO(showLoginError("Completa todos los campos"))
        else
          for {
            stateRef <- Ref[IO].of(ClientState())
            ws <- IO(new dom.WebSocket(s"ws://$ip:9000/lobby"))
            _ <- IO(InputHandler.init(msg => ws.send(write[ClientMsg](msg))))
            _ <- IO(ws.onopen = _ => {
              hideLogin()
              ws.send(write[ClientMsg](JoinLobby(name)))
            })
            _ <- IO(ws.onmessage =
              e =>
                dispatch(ws, stateRef, read[ServerMsg](e.data.toString))
                  .unsafeRunAndForget()
            )
            _ <- loop(ws, stateRef, 0, 0, 0f)
          } yield ()
    } yield ()

  def loop(
      ws: dom.WebSocket,
      stateRef: Ref[IO, ClientState],
      current: Double,
      last: Double,
      animTime: Float
  ): IO[Unit] =
    for {
      _ <- IO(println("LOOP TICK"))
      dt <- IO(((current - last) / 1000.0).toFloat)
      newAnimTime = animTime + dt
      state <- stateRef.get
      _ <- state.phase match {
        case InGame(map, myId) =>
          IO(
            Drawer.render(
              map,
              state.players,
              myId,
              (current - last) / 1000.0,
              newAnimTime
            )
          )
        case InLobby => IO.unit
      }
      _ <- IO(dom.window.requestAnimationFrame { t =>
        loop(ws, stateRef, t, current, newAnimTime).unsafeRunAndForget()
      })
    } yield ()

  def dispatch(
      ws: dom.WebSocket,
      stateRef: Ref[IO, ClientState],
      msg: ServerMsg
  ): IO[Unit] =
    msg match {
      case LobbyUpdate(ps) =>
        stateRef.update(_.copy(phase = InLobby)) *>
          IO { showLobby(); showCanvas(false); renderLobby(ws, ps) }

      case GameStarted(map, myId) =>
        stateRef.update(_.copy(phase = InGame(map, myId))) *>
          IO { hideLobby(); showCanvas(true) }

      case GameTick(ps) =>
        stateRef.update(_.copy(players = ps))

      case GameEnded() =>
        stateRef.set(ClientState()) *> IO(showCanvas(false))
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
            val placeholder =
              document.createElement("span").asInstanceOf[html.Element]
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
      _.asInstanceOf[html.Button].onclick =
        _ => ws.send(write[ClientMsg](SetReady(true)))
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
