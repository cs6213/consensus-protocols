package org.protocols.paxos.simple

import akka.actor.ActorSystem
import org.protocols.paxos.PaxosFactory
import org.protocols.paxos.common.PaxosTestUtilities

/**
  * @author Ilya Sergey
  */
class SimplePaxosTests extends PaxosTestUtilities(ActorSystem("SimplePaxosTests")) {

  s"All learners in Simple Paxos" must {
    
    s"agree on the same non-trivial value" in {
      val values = List("Apple", "Banana", "Carrot", "Durian", "Peach")
      setupAndTestInstances(values, new PaxosFactory[String], 7)
    }

  }


}
