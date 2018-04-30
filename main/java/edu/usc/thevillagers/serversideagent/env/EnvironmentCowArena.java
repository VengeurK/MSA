package edu.usc.thevillagers.serversideagent.env;

import edu.usc.thevillagers.serversideagent.HighLevelAction;
import edu.usc.thevillagers.serversideagent.HighLevelAction.Phase;
import edu.usc.thevillagers.serversideagent.HighLevelAction.Type;
import edu.usc.thevillagers.serversideagent.agent.Actor;
import edu.usc.thevillagers.serversideagent.env.actuator.ActuatorForwardStrafe;
import edu.usc.thevillagers.serversideagent.env.allocation.AllocatorEmptySpace;
import edu.usc.thevillagers.serversideagent.env.sensor.SensorBoolean;
import edu.usc.thevillagers.serversideagent.env.sensor.SensorPosition;
import net.minecraft.block.BlockColored;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

public class EnvironmentCowArena extends Environment {

	public final int size;

	protected BlockPos ref;

	private EntityCow[] cows;
	
	public EnvironmentCowArena() {
		this(5, 5);
	}

	public EnvironmentCowArena(int size, int nCows) {
		this.size = size;
		cows = new EntityCow[nCows];
		allocator = new AllocatorEmptySpace(new BlockPos(-size, -1, -size), new BlockPos(size, 2, size));
	}
	
	@Override
	protected void buildSensors() {
		sensors.add(new SensorPosition(size, 0, size, 
				(a) -> a.entity.getPositionVector().subtract(new Vec3d(ref))));
		for(int i = 0; i < cows.length; i++) {
			final int index = i;
			sensors.add(new SensorPosition(size, 0, size, 
					(a) -> cows[index].getPositionVector().subtract(a.entity.getPositionVector())));
			sensors.add(new SensorBoolean(() -> cows[index].getHealth() > 0));
		}
	}
	
	@Override
	protected void buildActuators() {
		actuators.add(new ActuatorForwardStrafe());
	}
	
	@Override
	public void newActor(Actor a) {
		super.newActor(a);
	}
	
	@Override
	public void init(WorldServer world) {
		super.init(world);
		ref = getOrigin();
	}

	@Override
	protected void stepActor(Actor actor) throws Exception {
		actor.reward = 0;
		actor.actionState.action = null;
		for(int i = 0; i < cows.length; i++) {
			actor.reward -= cows[i].getHealth();
			if(cows[i].getHealth() > 0) {
				if(cows[i].getDistanceSq(actor.entity) < 1) {
					actor.actionState.action = new HighLevelAction(Type.HIT, Phase.INSTANT, actor.entity.getEntityId(), 
							EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD), cows[i].getEntityId(), null, null, null);
					actor.reward += 5;
				}
			}
		}
		if(time >= 49) done = true;
	}
	
	@Override
	public void reset() {
		super.reset();
		for(Entity e : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(ref.add(-size, -1, -size), ref.add(size, +2, size))))
			if(!(e instanceof EntityLivingBase)) e.setDead();
		generate();
		for(int i = 0; i < cows.length; i++) {
			if(cows[i] != null)
				cows[i].setDead();
			cows[i] = new EntityCow(world);
			cows[i].setPosition(ref.getX() - size + world.rand.nextInt(2 * size - 3) + 2, ref.getY(), 
								ref.getZ() - size + world.rand.nextInt(2 * size - 3) + 2);
			cows[i].setHealth(.1F);
			cows[i].setDropItemsWhenDead(false);
			world.spawnEntity(cows[i]);
		}
	}
	
	private void generate() {
		for(int z =- size; z <= size; z++) {
			for(int x =- size; x <= size; x++) {
				world.setBlockState(ref.add(x, -1, z), Blocks.STAINED_GLASS.getDefaultState());
				if(Math.max(Math.abs(x), Math.abs(z)) == size)
						world.setBlockState(ref.add(x, 1, z), Blocks.STAINED_GLASS.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.BLACK));
			}
		}
	}
}