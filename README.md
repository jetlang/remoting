Jetlang Remoting provides a language agnostic wire level protocol and threading model for asynchronous distributed messaging.

= Features =

  * Language agnostic messaging protocol - JetlangRemotingSpec
  * Stateful distributed sessions
  * Session heartbeating
  * Session lifecycles - Connect, Heartbeat, Session Timeout, Logout, Disconnect
  * Automatic reconnects
  * Asynchronous I/O with callbacks 
  * Tight integration with Jetlang threading
  * Message format agnostic - binary, java serialization, json, etc. 
  * Simple text topics
  * High performance
  * Embeddable - Client and Acceptor implementations
  * Distributed - No central server required.

= Code =

[http://code.google.com/p/jetlang/source/browse/#svn%2Fserver Source]

[http://code.google.com/p/jetlang/source/browse/server/src/test/java/org/jetlang/remote/IntegrationBase.java Examples]

[http://code.google.com/p/jetlang/source/browse/server/src/test/#test%2Fjava%2Forg%2Fjetlang%2Fremote%2Fexample%2Fchat Example]
