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
case class Lobby(clients: Map[String, Client]) extends Phase
case class InGame(
    clients: Map[String, Client],
    map: Vector[Vector[String]],
    initialPlayerCount: Int
) extends Phase

sealed trait Event
case class Connected(id: String, send: String => IO[Unit]) extends Event
case class Disconnected(id: String) extends Event
case class Received(id: String, msg: ClientMsg) extends Event
case object GameOver extends Event

object NetworkState {
  def broadcast(
      clients: Map[String, Client],
      senders: Map[String, String => IO[Unit]],
      msg: ServerMsg
  ): IO[Unit] = {
    clients.keys.toList.parTraverse_ { id =>
      senders.get(id) match {
        case None       => IO.unit
        case Some(send) => send(write[ServerMsg](msg))
      }
    }
  }

  def lobbyMsg(clients: Map[String, Client]): LobbyUpdate = {
    LobbyUpdate(
      clients.values
        .map(client =>
          LobbyPlayer(client.id, client.name, client.character, client.ready)
        )
        .toList
    )
  }

  def expand(mat: Vector[Vector[Segment]]): Vector[Vector[String]] = {
    mat.flatMap { row =>
      val patterns = row.map(_.pattern)
      (0 until 2).map(y => patterns.flatMap(_(y)))
    }
  }
}

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      events    <- Queue.unbounded[IO, Event]
      agentsRef <- Ref.of[IO, Map[String, PlayerAgent]](Map.empty)

      server =
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"9000")
          .withHttpWebSocketApp(routes(_, events, agentsRef).orNotFound)
          .build
          .useForever

      loop = stateMachine(
        events,
        agentsRef,
        senders = Map.empty,
        phase = Lobby(Map.empty),
        coordinatorFiber = None
      )

      _ <- server.both(loop).void
    } yield ()

  def routes(
      wsb: WebSocketBuilder2[IO],
      events: Queue[IO, Event],
      agentsRef: Ref[IO, Map[String, PlayerAgent]]
  ): HttpRoutes[IO] = {
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
                case SendInput(input) =>
                  agentsRef.get.flatMap(_.get(id).fold(IO.unit)(_.send(input)))
                case other =>
                  events.offer(Received(id, other))
              }
            case WebSocketFrame.Close(_) =>
              events.offer(Disconnected(id))
            case _ =>
              IO.unit
          }

        wsb.build(toClient, fromClient)
      }
    }
  }

  def stateMachine(
      events: Queue[IO, Event],
      agentsRef: Ref[IO, Map[String, PlayerAgent]],
      senders: Map[String, String => IO[Unit]],
      phase: Phase,
      coordinatorFiber: Option[FiberIO[Unit]]
  ): IO[Unit] = {
    events.take.flatMap { event =>
      (phase, event) match {

        case (Lobby(clients), Connected(id, send)) =>
          val new_clients =
            clients + (id -> Client(id, "?", None, ready = false))
          val new_senders = senders + (id -> send)

          NetworkState.broadcast(
            new_clients,
            new_senders,
            NetworkState.lobbyMsg(new_clients)
          ) >>
            stateMachine(events, agentsRef, new_senders, Lobby(new_clients), coordinatorFiber)

        case (Lobby(clients), Disconnected(id)) =>
          val new_clients = clients - id
          val new_senders = senders - id

          NetworkState.broadcast(
            new_clients,
            new_senders,
            NetworkState.lobbyMsg(new_clients)
          ) >>
            stateMachine(events, agentsRef, new_senders, Lobby(new_clients), coordinatorFiber)

        case (Lobby(clients), Received(id, JoinLobby(name))) =>
          val new_clients = clients.updated(id, clients(id).copy(name = name))

          NetworkState.broadcast(
            new_clients,
            senders,
            NetworkState.lobbyMsg(new_clients)
          ) >>
            stateMachine(events, agentsRef, senders, Lobby(new_clients), coordinatorFiber)

        case (Lobby(clients), Received(id, SelectCharacter(character))) =>
          val new_clients =
            clients.updated(id, clients(id).copy(character = Some(character)))

          NetworkState.broadcast(
            new_clients,
            senders,
            NetworkState.lobbyMsg(new_clients)
          ) >>
            stateMachine(events, agentsRef, senders, Lobby(new_clients), coordinatorFiber)

        case (Lobby(clients), Received(id, SetReady(ready))) =>
          val new_clients = clients.updated(id, clients(id).copy(ready = ready))
          val allReady = new_clients.nonEmpty && new_clients.values.forall(
            client => client.ready && client.character.isDefined
          )

          NetworkState.broadcast(
            new_clients,
            senders,
            NetworkState.lobbyMsg(new_clients)
          ) >>
            (
              if (allReady)
                startGame(events, agentsRef, senders, new_clients)
              else
                stateMachine(events, agentsRef, senders, Lobby(new_clients), coordinatorFiber)
            )

        case (InGame(clients, map, initialPlayerCount), Disconnected(id)) =>
          val new_clients = clients - id
          val new_senders = senders - id

          agentsRef
            .modify(m => (m - id, m.get(id)))
            .flatMap(_.fold(IO.unit)(_.shutdown)) >>
            stateMachine(
              events,
              agentsRef,
              new_senders,
              InGame(new_clients, map, initialPlayerCount),
              coordinatorFiber
            )

        case (InGame(clients, _, _), GameOver) =>
          coordinatorFiber.fold(IO.unit)(_.cancel) >>
            endGame(events, agentsRef, senders, clients)

        case _ =>
          stateMachine(events, agentsRef, senders, phase, coordinatorFiber)
      }
    }
  }

  def startGame(
      events: Queue[IO, Event],
      agentsRef: Ref[IO, Map[String, PlayerAgent]],
      senders: Map[String, String => IO[Unit]],
      clients: Map[String, Client]
  ): IO[Unit] =
    for {
      rawMap <- IO(new MapGame(500, 0, 30, 0).generate())
      map = NetworkState.expand(rawMap)

      agentsList <- clients.values.toList.zipWithIndex.traverse { case (client, index) =>
        val stats = Constants.CHARACTERS(client.character.get)
        val player = Player(
          Vector2(index * 40f, 0f),
          Vector2(0f, 0f),
          30f,
          30f,
          stats,
          false,
          true
        )
        PlayerAgent.spawn(client.id, player).map(client.id -> _)
      }
      agents = agentsList.toMap
      _ <- agentsRef.set(agents)

      _ <- clients.values.toList.traverse_ { client =>
        senders.get(client.id).fold(IO.unit)(_(write[ServerMsg](GameStarted(map, client.id))))
      }

      coordinatorFiber <- coordinator(events, agentsRef, senders, clients, map, agents.size).start

      _ <- stateMachine(
        events,
        agentsRef,
        senders,
        InGame(clients, map, agents.size),
        Some(coordinatorFiber)
      )
    } yield ()

  def coordinator(
      events: Queue[IO, Event],
      agentsRef: Ref[IO, Map[String, PlayerAgent]],
      senders: Map[String, String => IO[Unit]],
      clients: Map[String, Client],
      map: Vector[Vector[String]],
      initialPlayerCount: Int
  ): IO[Unit] = {

    def tick(lastTick: Long): IO[Unit] =
      IO.sleep(8.milliseconds) >> {
        val now = System.currentTimeMillis()
        val dt = ((now - lastTick) / 1000f).min(0.1f)

        for {
          agents <- agentsRef.get

          updated <- agents.toList.parTraverse { case (_, agent) =>
            agent.state.modify { igp =>
              val moved = igp.copy(player = igp.player.update(igp.lastInput, dt, map))
              (moved, moved)
            }
          }

          claimedIds = updated.filter(_.player.claimed_checkpoint).map(_.id).toSet

          _ <-
            if (claimedIds.isEmpty) IO.unit
            else
              agents.toList.parTraverse_ { case (id, agent) =>
                if (claimedIds(id))
                  agent.state.update(igp =>
                    igp.copy(player = igp.player.copy(claimed_checkpoint = false))
                  )
                else
                  agent.state.update { igp =>
                    if (!igp.player.is_alive) igp
                    else {
                      val shouldPenalize = claimedIds.exists { claimerId =>
                        val claimer = updated.find(_.id == claimerId).get.player
                        igp.player.coords.x < claimer.last_checkpoint.x + Constants.BLOCK_SIZE
                      }
                      if (shouldPenalize)
                        igp.copy(player = igp.player.copy(
                          max_time = (igp.player.max_time - 1f).max(0f),
                          current_time = (igp.player.current_time - 1f).max(0f)
                        ))
                      else igp
                    }
                  }
              }

          finalState <- agentsRef.get.flatMap(_.toList.parTraverse { case (_, agent) => agent.state.get })

          memento = finalState.map(igp =>
            PlayerMemento(
              igp.id,
              igp.player.coords.x,
              igp.player.coords.y,
              igp.player.is_alive,
              igp.player.stats.size.x,
              igp.player.stats.size.y,
              igp.player.current_time,
              igp.player.max_time,
              clients(igp.id).character.get,
              igp.player.velocity.x,
              igp.player.velocity.y
            )
          )

          alivePlayers = finalState.count(_.player.is_alive)
          shouldEnd =
            if (initialPlayerCount == 1) alivePlayers == 0
            else alivePlayers <= 1

          _ <- NetworkState.broadcast(clients, senders, GameTick(memento))
          _ <- if (shouldEnd) events.offer(GameOver) else tick(now)
        } yield ()
      }

    tick(System.currentTimeMillis())
  }

  def endGame(
      events: Queue[IO, Event],
      agentsRef: Ref[IO, Map[String, PlayerAgent]],
      senders: Map[String, String => IO[Unit]],
      clients: Map[String, Client]
  ): IO[Unit] = {
    val resetClients = clients.map { case (id, client) =>
      id -> client.copy(ready = false, character = None)
    }

    agentsRef.get.flatMap(_.values.toList.traverse_(_.shutdown)) >>
      agentsRef.set(Map.empty) >>
      NetworkState.broadcast(resetClients, senders, GameEnded()) >>
      NetworkState.broadcast(
        resetClients,
        senders,
        NetworkState.lobbyMsg(resetClients)
      ) >>
      stateMachine(events, agentsRef, senders, Lobby(resetClients), None)
  }
}
