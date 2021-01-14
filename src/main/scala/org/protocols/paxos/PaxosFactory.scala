package org.protocols.paxos

import akka.actor.{ActorRef, ActorSystem, Props}
import org.protocols.paxos.simple.SimplePaxos

import scala.collection.immutable.IndexedSeq

/**
  * @author Ilya Sergey
  */

class PaxosFactory[T] extends SimplePaxos[T] with PaxosVocabulary[T] {

  val AcceptorClass: Class[_] = classOf[Acceptor]
  val ProposerClass: Class[_] = classOf[Proposer]
  val LearnerClass: Class[_] = classOf[Learner]

  /**
    *
    * Given an actor system, creates an instance of a single-decree Paxos
    * 
    */
  def createPaxosInstance(system: ActorSystem, numProposers: Int,
                          numAcceptors: Int, numLearners: Int): PaxosConfiguration = {

    // Sanity checks for the configuration
    if (numProposers <= 0) throw PaxosException(s"There should be at least one proposer (currently $numProposers)")
    if (numLearners <= 0) throw PaxosException(s"There should be at least one learner (currently $numLearners)")
    if (numAcceptors <= 0) throw PaxosException(s"Too few acceptors (currently $numAcceptors)")

    val acceptors = createAcceptors(system, numAcceptors)
    val proposers = createProposers(system, numProposers, acceptors)
    val learners = createLearners(system, numLearners, acceptors)

    new PaxosConfiguration(proposers, learners, acceptors)
  }

  protected def createProposers(system: ActorSystem, numProposers: Int, acceptors: Seq[ActorRef]) = {
    for (i <- 0 until numProposers) yield {
      system.actorOf(Props(ProposerClass, this, acceptors, i), name = s"Proposer-P$i")
    }
  }

  protected def createAcceptors(system: ActorSystem, numAcceptors: Int) = {
    for (i <- 0 until numAcceptors) yield {
      system.actorOf(Props(AcceptorClass, this), name = s"Acceptor-A$i")
    }
  }

  protected def createLearners(system: ActorSystem, numLearners: Int, acceptors: IndexedSeq[ActorRef]) = {
    for (i <- 0 until numLearners) yield {
      system.actorOf(Props(LearnerClass, this, acceptors), name = s"Learner-L$i")
    }
  }


}

