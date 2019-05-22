package com.avast.websockets.client

import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration


fun main(args: Array<String>) {
    val client = ReactorNettyWebSocketClient()
    client.execute(URI.create("ws://localhost:8080/command-events")) { session ->
        session.send(Mono.just(session.textMessage("${System.currentTimeMillis()}")))
                .thenMany(session.receive()
                        .map { it.payloadAsText }
                        .log()
                ).then()
    }.block(Duration.ofSeconds(100L))



}