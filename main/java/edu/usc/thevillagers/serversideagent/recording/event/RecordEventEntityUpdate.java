package edu.usc.thevillagers.serversideagent.recording.event;

import edu.usc.thevillagers.serversideagent.recording.WorldRecordRecorder;
import edu.usc.thevillagers.serversideagent.recording.WorldRecordReplayer;
import edu.usc.thevillagers.serversideagent.recording.WorldRecordWorker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Record of change among an entity's data. 
 * Uses the way entity are stored when saving the world to compute this data.
 */
public class RecordEventEntityUpdate extends RecordEvent {
	
	private int id;
	private NBTTagCompound data;
	
	public RecordEventEntityUpdate() {
	}
	
	public RecordEventEntityUpdate(int id, NBTTagCompound data) {
		this.id = id;
		this.data = data;
	}

	@Override
	public void replay(WorldRecordReplayer wr) {
		if(wr.entitiesData.containsKey(id)) WorldRecordRecorder.updateCompound(wr.entitiesData.get(id), data);
		else System.err.println("Missing entity with id: "+id);
	}

	@Override
	public void write(NBTTagCompound compound) {
		compound.setInteger("Id", id);
		compound.setTag("Data", data);
	}

	@Override
	public void read(NBTTagCompound compound) {
		id = compound.getInteger("Id");
		data = compound.getCompoundTag("Data");
	}

	@Override
	public boolean isWithinBounds(WorldRecordWorker record, AxisAlignedBB bounds) {
		return true;
	}
}
