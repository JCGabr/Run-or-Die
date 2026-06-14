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

case class Client(id: String, name: String, character: Option[String], ready: Boolean)
case class InGamePlayer(id: String, player: Player)

sealed trait Phase
case class Lobby(clients: Map[String, Client]) extends Phase
case class InGame(clients: Map[String, Client], players: Map[String, InGamePlayer], map: Vector[Vector[String]], lastTick: Long = System.currentTimeMillis()) extends Phase

sealed trait Event
case class Connected (id: String, send: String => IO[Unit]) extends Event
case class Disconnected(id: String) extends Event
case class Received (id: String, msg: ClientMsg) extends Event
case object Tick extends Event

object AppState {
    def broadcast(clients: Map[String, Client], senders: Map[String, String => IO[Unit]], msg: ServerMsg): IO[Unit] ={
        return clients.keys.toList.traverse_ { 
            id =>
                senders.get(id).fold(IO.unit)(_(write[ServerMsg](msg)))
        }
    }

    def lobbyMsg(clients: Map[String, Client]): LobbyUpdate ={
        return LobbyUpdate(clients.values.map(c => LobbyPlayer(c.id, c.name, c.character, c.ready)).toList)
    }

    def expand(mat: Vector[Vector[Segment]]): Vector[Vector[String]] ={
        return mat.flatMap { 
            row =>
                val pats = row.map(_.pattern)
                (0 until 2).map(py => pats.flatMap(_(py)))
        }
    }
}


object Main extends IOApp.Simple {

    def run: IO[Unit] = {
        Queue.unbounded[IO, Event].flatMap { 
            events =>
                val server =
                    EmberServerBuilder.default[IO]
                        .withHost(ipv4"0.0.0.0")
                        .withPort(port"9000")
                        .withHttpWebSocketApp(routes(_, events).orNotFound)
                        .build
                        .useForever

                val loop = stateMachine(events, senders = Map.empty, phase = Lobby(Map.empty), tickerFiber = None)

                server.both(loop).void
        }
    }

    def routes(wsb: WebSocketBuilder2[IO], events: Queue[IO, Event]): HttpRoutes[IO] ={
        HttpRoutes.of[IO] {
            case GET -> Root / "lobby" =>
            Queue.unbounded[IO, String].flatMap { 
                outQueue =>
                    val id = UUID.randomUUID().toString

                    val toClient: Stream[IO, WebSocketFrame] =
                    Stream.eval(events.offer(Connected(id, msg => outQueue.offer(msg)))) >>
                    Stream.fromQueueUnterminated(outQueue).map(WebSocketFrame.Text(_))

                    val fromClient: Pipe[IO, WebSocketFrame, Unit] =
                    _.evalMap {
                        case WebSocketFrame.Text(text, _) =>
                        events.offer(Received(id, read[ClientMsg](text)))
                        case WebSocketFrame.Close(_) =>
                        events.offer(Disconnected(id))
                        case _ =>
                        IO.unit
                    }

                    wsb.build(toClient, fromClient)
            }
        }
    }

    def stateMachine(events: Queue[IO, Event], senders: Map[String, String => IO[Unit]], phase: Phase, tickerFiber: Option[FiberIO[Unit]]): IO[Unit] ={
        events.take.flatMap { 
            event =>
                (phase, event) match {

                    case (Lobby(clients), Connected(id, send)) =>
                        val newClients = clients + (id -> Client(id, "?", None, ready = false))
                        val newSenders = senders + (id -> send)
                        
                        AppState.broadcast(newClients, newSenders, AppState.lobbyMsg(newClients)) >>
                        stateMachine(events, newSenders, Lobby(newClients), tickerFiber)

                    case (Lobby(clients), Disconnected(id)) =>
                        val newClients = clients - id
                        val newSenders = senders - id
                        
                        AppState.broadcast(newClients, newSenders, AppState.lobbyMsg(newClients)) >>
                        stateMachine(events, newSenders, Lobby(newClients), tickerFiber)

                    case (Lobby(clients), Received(id, JoinLobby(name))) =>
                        val newClients = clients.updated(id, clients(id).copy(name = name))
                        
                        AppState.broadcast(newClients, senders, AppState.lobbyMsg(newClients)) >>
                        stateMachine(events, senders, Lobby(newClients), tickerFiber)

                    case (Lobby(clients), Received(id, SelectCharacter(character))) =>
                        val newClients = clients.updated(id, clients(id).copy(character = Some(character)))
                        
                        AppState.broadcast(newClients, senders, AppState.lobbyMsg(newClients)) >>
                        stateMachine(events, senders, Lobby(newClients), tickerFiber)

                    case (Lobby(clients), Received(id, SetReady(ready))) =>
                        val newClients = clients.updated(id, clients(id).copy(ready = ready))
                        val allReady= newClients.nonEmpty && newClients.values.forall(c => c.ready && c.character.isDefined)
                        
                        AppState.broadcast(newClients, senders, AppState.lobbyMsg(newClients)) >>
                        (
                            if (allReady) 
                            startGame(events, senders, newClients)
                            else
                            stateMachine(events, senders, Lobby(newClients), tickerFiber)
                        )

                    case (InGame(clients, players, map, lastTick), Received(id, SendInput(input))) =>
                        val now = System.currentTimeMillis()
                        val dt = ((now - lastTick) / 1000f).min(0.1f)
                        val newPlayers = players.get(id).fold(players) { 
                            igp =>
                                val updated = igp.player.update(input, dt, map)
                                players.updated(id, igp.copy(player = updated))
                        }
                        stateMachine(events, senders, InGame(clients, newPlayers, map, now), tickerFiber)

                    case (InGame(clients, players, map, lastTick), Tick) =>
                        val snaps = players.values.map(
                            p =>
                                PlayerSnap(p.id, p.player.coords.x, p.player.coords.y, p.player.is_alive, p.player.stats.size.x, p.player.stats.size.y)
                        ).toList
                        val allDead = players.values.forall(!_.player.is_alive)

                        AppState.broadcast(clients, senders, GameTick(snaps)) >>
                        (
                            if (allDead)
                                tickerFiber.fold(IO.unit)(_.cancel) >> endGame(events, senders, clients)
                            else
                                stateMachine(events, senders, InGame(clients, players, map, lastTick), tickerFiber)
                        )

                    case (InGame(clients, players, map, _), Disconnected(id)) =>
                        val newClients = clients - id
                        val newPlayers = players - id
                        val newSenders = senders - id
                        stateMachine(events, newSenders, InGame(newClients, newPlayers, map), tickerFiber)

                    case _ =>
                        stateMachine(events, senders, phase, tickerFiber)
                    }
        }
    }


    def startGame(events:  Queue[IO, Event], senders: Map[String, String => IO[Unit]], clients: Map[String, Client]): IO[Unit] =
        IO {
            val rawMap = new MapGame(20, 0, 10, 0).generate()
            val map = AppState.expand(rawMap)

            val players = clients.values.zipWithIndex.map { 
                case (c, i) =>
                    val stats = Constants.CHARACTERS(c.character.get)
                    val p = Player(Vector2(i * 40f, 0f), Vector2(0f, 0f), 300f, 300f, stats, false, true)
                    c.id -> InGamePlayer(c.id, p)
            }.toMap

            (map, players)
        }.flatMap { 
            case (map, players) =>
                clients.values.toList.traverse_ { 
                    c =>
                        val pos = players(c.id).player.coords
                        senders.get(c.id).fold(IO.unit)(_(write[ServerMsg](GameStarted(map, pos.x, pos.y))))
                } >>
                ticker(events).flatMap { fiber =>
                    stateMachine(events, senders, InGame(clients, players, map), Some(fiber))
                }
        }

    def ticker(events: Queue[IO, Event]): IO[FiberIO[Unit]] = {
        (IO.sleep(16.milliseconds) >> events.offer(Tick))
            .foreverM
            .as(())
            .start
    }

    def endGame(events: Queue[IO, Event], senders: Map[String, String => IO[Unit]], clients: Map[String, Client]): IO[Unit] ={
        val resetClients = clients.map { 
            case (id, c) => 
                id -> c.copy(ready = false, character = None) 
            }
        
        AppState.broadcast(resetClients, senders, GameEnded()) >>
        AppState.broadcast(resetClients, senders, AppState.lobbyMsg(resetClients)) >>
        stateMachine(events, senders, Lobby(resetClients), None)
    }
}