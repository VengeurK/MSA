package edu.usc.thevillagers.serversideagent.env.actuator;

import edu.usc.thevillagers.serversideagent.agent.Agent;

/**
 * Actuator with no effect.
 */
public class ActuatorDummy extends Actuator {

	public ActuatorDummy(int dim) {
		super(dim);
	}

	@Override
	public void act(Agent agent) {
	}
}
