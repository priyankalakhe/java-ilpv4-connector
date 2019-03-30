package ilpv4

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class SimpleConnectorSimulation extends Simulation {

  val httpConf = http.baseUrl("http://localhost:8080")

  val sample = scenario("Health Check").exec(
    http("Get Health").get("/actuator/health").check(status.is(200))
  )

  setUp(sample.inject(constantUsersPerSec(500) during (60 seconds))
    .protocols(httpConf))
//    .assertions(
//      global.successfulRequests.percent.is(100),
//      global.responseTime.max.lt(1000),
//      global.responseTime.mean.lt(750),
//      global.requestsPerSec.lt(10)
//    )
}