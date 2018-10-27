# Toy Spiking Neurons using Akka FSM
Toy spiking monostable and bistable integrator neurons for testing Akka FSM DSL. Goal to use this 
approach in Spikes (current private repo).

## Monostable Integrator
In this toy model, monostable integrator neurons are modelled to spike once when they are in the depolarized
state and the membrane potential reaches or exceeds a threshold. Once spiked, they move to a refractory period, 
and then after that back to the depolarized state.

The FSM implementation has two states: `Depolarized` and `Refractory`. The spiking occurs in the transition
between these to states. The `Refactory` state has a timeout that occurs when the refractory period is
over.

## Bistable Integrator
The bistable integrator is modelled as moving from the depolarized state into a spiking state when the membrane
potential reaches or exceeds the spike-threshold. The neuron continues to spike until the membrane potential decays
to or below the resting-threshold, at which point the neuron transitions back to the depolarized state.
While in the spiking state, incoming signals are added to the membrane potential.

The FSM implementation has three states: `Depolarized`, `Spiking`, and `Refractory`. In the spiking state, a
timer is used to initiate the neuron to send a signal to itself, which happens until the membrane potential
decays. Note that these "self-signals" have a signal strength of 0 and do not affect the membrane potential. 
