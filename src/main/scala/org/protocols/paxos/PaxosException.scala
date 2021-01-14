package org.protocols.paxos

/**
  * @author Ilya Sergey
  */
case class PaxosException(msg: String) extends Exception {
  override def getMessage: String = msg
}