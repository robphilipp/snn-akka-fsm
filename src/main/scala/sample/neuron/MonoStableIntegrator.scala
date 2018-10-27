package sample.neuron

import akka.actor.{Actor, ActorSystem, FSM, Inbox, Props}
import sample.neuron.Neuron.{Connect, Signal}

/**
  * Monostable integrator neuron with depolarized and refractory states. Spiking is performed in the
  * transition from the depolarized to the refractory state.
  *
  * @param restingPotential The resting potential of the neuron (mV)
  * @param threshold        The spiking threshold of the neuron (mV)
  * @param refractory       The refractory period (ms)
  */
class MonoStableIntegrator(restingPotential: Double, threshold: Double, refractory: Long) extends FSM[NeuronState, NeuronData] {

  import sample.neuron.Neuron._
  import scala.concurrent.duration._

  startWith(Depolarized, NeuronData(restingPotential, now, now, Vector.empty, noSignal))

  // depolarized state
  when(Depolarized) {
    case Event(signal @ Signal(_, _, _), data @ NeuronData(_, _, _, _, _)) => depolarized(signal, data)
  }

  // refractory state can be call when a signal arrives or when the refractory period is over
  when(Refractory, stateTimeout = refractory millis) {
    case Event(signal @ Signal(_, _, _), data @ NeuronData(_, _, _, _, _)) => refractory(signal, data)
    case Event(StateTimeout, data @ NeuronData(_, _, _, _, _)) => refractory(Signal(self, 0, 0), data)
  }

  // any other event is handled here
  whenUnhandled {
    // connect a new neuron
    case Event(connect @ Connect(_, _), data @ NeuronData(_, _, _, _, _)) => connection(connect, data)

    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  // transitions
  onTransition {
    // sends spikes to the child neurons
    case Depolarized -> Refractory =>
      // stateData has the old state data, and nextStateData has the new state data
      nextStateData match {
        case data @ NeuronData(_, _, _, _, _) =>
          println(s"transition(Spiking, ${data.lastSignal.id}); state: $stateName -> Refractory; potential: ${data.potential}; elapsed: ${data.lastEvent - start}")
          data.postSynaptic.foreach(_._2 ! Signal(self, 1, data.lastSignal.id))
        case _ => println("transition: do nothing")
      }
  }

  initialize()

  /**
    * Adds the post-synapitic neuron to this neuron
    * @param connection The information about the connection
    * @param data The current state-data
    * @return The new state (same or another state) with updated state data
    */
  protected def connection(connection: Connect, data: NeuronData): FSM.State[NeuronState, NeuronData] = {
    println(s"connect(${self.path.name}); state: $stateName; pre-synaptic: ${self.path.name}; post-synaptic: ${connection.postSynaptic.path.name}; delay: ${connection.delay}")
    stay using data.copy(postSynaptic = data.postSynaptic :+ (connection.delay, connection.postSynaptic))
  }

  /**
    * Called when a siganl arrives and the neuron is in a depolarized state
    * @param signal The incoming signal
    * @param data The state-data
    * @return The new state (same or another state) with updated state data
    */
  protected def depolarized(signal: Signal, data: NeuronData): FSM.State[NeuronState, NeuronData] = {
    val currentTime = now
    val potential = data.potential + signal.strength
    println(s"event(${self.path.name}, ${signal.id}); state: $stateName; source: ${if (signal.preSynaptic != null) signal.preSynaptic.path.name else "[world]"}; potential: $potential; spike: ${now - data.lastSpike}; elapsed: ${data.lastEvent - start}")
    if (potential >= threshold) goto(Refractory) using data.copy(potential = potential, lastEvent = currentTime, lastSpike = currentTime, lastSignal = signal)
    else stay using data.copy(potential = potential, lastEvent = currentTime, lastSignal = signal)
  }

  /**
    * Called when a signal arrives when the neuron is in the refractory period, or when the refractory
    * period ends
    * @param signal The incoming signal or an empty signal at the end of the refractory period
    * @param data The current state data
    * @return The new state (same or another state) with updated state data
    */
  protected def refractory(signal: Signal, data: NeuronData): FSM.State[NeuronState, NeuronData] = {
    val currentTime = now
    val updatedData = data.copy(lastEvent = currentTime, potential = restingPotential)
    println(s"event(${self.path.name}, ${signal.id}); state: $stateName; source: ${if (signal.preSynaptic != null) signal.preSynaptic.path.name else "[world]"}; potential: ${updatedData.potential}; spike: ${now - data.lastSpike}; elapsed: ${data.lastEvent - start}")
    if (currentTime < data.lastSpike + refractory) stay using updatedData else goto(Depolarized) using updatedData
  }
}

object MonoStableIntegrator {

  val system = ActorSystem()

  def main(args: Array[String]): Unit = {
    val n11 = system.actorOf(Props(new MonoStableIntegrator(restingPotential = 0, threshold = 10, refractory = 20)), name = "input")
    val n2s = for (i <- 1 to 3) yield system.actorOf(Props(new MonoStableIntegrator(restingPotential = 0, threshold = 10, refractory = 20)), name = s"output-$i")

    // connect the neurons in the second layer to the neurons in the first layer
    n2s.foreach(neuron => n11 ! Connect(neuron, 0))

    val inbox: Inbox = Inbox.create(system)
    for (i <- 1 to 1000) {
      inbox.send(n11, Signal(Actor.noSender, 1, i))
      System.out.flush()
      if (i % 11 == 0) Thread.sleep(10) else Thread.sleep(5)
    }
  }
}