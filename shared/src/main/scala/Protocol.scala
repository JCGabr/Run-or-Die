import upickle.default.*

sealed trait ClientMsg derives ReadWriter
case class JoinLobby(name: String) extends ClientMsg
case class SelectCharacter(char: String) extends ClientMsg
case class SetReady(ready: Boolean) extends ClientMsg
case class SendInput(input: InputMsg) extends ClientMsg

sealed trait InputMsg derives ReadWriter

case object PressLeft extends InputMsg
case object ReleaseLeft extends InputMsg
case object PressRight extends InputMsg
case object ReleaseRight extends InputMsg
case object PressJump extends InputMsg
case object ReleaseJump extends InputMsg

sealed trait ServerMsg derives ReadWriter
case class LobbyUpdate(players: List[LobbyPlayer]) extends ServerMsg
case class GameStarted(map: Vector[Vector[String]], myId: String) extends ServerMsg
case class GameTick(players: List[PlayerMemento]) extends ServerMsg
case class GameEnded() extends ServerMsg

case class LobbyPlayer(id: String, name: String, char: Option[String], ready: Boolean) derives ReadWriter
case class PlayerMemento(id: String, x: Float, y: Float, alive: Boolean, sizeX: Float, sizeY: Float) derives ReadWriter