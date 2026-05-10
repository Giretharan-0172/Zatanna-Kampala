# Java HTTP Passthrough Proxy

A multi-threaded HTTP/HTTPS passthrough proxy built from scratch using Java's standard library — no frameworks, no external dependencies. Intercepts, logs, and forwards HTTP traffic. Tunnels HTTPS via the `CONNECT` method.

---

## Context

This micro-project was built as a 4-hour proof-of-work to explore the low-level networking mechanics required to build Man-in-the-Middle (MITM) proxies for AI agents (specifically inspired by Zatanna's Kampala architecture). The goal was to drop down to the `java.net.Socket` layer and bypass high-level frameworks to demonstrate a first-principles understanding of TCP routing, byte-stream manipulation, and `CONNECT` tunneling.

---

## What it does

- Listens on `localhost:8080` for incoming browser traffic
- Parses raw HTTP request headers from the byte stream
- Extracts the destination `Host` and port
- Logs all request and response headers to console
- For plain HTTP: forwards the full request to the destination server and pipes the response back
- For HTTPS (`CONNECT`): responds with `200 Connection Established` and opens a raw TCP tunnel — bytes flow in both directions without decryption
- Handles multiple concurrent connections via a thread pool

## What it won't do

- Decrypt HTTPS traffic (TLS termination requires dynamic certificate generation — out of scope)
- Modify request or response payloads
- Cache responses

---

## Architecture

```
Browser (proxy configured to localhost:8080)
    │
    ▼
ServerSocket on :8080
    │  accept()
    ▼
ClientHandler (thread from pool)
    │
    ├── parse Host header
    ├── log request headers
    ├── open Socket to destination
    │
    ├── SharedPipe [client → destination]  (forwards request + body)
    └── SharedPipe [destination → client]  (forwards response, logs headers)
```

Both pipe threads run concurrently. A `CountDownLatch(2)` keeps the destination socket open until both directions finish.

---

## Project structure

```
src/
├── ProxyServer.java      # Entry point. ServerSocket loop, submits to thread pool.
├── ClientHandler.java    # Per-connection logic. Header parsing, host extraction, pipe setup.
├── SharedPipe.java       # Runnable byte forwarder. One instance per direction.
└── SharedPool.java       # Shared ExecutorService (fixed thread pool of 50).
```

---

## How to run

**Requirements:** Java 17+

**Compile:**
```bash
javac *.java
```

**Run:**
```bash
java ProxyServer
```

The proxy starts listening on port `8080`.

**Configure your browser:**

Set your system or browser proxy to:
- Host: `127.0.0.1`
- Port: `8080`

In Windows: Settings → Network → Proxy → Manual proxy setup.

**Test with curl:**
```bash
curl -v --proxy http://127.0.0.1:8080 http://example.com
```

---

## How it works internally

### Header parsing

Incoming bytes are read one at a time from the client socket's `InputStream`, appended to a `StringBuilder`, until the sequence `\r\n\r\n` is detected — the HTTP header terminator. No `BufferedReader` is used, so no body bytes are accidentally consumed.

### Host extraction

The `Host:` header line is scanned from the parsed header block. If the value contains a `:` (e.g. `example.com:8080`), it is split to separate hostname and port. Default port is `80` for HTTP and `443` for HTTPS.

### CONNECT tunneling

When the request method is `CONNECT`, the proxy:
1. Responds with `HTTP/1.1 200 Connection Established\r\n\r\n`
2. Opens a raw TCP socket to the destination
3. Starts two `SharedPipe` threads — one per direction
4. All subsequent bytes (TLS handshake, encrypted data) are forwarded blindly

### Concurrent piping

Each connection spawns two `SharedPipe` runnables submitted to the shared thread pool. Each reads from one `InputStream` and writes to one `OutputStream` in a loop until `-1` (connection closed). A `CountDownLatch` coordinates cleanup — both pipes call `countDown()` in a `finally` block, and `ClientHandler` calls `await()` before closing the destination socket.

---

## Limitations and known issues

- HTTPS traffic is tunneled but not inspected — headers inside TLS are invisible to the proxy
- `Connection: keep-alive` is rewritten to `Connection: close` for HTTP to simplify stream termination
- No timeout handling — a stalled connection will hold a thread until the OS closes it
- No HTTPS interception (would require dynamic CA certificate generation per host)

---

## Concepts behind the build

| Concept | Where it appears |
|---|---|
| TCP sockets | `ServerSocket`, `Socket` in `ProxyServer` and `ClientHandler` |
| HTTP as raw text | Header parsing in `extractHost()` |
| Byte stream I/O | `InputStream`/`OutputStream` throughout, never `BufferedReader` |
| Thread pool | `ExecutorService` in `SharedPool` |
| Concurrent piping | Two `SharedPipe` runnables per connection |
| Synchronization | `CountDownLatch` in `ClientHandler` |
| CONNECT tunneling | `isConnect` flag, `200 Connection Established` response |