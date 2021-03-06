package io.beehive

import scala.concurrent.Future

import akka.actor.{ ActorSystem , Actor, Props }
import akka.event.Logging
import akka.util.Timeout

import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import com.typesafe.config.{ Config, ConfigFactory }

object Main extends App with RequestTimeout {

    val config = ConfigFactory.load() 
    
     // Gets the host and a port from the configuration
    val host = config.getString("http.host")
    val port = config.getInt("http.port")

    implicit val system = ActorSystem() 
    
    //bindAndHandle requires an implicit ExecutionContext
    implicit val ec = system.dispatcher  

    // the RestApi provides a Route
    val api = new RestApi(system, requestTimeout(config)).routes 

    implicit val materializer = ActorMaterializer()
    
    //Start the HTTP server
    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(api, host, port)

    val log =  Logging(system.eventStream, "go-ticks")
    bindingFuture.map { serverBinding =>
        log.info(s"RestApi bound to ${serverBinding.localAddress} ")
    }.onFailure { 
        case ex: Exception =>
            log.error(ex, "Failed to bind to {}:{}!", host, port)
            system.terminate()
    }
}

trait RequestTimeout {
    import scala.concurrent.duration._
    def requestTimeout(config: Config): Timeout = {
        val t = config.getString("akka.http.server.request-timeout")
        val d = Duration(t)
        FiniteDuration(d.length, d.unit)
    }
}
