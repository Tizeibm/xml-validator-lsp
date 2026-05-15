# Mass XML Validator — VS Code Extension + Java LSP Server

Validate massive XML files (100 GB+) against XSD schemas **without crashing VS Code or exhausting RAM**.

## Architecture

```
┌─────────────────────────────────────┐
│         VS Code Extension           │
│         (TypeScript Client)         │
│                                     │
│  ┌─ Command: "Valider un gros      │
│  │  fichier XML"                    │
│  │                                  │
│  ├─ showOpenDialog (XML)            │
│  ├─ showOpenDialog (XSD)            │
│  │                                  │
│  ├─ OutputChannel (error display)   │
│  └─ withProgress (spinner)          │
│                                     │
│  custom/startValidation ────────────┼──┐
│  custom/validationBatch ◄───────────┼──┤
│  custom/validationFinished ◄────────┼──┤
└─────────────────────────────────────┘  │
                                         │
         stdin/stdout JSON-RPC           │
                                         │
┌────────────────────────────────────────┼┐
│         Java LSP Server               ││
│         (Woodstox + StAXSource)       ││
│                                       ││
│  ┌─ 8 MB BufferedInputStream          ││
│  ├─ Woodstox XMLStreamReader          ││
│  │  (Cursor API, zero-allocation)     ││
│  ├─ StAXSource → Validator            ││
│  ├─ BatchingErrorHandler (1000/batch) ││
│  └─ Worker Thread (non-blocking)      ││
│                                       ││
└───────────────────────────────────────┘│
```

## Key Design Decisions

- **No file opened in editor** — The XML file is never loaded into VS Code's editor buffer
- **Custom LSP notifications** — Standard `didOpen`/`publishDiagnostics` is completely banned
- **Streaming validation** — Single-pass StAX reading, no DOM tree ever built
- **XXE protection** — DTD and external entity resolution fully disabled
- **Batched error reporting** — Errors sent in batches of 1000 to prevent LSP channel flooding

## Prerequisites

- **Java 21+** (for the LSP server)
- **Maven 3.9+** (to build the server)
- **Node.js 18+** & **npm** (to build the extension)
- **VS Code 1.85+**

## Building

### 1. Build the Java Server

```bash
cd server
mvn clean package
```

This produces `server/target/xml-validator-server.jar` (fat JAR with all dependencies).

### 2. Copy the JAR to the extension

```bash
mkdir -p client/server
cp server/target/xml-validator-server.jar client/server/
```

### 3. Build the VS Code Extension

```bash
cd client
npm install
npm run compile
```

### 4. Run in Development

1. Open the `client/` folder in VS Code
2. Press `F5` to launch the Extension Development Host
3. In the new window, press `Ctrl+Shift+P` and run **"Valider un gros fichier XML"**
4. Select your XML file, then your XSD file
5. Watch the validation progress in the "Mass XML Validator" output panel

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| `xmlValidator.javaPath` | `java` | Path to Java 21+ executable |
| `xmlValidator.jvmArgs` | `["-Xmx512m", "-Xms128m"]` | JVM arguments. 512 MB is enough for streaming. |

## Memory Profile

The server is designed for **constant memory usage** regardless of file size:

- **XSD Schema**: Loaded once, typically < 10 MB
- **Woodstox internal buffer**: 512 KB
- **BufferedInputStream**: 8 MB
- **Error batch buffer**: ~1000 error objects, flushed regularly
- **Total**: Typically < 50 MB for any file size

## License

MIT
