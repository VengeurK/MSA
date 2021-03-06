package edu.usc.thevillagers.serversideagent.env;

import edu.usc.thevillagers.serversideagent.env.actuator.ActuatorJump;
import edu.usc.thevillagers.serversideagent.env.allocation.AllocatorEmptySpace;
import net.minecraft.block.BlockColored;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public class EnvironmentParkourJump extends EnvironmentParkour {
	
	@Override
	public void initialize(float[] pars) {
		super.initialize(pars);
		allocator = new AllocatorEmptySpace(new BlockPos(-width-1, -1, -2), new BlockPos(width+1, 2, length+1));
	}

	@Override
	protected void buildActuators() {
		super.buildActuators();
		actuators.add(new ActuatorJump());
	}
	
	@Override
	public void reset() {
		super.reset();
		generate();
	}
	
	private void generate() {
		for(int z = 1; z <= length; z++) {
			for(int x =- width; x <= width; x++) {
				world.setBlockState(ref.add(x, -1, z), Blocks.AIR.getDefaultState());
			}
		}
		BlockPos pos = ref.add(0, -1, 1);
		int blocks = length + world.rand.nextInt(2 * width);
		EnumFacing[] dirs = new EnumFacing[] {EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH};
		for(int i = 0; i < blocks; i ++) {
			if(i % 2 == 0) world.setBlockState(pos, Blocks.STAINED_GLASS.getDefaultState());
			EnumFacing dir = dirs[world.rand.nextInt(dirs.length)];
			BlockPos p = pos.offset(dir);
			if(p.getX() >= ref.getX() - width && p.getX() <= ref.getX() + width && p.getZ() > ref.getZ() && p.getZ() <= ref.getZ() + length)
				pos = p;
		}
		world.setBlockState(pos, Blocks.STAINED_GLASS.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.LIME));
	}
}
