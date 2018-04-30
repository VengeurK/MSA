package edu.usc.thevillagers.serversideagent.env;

import edu.usc.thevillagers.serversideagent.agent.Actor;
import edu.usc.thevillagers.serversideagent.agent.Agent;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

public class EnvironmentFollow extends Environment {
	
	private Entity target;

	public EnvironmentFollow() {
		super(2, 2);
	}
	
	@Override
	public void init(WorldServer world) {
		super.init(world);
		BlockPos ref = getOrigin();
		target = world.getClosestPlayer(ref.getX(), ref.getY(), ref.getZ(), 50, false);
	}
	
	@Override
	public void newActor(Actor a) {
		super.newActor(a);
		done = true;
	}
	
	@Override
	public void encodeObservation(Agent agent, float[] stateVector) {
		stateVector[0] = (float) (target.posX - agent.entity.posX) / 5F;
		stateVector[1] = (float) (target.posZ - agent.entity.posZ) / 5F;
	}

	@Override
	public void decodeAction(Agent agent, float[] actionVector) {
		agent.actionState.forward = actionVector[0];
		agent.actionState.strafe = actionVector[1];
	}

	@Override
	protected void stepActor(Actor actor) throws Exception {
		float dx = (float) (target.posX - actor.entity.posX) / 5F;
		float dz = (float) (target.posZ - actor.entity.posZ) / 5F;
		actor.reward = - (dx*dx + dz*dz);
	}
}
