package edu.usc.thevillagers.serversideagent.recording.event;

import edu.usc.thevillagers.serversideagent.recording.WorldRecordRecorder;
import edu.usc.thevillagers.serversideagent.recording.WorldRecordReplayer;
import edu.usc.thevillagers.serversideagent.recording.WorldRecordWorker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * An event that can be recorded by {@link WorldRecordRecorder} and replayed by {@link WorldRecordReplayer}
 */
public abstract class RecordEvent {

	public abstract void replay(WorldRecordReplayer wr);
	public abstract void write(NBTTagCompound compound);
	public abstract void read(NBTTagCompound compound);
	public abstract boolean isWithinBounds(WorldRecordWorker record, AxisAlignedBB bounds);
	
	private static final Class<?>[] classes = new Class<?>[] {
		RecordEventBlockChange.class,
		RecordEventEntitySpawn.class,
		RecordEventEntityUpdate.class,
		RecordEventEntityDie.class,
		RecordEventTileEntitySpawn.class,
		RecordEventTileEntityUpdate.class,
		RecordEventTileEntityDie.class,
		RecordEventAction.class,
	};
	
	private static Class<?> getClassFromId(int id) {
		if(id < 0 || id >= classes.length) throw new IllegalArgumentException("Unknown event id "+id);
		return classes[id];
	}
	
	private static int getClassId(Class<?> c) {
		for(int id = 0; id < classes.length; id++)
			if(classes[id] == c)
				return id;
		throw new IllegalArgumentException("Unknown event class "+c);
	}
	
	private static RecordEvent instantiate(int id) {
		try {
			return (RecordEvent) getClassFromId(id).newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new IllegalArgumentException("Can't instantiate event with id "+id, e);
		}
	}
	
	public static NBTTagCompound toNBT(RecordEvent event) {
		NBTTagCompound comp = new NBTTagCompound();
		event.write(comp);
		comp.setInteger("RecordEventId", RecordEvent.getClassId(event.getClass()));
		return comp;
	}
	
	public static RecordEvent fromNBT(NBTTagCompound comp) {
		RecordEvent event = RecordEvent.instantiate(comp.getInteger("RecordEventId"));
		event.read(comp);
		return event;
	}
}
