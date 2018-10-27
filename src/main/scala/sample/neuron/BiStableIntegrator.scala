package sample.neuron

import akka.actor.{Actor, ActorSystem, FSM, Inbox, Props, Timers}
import sample.neuron.BiStableIntegrator.TonicFire
import sample.neuron.Neuron.{Connect, Signal}

import scala.concurrent.duration._

class BiStableIntegrator(restingPotential: Double,
                         spikingThreshold: Double,
                         restingThreshold: Double,
                         refractory: Long,
                         interval: Long,
                         decayHalfLife: Long
                        ) extends FSM[NeuronState, NeuronData] with Timers {

  import sample.neuron.Neuron._
  //  import context.dispatcher

  startWith(Depolarized, NeuronData(restingPotential, now, now, Vector.empty, noSignal))

  when(Depolarized) {
    case Event(signal @ Signal(_, _, _), data @ NeuronData(_, _, _, _, _)) => depolarized(signal, data)
  }

  when(Spiking) {
    case Event(signal @ Signal(_, _, _), data @ NeuronData(_, _, _, _, _)) => spiking(signal, data)
  }

  when(Refractory) {
    case Event(signal @ Signal(_, _, _), data @ NeuronData(_, _, _, _, _)) => refractory(signal, data)
  }

  whenUnhandled {
    // connect a new neuron
    case Event(connect @ Connect(_, _), data @ NeuronData(_, _, _, _, _)) => connection(connect, data)

    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  protected def connection(connection: Connect, data: NeuronData): FSM.State[NeuronState, NeuronData] = {
    println(s"connect; state: $stateName; pre-synaptic: ${self.path.name}; post-synaptic: ${connection.postSynaptic.path.name}; delay: ${connection.delay}")
    stay using data.copy(postSynaptic = data.postSynaptic :+ (connection.delay, connection.postSynaptic))
  }

  protected def depolarized(signal: Signal, data: NeuronData): FSM.State[NeuronState, NeuronData] = {
    val currentTime = now
    val potential = data.potential + signal.strength
    println(s"event(${self.path.name}); state: $stateName; source: ${if (signal.preSynaptic != null) signal.preSynaptic.path.name else "[world]"}; potential: $potential; spike: ${now - data.lastSpike}; elapsed: ${data.lastEvent - start}")
    if (potential >= spikingThreshold) goto(Spiking) using data.copy(potential = potential, lastEvent = currentTime, lastSpike = currentTime)
    else stay using data.copy(potential = potential, lastEvent = currentTime)
  }

  protected def spiking(signal: Signal, data: NeuronData): FSM.State[NeuronState, NeuronData] = {
    val currentTime = now

    val potential = math.min(spikingThreshold, data.potential * math.exp(-(now - data.lastEvent) / decayHalfLife.toDouble) + signal.strength)

    if (potential >= restingThreshold) {
      data.postSynaptic.foreach(_._2 ! Signal(self, 1, signal.id))
      timers.startSingleTimer(TonicFire, Signal(self, 0, signal.id), interval millis)
      println(s"event(${self.path.name}); state: $stateName; source: ${if (signal.preSynaptic != null) signal.preSynaptic.path.name else "[world]"}; potential: ${data.potential}; spike: ${now - data.lastSpike}; elapsed: ${data.lastEvent - start}")
      stay using data.copy(potential = potential, lastEvent = currentTime)
    } else if (currentTime < data.lastSpike + refractory) {
      goto(Refractory) using data.copy(potential = restingPotential)
    } else {
      goto(Depolarized) using data.copy(potential = restingPotential + signal.strength, lastEvent = currentTime)
    }
  }

  protected def refractory(signal: Signal, data: NeuronData): FSM.State[NeuronState, NeuronData] = {
    val currentTime = now
    println(s"event(${self.path.name}); state: $stateName; source: ${if (signal.preSynaptic != null) signal.preSynaptic.path.name else "[world]"}; potential: ${data.potential}; spike: ${now - data.lastSpike}; elapsed: ${data.lastEvent - start}")
    if (currentTime < data.lastSpike + refractory) stay using data.copy(lastEvent = currentTime)
    else goto(Depolarized) using data.copy(lastEvent = currentTime)
  }
}

object BiStableIntegrator {

  private case object TonicFire

  val system = ActorSystem()

  def main(args: Array[String]): Unit = {
    val n11 = system.actorOf(Props(new MonoStableIntegrator(restingPotential = 0, threshold = 5, refractory = 20)), name = "input")
    val n2s = for (i <- 1 to 3) yield
      system.actorOf(Props(new BiStableIntegrator(
        restingPotential = 0,
        spikingThreshold = 10,
        restingThreshold = 6,
        refractory = 50,
        interval = 20,
        decayHalfLife = 750 // membrane potential decay half-life
      )), name = s"output-$i")

    // connect the neurons in the second layer to the neurons in the first layer
    n2s.foreach(neuron => n11 ! Connect(neuron, 0))

    val inbox: Inbox = Inbox.create(system)
    for (i <- 1 to 150) {
      inbox.send(n11, Signal(Actor.noSender, 1, i))
      System.out.flush()
      if (i % 11 == 0) Thread.sleep(100) else Thread.sleep(5)
    }

    Thread.sleep(4000)
    system.terminate()
  }
}