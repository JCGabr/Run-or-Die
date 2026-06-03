# Run-or-Die

## Estructura (WIP)
```
project/
├── shared/
│   ├── State.scala           ← Player, Position, State, GameLoopInfo
│   ├── Logic.scala           ← update(), colisiones, reglas de juego
│   └── Protocol.scala        ← mensajes JSON: PlayerMoved, Die, etc.
│
├── client/                   ← Scala.js, corre en el navegador
│   ├── Main.scala            ← game loop, requestAnimationFrame, conecta WS
│   ├── Drawer.scala          ← todo lo que toca canvas/ctx
│   └── InputHandler.scala    ← teclado, mouse, envía mensajes al servidor
│
└── server/                   ← Akka HTTP
    ├── Server.scala          ← WebSocket route, rooms, broadcast, ActorSystem
    └── Validator.scala       ← valida inputs antes de aplicar a Logic
```
## Compilar

Compila el JS
```
sbt fastLinkJS
```

Compila modulo especifico

```
sbt "server/run"
sbt "client/run"
sbt "shared/run"
```

## Output 

```
main.js
```

El [main.js](client/target/scala-3.8.3/client-fastopt/main.js) es el archivo de la compilación. Que se vincula al [index.html](/client/src/main/resource/index.html) (Abrir este archivo para ver el progreso)
