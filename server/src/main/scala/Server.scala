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
case class InGamePlayer(id: String, player: Player, lastInput: InputState = InputState.empty)

sealed trait Phase
case class Lobby(clients: Map[String, Client]) extends Phase
case class InGame(clients: Map[String, Client], players: Map[String, InGamePlayer], map: Vector[Vector[String]], lastTick: Long = System.currentTimeMillis()) extends Phase

sealed trait Event
case class Connected (id: String, send: String => IO[Unit]) extends Event
case class Disconnected(id: String) extends Event
case class Received (id: String, msg: ClientMsg) extends Event
case object Tick extends Event

object NetworkState {
    def broadcast(clients: Map[String, Client], senders: Map[String, String => IO[Unit]], msg: ServerMsg): IO[Unit] ={
        clients.keys.toList.parTraverse_ {
            id =>
                senders.get(id) match {
                    case None => IO.unit
                    case Some(send) => send(write[ServerMsg](msg))
                }
        }
    }

    def lobbyMsg(clients: Map[String, Client]): LobbyUpdate ={
        return LobbyUpdate(
            clients.values.map(
                client => 
                    LobbyPlayer(client.id, client.name, client.character, client.ready)
                ).toList
            )
    }

    def expand(mat: Vector[Vector[Segment]]): Vector[Vector[String]] ={
        return mat.flatMap { 
            row =>
                val patterns = row.map(_.pattern)
                (0 until 2).map(y => patterns.flatMap(_(y)))
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
                        val new_clients = clients + (id -> Client(id, "?", None, ready = false))
                        val new_senders = senders + (id -> send)
                        
                        NetworkState.broadcast(new_clients, new_senders, NetworkState.lobbyMsg(new_clients)) >>
                        stateMachine(events, new_senders, Lobby(new_clients), tickerFiber)

                    case (Lobby(clients), Disconnected(id)) =>
                        val new_clients = clients - id
                        val new_senders = senders - id
                        
                        NetworkState.broadcast(new_clients, new_senders, NetworkState.lobbyMsg(new_clients)) >>
                        stateMachine(events, new_senders, Lobby(new_clients), tickerFiber)

                    case (Lobby(clients), Received(id, JoinLobby(name))) =>
                        val new_clients = clients.updated(id, clients(id).copy(name = name))
                        
                        NetworkState.broadcast(new_clients, senders, NetworkState.lobbyMsg(new_clients)) >>
                        stateMachine(events, senders, Lobby(new_clients), tickerFiber)

                    case (Lobby(clients), Received(id, SelectCharacter(character))) =>
                        val new_clients = clients.updated(id, clients(id).copy(character = Some(character)))
                        
                        NetworkState.broadcast(new_clients, senders, NetworkState.lobbyMsg(new_clients)) >>
                        stateMachine(events, senders, Lobby(new_clients), tickerFiber)

                    case (Lobby(clients), Received(id, SetReady(ready))) =>
                        val new_clients = clients.updated(id, clients(id).copy(ready = ready))
                        val allReady= new_clients.nonEmpty && new_clients.values.forall(client => client.ready && client.character.isDefined)
                        
                        NetworkState.broadcast(new_clients, senders, NetworkState.lobbyMsg(new_clients)) >>
                        (
                            if (allReady) 
                                startGame(events, senders, new_clients)
                            else
                                stateMachine(events, senders, Lobby(new_clients), tickerFiber)
                        )

                    case (InGame(clients, players, map, lastTick), Received(id, SendInput(input))) =>
                        val newPlayers = players.updatedWith(id) {
                            case Some(igp) =>
                                val updatedInput = input match {
                                    case PressLeft    => igp.lastInput.copy(moveLeft  = true)
                                    case ReleaseLeft  => igp.lastInput.copy(moveLeft  = false)
                                    case PressRight   => igp.lastInput.copy(moveRight = true)
                                    case ReleaseRight => igp.lastInput.copy(moveRight = false)
                                    case PressJump    => igp.lastInput.copy(jump      = true)
                                    case ReleaseJump  => igp.lastInput.copy(jump      = false)
                                }
                                Some(igp.copy(lastInput = updatedInput))
                            case None => None
                        }
                        stateMachine(events, senders, InGame(clients, newPlayers, map, lastTick), tickerFiber)

                    case (InGame(clients, players, map, lastTick), Tick) =>
                        val now = System.currentTimeMillis(); 
                        val dt = ((now - lastTick) / 1000f).min(0.1f)

                        val updatedPlayers = players.map { case (id, igp) => id -> igp.copy(player = igp.player.update(igp.lastInput, dt, map)) }

                        val snaps = updatedPlayers.values.map(p => PlayerMemento(p.id, p.player.coords.x, p.player.coords.y, p.player.is_alive, p.player.stats.size.x, p.player.stats.size.y)).toList

                        val allDead = updatedPlayers.values.forall(!_.player.is_alive)

                        NetworkState.broadcast(clients, senders, GameTick(snaps)) >>
                        (
                            if (allDead)
                                tickerFiber.fold(IO.unit)(_.cancel) >> endGame(events, senders, clients)
                            else
                                stateMachine(events, senders, InGame(clients, updatedPlayers, map, now), tickerFiber)
                        )

                    case (InGame(clients, players, map, _), Disconnected(id)) =>
                        val new_clients = clients - id
                        val new_players = players - id
                        val new_senders = senders - id
                        stateMachine(events, new_senders, InGame(new_clients, new_players, map), tickerFiber)

                    case _ =>
                        stateMachine(events, senders, phase, tickerFiber)
                    }
        }
    }

    def startGame(events:  Queue[IO, Event], senders: Map[String, String => IO[Unit]], clients: Map[String, Client]): IO[Unit] =
        IO {
            val rawMap = new MapGame(500, 0, 30, 0).generate()
            val map = NetworkState.expand(rawMap)

            val players = clients.values.zipWithIndex.map { 
                case (client, index) =>
                    val stats = Constants.CHARACTERS(client.character.get)
                    val player = Player(Vector2(index * 40f, 0f), Vector2(0f, 0f), 300f, 300f, stats, false, true)
                    client.id -> InGamePlayer(client.id, player)
            }.toMap

            (map, players)
        }.flatMap { 
            case (map, players) =>
                clients.values.toList.traverse_ { 
                    client =>
                        val pos = players(client.id).player.coords
                        senders.get(client.id).fold(IO.unit)(_(write[ServerMsg](GameStarted(map, client.id))))
                } >>
                ticker(events).flatMap { fiber =>
                    stateMachine(events, senders, InGame(clients, players, map), Some(fiber))
                }
        }

    def ticker(events: Queue[IO, Event]): IO[FiberIO[Unit]] = {
        (IO.sleep(8.milliseconds) >> events.offer(Tick))
            .foreverM
            .as(())
            .start
    }

    def endGame(events: Queue[IO, Event], senders: Map[String, String => IO[Unit]], clients: Map[String, Client]): IO[Unit] ={
        val resetClients = clients.map { 
            case (id, client) => 
                id -> client.copy(ready = false, character = None) 
            }
        
        NetworkState.broadcast(resetClients, senders, GameEnded()) >>
        NetworkState.broadcast(resetClients, senders, NetworkState.lobbyMsg(resetClients)) >>
        stateMachine(events, senders, Lobby(resetClients), None)
    }
}