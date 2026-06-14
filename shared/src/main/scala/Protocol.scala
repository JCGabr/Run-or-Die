import upickle.default.*

sealed trait ClientMsg derives ReadWriter
case class JoinLobby(name: String) extends ClientMsg
case class SelectCharacter(char: String) extends ClientMsg
case class SetReady(ready: Boolean) extends ClientMsg
case class SendInput(input: InputState) extends ClientMsg

sealed trait ServerMsg derives ReadWriter
case class LobbyUpdate(players: List[LobbyPlayer]) extends ServerMsg
case class GameStarted(map: Vector[Vector[String]], myId: String) extends ServerMsg
case class GameTick(players: List[PlayerSnap]) extends ServerMsg
case class GameEnded() extends ServerMsg

case class LobbyPlayer(id: String, name: String, char: Option[String], ready: Boolean) derives ReadWriter
case class PlayerSnap(id: String, x: Float, y: Float, alive: Boolean, sizeX: Float, sizeY: Float) derives ReadWriter