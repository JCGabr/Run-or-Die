import cats.effect.*
import cats.effect.std.Queue
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import fs2.*
import upickle.default.*
import java.util.UUID
import scala.concurrent.duration.*

case class Client(
    id: String,
    name: String,
    character: Option[String],
    ready: Boolean
)

case class InGamePlayer(
    id: String,
    player: Player,
    lastInput: InputState = InputState.empty
)

sealed trait Phase

case class Lobby(
    clients: Map[String, Client]
) extends Phase

case class InGame(
    clients: Map[String, Client],
    map: Vector[Vector[String]],
    players: Map[String, InGamePlayer],
    lastTick: Long,
    initialPlayerCount: Int
) extends Phase

sealed trait Event
case class Connected(id: String, send: String => IO[Unit]) extends Event
case class Disconnected(id: String) extends Event
case class Received(id: String, msg: ClientMsg) extends Event
case class InputReceived(id: String, input: InputMsg) extends Event
case object GameOver extends Event

object NetworkState {
  def broadcast(
      clients: Map[String, Client],
      senders: Map[String, String => IO[Unit]],
      msg: ServerMsg
  ): IO[Unit] =
    clients.keys.toList.parTraverse_ { id =>
      senders.get(id).fold(IO.unit)(_(write[ServerMsg](msg)))
    }

  def lobbyMsg(clients: Map[String, Client]): LobbyUpdate =
    LobbyUpdate(
      clients.values
        .map(c => LobbyPlayer(c.id, c.name, c.character, c.ready))
        .toList
    )

  def expand(mat: Vector[Vector[Segment]]): Vector[Vector[String]] =
    mat.flatMap { row =>
      val patterns = row.map(_.pattern)
      (0 until 2).map(y => patterns.flatMap(_(y)))
    }
}

object Main extends IOApp.Simple {

  val TICK_MS: Long = 8L

  def run: IO[Unit] =
    for {
      events <- Queue.unbounded[IO, Event]

      server =
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"9000")
          .withHttpWebSocketApp(routes(_, events).orNotFound)
          .build
          .useForever

      loop = stateMachine(events, senders = Map.empty, phase = Lobby(Map.empty))

      _ <- server.both(loop).void
    } yield ()

  def routes(
      wsb: WebSocketBuilder2[IO],
      events: Queue[IO, Event]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root / "lobby" =>
      Queue.unbounded[IO, String].flatMap { outQueue =>
        val id = UUID.randomUUID().toString

        val toClient: Stream[IO, WebSocketFrame] =
          Stream.eval(
            events.offer(Connected(id, msg => outQueue.offer(msg)))
          ) >>
            Stream.fromQueueUnterminated(outQueue).map(WebSocketFrame.Text(_))

        val fromClient: Pipe[IO, WebSocketFrame, Unit] =
          _.evalMap {
            case WebSocketFrame.Text(text, _) =>
              read[ClientMsg](text) match {
                // Los inputs ya no van a un Ref de agente; son eventos normales
                case SendInput(input) => events.offer(InputReceived(id, input))
                case other => events.offer(Received(id, other))
              }
            case WebSocketFrame.Close(_) => events.offer(Disconnected(id))
            case _ => IO.unit
          }

        wsb.build(toClient, fromClient)
      }
    }

  def stateMachine(
      events: Queue[IO, Event],
      senders: Map[String, String => IO[Unit]],
      phase: Phase
  ): IO[Unit] =
    phase match {
      case inGame: InGame =>
        IO.race(
          events.take,
          IO.sleep(TICK_MS.milliseconds)
        ).flatMap {
          case Left(event) => handleEvent(events, senders, inGame, event)
          case Right(_) => performTick(events, senders, inGame)
        }

      case lobby: Lobby =>
        events.take.flatMap(event => handleEvent(events, senders, lobby, event))
    }

  def handleEvent(
      events: Queue[IO, Event],
      senders: Map[String, String => IO[Unit]],
      phase: Phase,
      event: Event
  ): IO[Unit] = (phase, event) match {

    case (Lobby(clients), Connected(id, send)) =>
      val newClients = clients + (id -> Client(id, "?", None, ready = false))
      val newSenders = senders + (id -> send)
      NetworkState.broadcast(
        newClients,
        newSenders,
        NetworkState.lobbyMsg(newClients)
      ) >>
        stateMachine(events, newSenders, Lobby(newClients))

    case (Lobby(clients), Disconnected(id)) =>
      val newClients = clients - id
      val newSenders = senders - id
      NetworkState.broadcast(
        newClients,
        newSenders,
        NetworkState.lobbyMsg(newClients)
      ) >>
        stateMachine(events, newSenders, Lobby(newClients))

    case (Lobby(clients), Received(id, JoinLobby(name))) =>
      val newClients = clients.updated(id, clients(id).copy(name = name))
      NetworkState.broadcast(
        newClients,
        senders,
        NetworkState.lobbyMsg(newClients)
      ) >>
        stateMachine(events, senders, Lobby(newClients))

    case (Lobby(clients), Received(id, SelectCharacter(character))) =>
      val newClients =
        clients.updated(id, clients(id).copy(character = Some(character)))
      NetworkState.broadcast(
        newClients,
        senders,
        NetworkState.lobbyMsg(newClients)
      ) >>
        stateMachine(events, senders, Lobby(newClients))

    case (Lobby(clients), Received(id, SetReady(ready))) =>
      val newClients = clients.updated(id, clients(id).copy(ready = ready))
      val allReady = newClients.nonEmpty && newClients.values.forall(c =>
        c.ready && c.character.isDefined
      )
      NetworkState.broadcast(
        newClients,
        senders,
        NetworkState.lobbyMsg(newClients)
      ) >>
        (if (allReady) startGame(events, senders, newClients)
         else stateMachine(events, senders, Lobby(newClients)))

    case (
          InGame(clients, map, players, lastTick, initCount),
          InputReceived(id, msg)
        ) =>
      val newPlayers = players.updatedWith(id)(
        _.map(igp => igp.copy(lastInput = applyInput(igp.lastInput, msg)))
      )
      stateMachine(
        events,
        senders,
        InGame(clients, map, newPlayers, lastTick, initCount)
      )

    case (InGame(clients, map, players, _, initCount), Disconnected(id)) =>
      stateMachine(
        events,
        senders - id,
        InGame(
          clients - id,
          map,
          players - id,
          System.currentTimeMillis(),
          initCount
        )
      )

    case (InGame(clients, _, _, _, _), GameOver) =>
      endGame(events, senders, clients)

    case _ =>
      stateMachine(events, senders, phase)
  }

  def applyInput(current: InputState, input: InputMsg): InputState =
    input match {
      case PressLeft => current.copy(moveLeft = true)
      case ReleaseLeft => current.copy(moveLeft = false)
      case PressRight => current.copy(moveRight = true)
      case ReleaseRight => current.copy(moveRight = false)
      case PressJump => current.copy(jump = true)
      case ReleaseJump => current.copy(jump = false)
    }

  def performTick(
      events: Queue[IO, Event],
      senders: Map[String, String => IO[Unit]],
      game: InGame
  ): IO[Unit] = {
    val now = System.currentTimeMillis()
    val dt = ((now - game.lastTick) / 1000f).min(0.1f)

    val moved: Map[String, InGamePlayer] =
      game.players.map { case (id, igp) =>
        id -> igp.copy(player = igp.player.update(igp.lastInput, dt, game.map))
      }

    val claimedIds: Set[String] =
      moved.collect {
        case (id, igp) if igp.player.claimed_checkpoint => id
      }.toSet

    val penalized: Map[String, InGamePlayer] =
      if (claimedIds.isEmpty) moved
      else
        moved.map { case (id, igp) =>
          val updated =
            if (claimedIds(id))
              igp.copy(player = igp.player.copy(claimed_checkpoint = false))
            else if (!igp.player.is_alive)
              igp
            else {
              val shouldPenalize = claimedIds.exists { claimerId =>
                val claimer = moved(claimerId).player
                igp.player.coords.x < claimer.last_checkpoint.x + Constants.BLOCK_SIZE
              }
              if (shouldPenalize)
                igp.copy(player =
                  igp.player.copy(
                    max_time = (igp.player.max_time - 1f).max(0f),
                    current_time = (igp.player.current_time - 1f).max(0f)
                  )
                )
              else igp
            }
          id -> updated
        }

    val memento: List[PlayerMemento] =
      penalized.values.map { igp =>
        PlayerMemento(
          igp.id,
          igp.player.coords.x,
          igp.player.coords.y,
          igp.player.is_alive,
          igp.player.stats.size.x,
          igp.player.stats.size.y,
          igp.player.current_time,
          igp.player.max_time,
          game.clients(igp.id).character.get,
          igp.player.velocity.x,
          igp.player.velocity.y
        )
      }.toList

    val alivePlayers = penalized.values.count(_.player.is_alive)
    val shouldEnd =
      if (game.initialPlayerCount == 1) alivePlayers == 0
      else alivePlayers <= 1

    val newGame = game.copy(players = penalized, lastTick = now)

    NetworkState.broadcast(game.clients, senders, GameTick(memento)) >>
      (if (shouldEnd)
         events.offer(GameOver) >> stateMachine(events, senders, newGame)
       else stateMachine(events, senders, newGame))
  }

  def startGame(
      events: Queue[IO, Event],
      senders: Map[String, String => IO[Unit]],
      clients: Map[String, Client]
  ): IO[Unit] =
    for {
      rawMap <- IO(new MapGame(500, 0, 30, 0).generate())
      map = NetworkState.expand(rawMap)

      players = clients.values.zipWithIndex.map { case (client, i) =>
        val stats = Constants.CHARACTERS(client.character.get)
        val player = Player(
          Vector2(i * 40f, 0f),
          Vector2(0f, 0f),
          30f,
          30f,
          stats,
          false,
          true
        )
        client.id -> InGamePlayer(client.id, player)
      }.toMap

      _ <- clients.values.toList.traverse_ { client =>
        senders
          .get(client.id)
          .fold(IO.unit)(
            _(write[ServerMsg](GameStarted(map, client.id)))
          )
      }

      _ <- stateMachine(
        events,
        senders,
        InGame(clients, map, players, System.currentTimeMillis(), players.size)
      )
    } yield ()

  def endGame(
      events: Queue[IO, Event],
      senders: Map[String, String => IO[Unit]],
      clients: Map[String, Client]
  ): IO[Unit] = {
    val resetClients = clients.map { case (id, c) =>
      id -> c.copy(ready = false, character = None)
    }
    NetworkState.broadcast(resetClients, senders, GameEnded()) >>
      NetworkState.broadcast(
        resetClients,
        senders,
        NetworkState.lobbyMsg(resetClients)
      ) >>
      stateMachine(events, senders, Lobby(resetClients))
  }
}
