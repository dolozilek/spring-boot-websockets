package com.avast.websockets.server

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.util.function.Consumer


@SpringBootApplication
class WebsocketsApplication

fun main() {
    runApplication<WebsocketsApplication>()
}

@Configuration
class Configuration(val commandsWebsocketsHandler: CommandsWebsocketsHandler) {
    @Bean
    fun wsMapping(): SimpleUrlHandlerMapping {
        val mapping = SimpleUrlHandlerMapping()
        mapping.urlMap = mapOf(("/command-events" to commandsWebsocketsHandler))
        mapping.order = 1
        return mapping
    }

    @Bean
    fun handlerAdapter() = WebSocketHandlerAdapter()

}


enum class CommandState {
    FINISHED, FAILED
}

data class CommandId(val value: String)
data class SessionId(val value: String)
data class CommandEvent(val commandId: CommandId, val state: CommandState, val sessionId: SessionId)


@Component
class CommandsWebsocketsHandler : WebSocketHandler {

    val commandHandlers = mutableMapOf<SessionId, RabbitMessageHandler>()


    class RabbitMessageHandler(private val session: WebSocketSession, private val sink: FluxSink<WebSocketMessage>) {
        fun pushState(commandId: CommandId, commandState: CommandState, sessionId: SessionId) {
            val commandEvent = CommandEvent(commandId, commandState,sessionId)
            val om = ObjectMapper()
            sink.next(session.textMessage(om.writeValueAsString(commandEvent)))
        }
    }

    override fun handle(session: WebSocketSession): Mono<Void> {
        return session
                .receive()
                .map { SessionId(it.payloadAsText) }
                .flatMap {
                    val publisher = Flux.create(Consumer<FluxSink<WebSocketMessage>> { sink ->
                        val handler = RabbitMessageHandler(session, sink)
                        commandHandlers[it] = handler
                    })
                    println("Created publisher for $it")
                    session.send(publisher).doFinally { _ ->
                        println("Removing $it")
                        commandHandlers.remove(it)
                    }
                }.then()
    }

    fun produceMessage(commandId: CommandId, commandState: CommandState, sessionId: String) {
        val ssid = SessionId(sessionId)
        val handler = commandHandlers[ssid]
        if (handler != null) {
            handler.pushState(commandId, commandState, ssid)
        } else {
            println("No handler found")
        }
    }
}


@RestController
class RabbitProducer(val commandsWebsocketsHandler: CommandsWebsocketsHandler) {
    @GetMapping("event")
    fun produceEvent(@RequestParam sessionId: String, @RequestParam commandId: String, @RequestParam commandState: CommandState) {
        commandsWebsocketsHandler.produceMessage(CommandId(commandId), commandState, sessionId)
    }
}
