package org.protocols.paxos.common

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import org.protocols.paxos.PaxosFactory

import scala.concurrent.duration._
import scala.util.Random


/**
  * @author Ilya Sergey
  */

abstract class PaxosTestUtilities(_system: ActorSystem)
  extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with MustMatchers
    with BeforeAndAfterAll {

  /**
    * Setup and test a single Paxos system 
    *
    * @param values        values to propose
    * @param factory       a factory for creating org.protocols.paxos system
    * @param acceptorNum   a number of acceptor nodes
    * @param acceptorsFail whether acceptors can go down randomly
    * @tparam A type of the value
    */
  protected def setupAndTestInstances[A](values: List[A],
                                         factory: PaxosFactory[A],
                                         acceptorNum: Int,
                                         acceptorsFail: Boolean = false): Unit = {
    val learnerNum = values.length
    val proposerNum = values.length

    val instance = factory.createPaxosInstance(system, proposerNum, acceptorNum, learnerNum)
    val proposers: Seq[ActorRef] = instance.proposers
    val acceptors: Seq[ActorRef] = instance.acceptors

    // Propose values
    proposeValues(values, factory, proposers, acceptors, acceptorsFail)

    // Wait for some time
    Thread.sleep(1000)

    // Learn the results
    val learners = instance.learners
    learnAcceptedValues(learners, factory)

    instance.killAll()
    afterAll()
  }


  /**
    * Propose values via multiple proposals 
    */
  private def proposeValues[A](values: List[A], factory: PaxosFactory[A],
                               proposers: Seq[ActorRef], acceptors: Seq[ActorRef],
                               acceptorsFail: Boolean = false): Unit = {
    import factory._
    val valuesShuffled = Random.shuffle(values.toVector)

    // Create proposing threads
    val rs = for {i <- valuesShuffled.indices
                  v = valuesShuffled(i)
                  p = proposers(i)} yield
      new Thread {
        override def run() {
          println(s"Proposing $v via ${p.path.name}")
          // Some randomized delays to compensate for ballot distribution
          Thread.sleep(Random.nextInt(10), 0)
          p ! ProposeValue(v)
        }
      }

    // Create threads that may non-deterministically kill acceptors
    val hitmen = acceptorKillers(acceptors, acceptorsFail)

    println()
    println("[Proposing values]")
    // Start all proposing threads
    for (r <- hitmen ++ rs) r.start()
    // Wait for them to finish
    for (r <- hitmen ++ rs) r.join()

  }

  private def acceptorKillers(acceptors: Seq[ActorRef], acceptorsFail: Boolean = false): Seq[Thread] = {
    if (!acceptorsFail) return Nil
    val shuffled = Random.shuffle(acceptors.toVector)
    val killers = for (i <- 0 until acceptors.size / 3) yield new Thread {
      override def run() = {
        val acc = shuffled(i)
        Thread.sleep(Random.nextInt(5), 0)
        println(s">> Sending poison pill to acceptor ${acc.path.name} <<")
        acc ! PoisonPill
      }
    }
    killers
  }

  /**
    * Learning accepting values 
    */
  private def learnAcceptedValues[A](learners: Seq[ActorRef], factory: PaxosFactory[A]) = {
    import factory._
    println("")
    println("[Learning values]")

    for (l <- learners) {
      l ! QueryLearner(self)
    }

    // Collect results
    val res = receiveN(learners.size, 3 seconds).asInstanceOf[Seq[LearnedAgreedValue]]

    for (LearnedAgreedValue(v, l) <- res) {
      println(s"Value from learner [${l.path.name}]: $v")
    }

    assert(res.size == learners.size, s"heard back from all learners or no one")
    assert(res.forall { case LearnedAgreedValue(v, l) => v == res.head.value },
      s"All learners should return the same result at the end.")

    println()
  }

  override def afterAll() {
    system.terminate()
  }


  private def fact(n: Int): Int = if (n < 1) 1 else n * fact(n - 1)

}




