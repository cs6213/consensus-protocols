package org.protocols.paxos.simple

import akka.actor.{Actor, ActorRef}
import org.protocols.paxos.PaxosVocabulary

import scala.collection.immutable.Nil

/**
  * An implementation of a single-decree Paxos
  *
  * @author Ilya Sergey
  */

trait SimplePaxos[T] extends PaxosVocabulary[T] {

  // Instantiate messages

  /**
    * The acceptor class for a Single Decree Paxos
    */
  class Acceptor extends Actor {

    var currentBallot: Ballot = -1
    var chosenValues: List[(Ballot, T)] = Nil

    override def receive: Receive = {
      case Phase1A(b, l) =>
        if (b > currentBallot) {
          currentBallot = b
          l ! Phase1B(promise = true, self, findMaxBallotAccepted(chosenValues))
        } else {
          /* do nothing */
        }
      case m@Phase2A(b, l, v, _) =>
        if (b == currentBallot) {
          // record the value
          chosenValues = (b, v) :: chosenValues
          // we may even ignore this step
          l ! Phase2B(b, self, ack = true)
        } else {
          /* do nothing */
        }
      // Send accepted request
      case QueryAcceptor(sender) =>
        val value = findMaxBallotAccepted(chosenValues).map(_._2)
        // println(s"[Acceptor ${self.path.name}]: sending value $value to ${sender.path.name}")
        sender ! ValueAcc(self, value)
    }

    override def postStop(): Unit = {
      println(s"Acceptor ${self.path.name} has been stopped")
    }
  }

  /**
    * The proposer class, initiating the agreement procedure
    *
    * @param acceptors a set of acceptors in this instance
    * @param myBallot  fixed ballot number
    */
  class Proposer(val acceptors: Seq[ActorRef], val myBallot: Ballot) extends Actor {

    def proposerPhase1: Receive = {
      case ProposeValue(v) =>
        // Start Paxos round with my givenballot
        for (a <- acceptors) a ! Phase1A(myBallot, self)
        context.become(proposerPhase2(v, Nil))
    }

    def proposerPhase2(v: T, responses: List[(ActorRef, Option[(Ballot, T)])]): Receive = {
      case Phase1B(true, a, vOpt) =>
        val newResponses = (a, vOpt) :: responses
        // find maximal group
        if (newResponses.size > acceptors.size / 2) {
          // found quorum
          val nonEmptyResponses = responses.map(_._2).filter(_.nonEmpty)
          val (mBal, toPropose) = nonEmptyResponses match {
            case Nil => (-1, v)
            case rs => rs.map(_.get).maxBy(_._1) // A highest-ballot proposal
          }
          val quorum = newResponses.map(_._1)

          for (a <- acceptors) a ! Phase2A(myBallot, self, toPropose, mBal)
          context.become(finalStage)
        } else {
          context.become(proposerPhase2(v, newResponses))
        }
    }

    /**
      * Now we only respond to queries about selected values
      */
    def finalStage: Receive = PartialFunction.empty

    override def receive: Receive = proposerPhase1
  }

  /**
    * The learner class. Queries acceptors for values
    */
  class Learner(val acceptors: Seq[ActorRef]) extends Actor {

    override def receive: Receive = waitForQuery

    /**
      * Wait for the client to ask something.
      * Upon receiving the query, ask the acceptors.
      * Then get back to the initial sender.
      */
    def waitForQuery: Receive = {
      case QueryLearner(sender) =>
        for (a <- acceptors) a ! QueryAcceptor(self)
        context.become(processResponses(sender, Nil))

    }

    private def processResponses(sender: ActorRef, acc: List[Option[T]]): Receive = {
      case ValueAcc(a, vOpt) =>
        val results = vOpt :: acc
        val (r, n) = results.groupBy(x => x).map { case (x, l) => (x, l.size) }.toList.maxBy(_._2)

        // Check if there is a quorum
        if (n > acceptors.size / 2) {
          sender ! LearnedAgreedValue(r, self)
          context.become(waitForQuery)
        } else {
          context.become(processResponses(sender, results))
        }
    }
  }

}
