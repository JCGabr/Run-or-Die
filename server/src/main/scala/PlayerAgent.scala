import cats.effect.*
import cats.effect.std.Queue
import cats.syntax.all.*

case class PlayerAgent(
    id: String,
    state: Ref[IO, InGamePlayer],
    inbox: Queue[IO, InputMsg],
    private val fiber: FiberIO[Unit]
) {
  def send(input: InputMsg): IO[Unit] = inbox.offer(input)
  def shutdown: IO[Unit] = fiber.cancel
}

object PlayerAgent {

  def spawn(id: String, initialPlayer: Player): IO[PlayerAgent] =
    for {
      stateRef <- Ref.of[IO, InGamePlayer](InGamePlayer(id, initialPlayer))
      inbox <- Queue.unbounded[IO, InputMsg]
      fiber <- mailboxLoop(stateRef, inbox).start
    } yield PlayerAgent(id, stateRef, inbox, fiber)

  private def mailboxLoop(
      state: Ref[IO, InGamePlayer],
      inbox: Queue[IO, InputMsg]
  ): IO[Unit] =
    inbox.take.flatMap { input =>
      state.update(igp =>
        igp.copy(lastInput = applyInput(igp.lastInput, input))
      )
    }.foreverM

  private def applyInput(current: InputState, input: InputMsg): InputState =
    input match {
      case PressLeft => current.copy(moveLeft = true)
      case ReleaseLeft => current.copy(moveLeft = false)
      case PressRight => current.copy(moveRight = true)
      case ReleaseRight => current.copy(moveRight = false)
      case PressJump => current.copy(jump = true)
      case ReleaseJump => current.copy(jump = false)
    }
}
