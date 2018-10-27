package sample.neuron

import java.time.{Clock, Instant}

import akka.actor.ActorRef
import sample.neuron.Neuron.Signal

sealed trait NeuronState

case object Depolarized extends NeuronState

case object Spiking extends NeuronState

case object Refractory extends NeuronState

final case class NeuronData(potential: Double, lastEvent: Long, lastSpike: Long, postSynaptic: Vector[(Long, ActorRef)], lastSignal: Signal)

abstract class Neuron(restingPotential: Double, threshold: Double, refractory: Long)

object Neuron {

  final case class Connect(postSynaptic: ActorRef, delay: Long)

  case class Signal(preSynaptic: ActorRef, strength: Double, id: Long)
  def noSignal: Signal = Signal(ActorRef.noSender, 0, 0)

  val start: Long = now

  def now: Long = Instant.now(Clock.systemUTC()).toEpochMilli
}