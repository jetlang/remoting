Jetlang Remoting project provides apis for connecting distributed systems. Jetlang provides a language agnostic wire level protocol and threading model for asynchronous distributed messaging.
The library also contains client and server websocket implementations.

The latest version is available in the maven repository.

https://repo1.maven.org/maven2/org/jetlang/jetlang.remoting/

#Dependencies
  * JDK 8
  * Jetlang Core - https://github.com/jetlang/core

#WebSocket Features
  * Client and Server implementations
  * The jetlang core library is the only dependency
  * Websocket connections can subscribe and schedule jetlang events
  * Low latency
  * Minimal allocations
  * Non-blocking sends
  * Http Server
  * Ideal for microservices - embeddable, lightweight
  * Configurable threading - Single threaded for easy state management or use jetlang pool fibers for massive scalability

#Getting Started

https://github.com/jetlang/remoting/tree/master/src/test/java/org/jetlang/remote/example/ws

#Jetlang Remoting Api Features

  * Language agnostic messaging protocol - https://github.com/jetlang/remoting/wiki
  * Stateful distributed sessions
  * Session heartbeating
  * Session lifecycles - Connect, Heartbeat, Session Timeout, Logout, Disconnect
  * Automatic reconnects
  * Asynchronous I/O with callbacks 
  * Tight integration with Jetlang threading
  * Message format agnostic - binary, java serialization, json, thrift, etc. 
  * Simple text topics
  * High performance
  * Embeddable - Client and Acceptor implementations
  * Distributed - No central server required.
  
#Getting Started

https://github.com/jetlang/remoting/tree/master/src/test/java/org/jetlang/remote/example/chat


  
