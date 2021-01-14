package org.protocols.paxos.simple

import akka.actor.ActorSystem
import org.protocols.paxos.PaxosFactory
import org.protocols.paxos.common.PaxosTestUtilities

/**
  * @author Ilya Sergey
  */
class PaxosFailureTests extends PaxosTestUtilities(ActorSystem("PaxosFailureTests")) {

  s"All learners in Simple Paxos" must {
    
    s"agree in the case of failure" in {
      val values = List("Apple", "Banana", "Carrot", "Durian", "Peach")
      setupAndTestInstances(values, new PaxosFactory[String], 7, acceptorsFail = true)
    }

  }


}
