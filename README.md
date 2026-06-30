# Run-or-Die
## Estructura (WIP)
```
project/
├── shared/
│   ├── Map.scala              ← MapGame, Segment, SegmentFamily, generación del mapa
│   ├── Player.scala           ← Player, update(), resolveCollisions()
│   ├── State.scala            ← InputState, Constants, GameLoopInfo
│   └── Protocol.scala         ← mensajes JSON: PlayerMoved, Die, etc. 
│
├── client/                    ← Scala.js, corre en el navegador
│   ├── Main.scala             ← game loop, requestAnimationFrame, conecta WS, 
│   ├── Drawer.scala           ← todo lo que toca canvas/ctx
│   └── InputHandler.scala     ← teclado, envía mensajes al servidor
│
└── server/                    ← http4s + Cats Effect (Ember)
    └── Server.scala          ← WebSocket route, Queue[IO, Event], stateMachine

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

## Cómo jugar en red local

### 1. Compilar el cliente
```
sbt fastLinkJS
```
### 2. Levantar el servidor
```
sbt "server/run"
```

### 3. Servir el cliente por HTTP
Desde la carpeta `client/`
```
python -m http.server 8080
```

### 4. Abrir el juego
http://localhost:8080/src/main/resource/index.html

### 5. Jugar desde otra PC en la misma red
En la PC que corre el servidor, obtener la IP local:
```
ipconfig
```
(buscar la dirección IPv4 del adaptador de red activo, WiFi o Ethernet)

Desde la otra PC, en el navegador:
```
http://<IP-DEL-HOST>:8080/src/main/resource/index.html
```
Cuando el juego pida la IP del servidor, ambas PCs deben ingresar la IP del host (no `localhost`).
