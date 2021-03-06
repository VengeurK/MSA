package edu.usc.thevillagers.serversideagent.recording;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.mojang.authlib.GameProfile;

import edu.usc.thevillagers.serversideagent.ServerSideAgentMod;
import edu.usc.thevillagers.serversideagent.agent.DummyNetworkManager;
import edu.usc.thevillagers.serversideagent.recording.event.RecordEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.block.BlockDirectional;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Replays a world record from the disk.
 */
public class WorldRecordReplayer extends WorldRecordWorker {
	
	public static final int DUMMY_DIMENSION = 0xDEAD;
	
	public long worldTimeOffset;
	
	private Future<ChangeSet> nextChangeSet;
	public Set<ActionListener> actionListeners = new HashSet<>();
	
	public Map<Integer, Integer> idMapping = new HashMap<>();
	
	public BlockPos offset;
	
	public WorldRecordReplayer(File saveFolder) {
		this.world = null;
		this.saveFolder = saveFolder;
		this.offset = BlockPos.ORIGIN;
	}
	
	@Override
	public void readInfo() throws IOException {
		super.readInfo();
	}
	
	protected World createWorld() {
		WorldSettings settings = new WorldSettings(0, GameType.NOT_SET, false, false, WorldType.FLAT);
		WorldInfo info = new WorldInfo(settings, "dummy");
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		WorldServer world = new WorldServer(server, server.getActiveAnvilConverter().getSaveLoader("dummy", false),
				info, DUMMY_DIMENSION, server.profiler); //TODO custom ISaveHandler
		DimensionManager.setWorld(DUMMY_DIMENSION, null, server);
		return world;
	}
	
	public void endReplayTick() throws InterruptedException, ExecutionException {
		if(currentTick >= duration) return;
		int phase = currentTick % snapshotLength;
		if(phase == 0) {
			currentChangeSet = nextChangeSet.get();
			if(currentTick + snapshotLength < duration) {
				ChangeSet changeSet = changeSet(currentTick + snapshotLength);
				nextChangeSet = ioExecutor.submit(() -> {
					if(!changeSet.hasData()) changeSet.read();
					return changeSet;
				});
			}
		}
		for(RecordEvent event : currentChangeSet.data.get(phase))
			event.replay(this);
		for(Entry<Integer, NBTTagCompound> entry : entitiesData.entrySet())
			updateEntity(entry.getKey(), entry.getValue());
		for(Entry<BlockPos, NBTTagCompound> entry : tileEntitiesData.entrySet())
			updateTileEntity(entry.getKey(), entry.getValue());
		currentTick++;
		for(Entity e : world.loadedEntityList)
			world.updateEntityWithOptionalForce(e, false);
		world.setWorldTime((worldTimeOffset + currentTick) % 24000);
	}
	
	public void seek(int tick) throws IOException, InterruptedException, ExecutionException {
		reset();
		long start = System.currentTimeMillis();
		
		if(tick >= duration) tick = duration - 1;
		currentTick = tick - (tick % snapshotLength);
		
		Snapshot snapshot = snapshot(tick);
		if(!snapshot.hasData()) snapshot.read(); 
		
		ChangeSet changeSet = changeSet(currentTick);
		nextChangeSet = ioExecutor.submit(() -> {
			if(!changeSet.hasData()) changeSet.read();
			return changeSet;
		});
		
		long ioEnd = System.currentTimeMillis();
		
		snapshot.applyDataToWorld(this);
		long appSnapEnd = System.currentTimeMillis();
		
		currentChangeSet = null;
		while(currentTick < tick)
			endReplayTick();
		
		long end = System.currentTimeMillis();
		System.out.println(String.format("SEEK(%d) in %d ms [io = %d ms | snap = %d ms | changes = %d ms]", tick, end - start, ioEnd - start, appSnapEnd - ioEnd, end - appSnapEnd));
	}
	
	public Long2ObjectMap<Chunk> getChunkMapping() {
		return ServerSideAgentMod.getPrivateField(ChunkProviderServer.class, "id2ChunkMap", world.getChunkProvider());
	}
	
	public void spawnEntity(int id, Entity e) {
		idMapping.put(id, e.getEntityId());
		world.spawnEntity(e);
	}

	public void killEntity(int id) {
		Entity e = world.getEntityByID(idMapping.remove(id));
		if(e == null) {
			System.out.println("Missing entity "+id);
			return;
		}
		world.removeEntityDangerously(e);
	}

	public void updateEntity(int id, NBTTagCompound data) {
		Entity e = world.getEntityByID(idMapping.get(id));
		if(e == null) {
			System.err.println("Missing entity "+id);
			return;
		}
		e.readFromNBT(data);
		e.setPosition(e.posX + offset.getX(), e.posY + offset.getY(), e.posZ + offset.getZ());
		if(e instanceof EntityPlayer) {
			e.setSneaking(data.getBoolean("Sneaking"));
		}
	}

	public Entity getEntity(int id) {
		return world.getEntityByID(idMapping.get(id));
	}

	public void spawnTileEntity(TileEntity tileEntity) {
		tileEntity.setPos(tileEntity.getPos().add(offset));
		if(tileEntity instanceof TileEntityPiston) {
			TileEntityPiston piston = (TileEntityPiston) tileEntity;
			world.setBlockState(piston.getPos(), 
					Blocks.PISTON_EXTENSION.getDefaultState().withProperty(BlockDirectional.FACING, piston.getFacing()), 4);
		}
		world.setTileEntity(tileEntity.getPos(), tileEntity);
	}

	public void killTileEntity(BlockPos pos) {
		pos = pos.add(offset);
		world.setTileEntity(pos, null);
	}

	public void updateTileEntity(BlockPos pos, NBTTagCompound data) {
		pos = pos.add(offset);
		TileEntity tileEntity = world.getTileEntity(pos);
		if(tileEntity == null) {
			tileEntity = TileEntity.create(world, data);
			tileEntity.setWorld(world);
			tileEntity.setPos(pos);
			spawnTileEntity(tileEntity);
		}
		tileEntity.readFromNBT(data);
	}
	
	public EntityPlayer createReplayEntityPlayer(World world, GameProfile profile) {
		EntityPlayerMP player = new EntityPlayerMP(FMLCommonHandler.instance().getMinecraftServerInstance(), 
				(WorldServer) world, profile, new PlayerInteractionManager(world));
		player.connection = new NetHandlerPlayServer(world.getMinecraftServer(), new DummyNetworkManager(), player);
		return player;
	}

	public void reset() {
		world = createWorld();
		entitiesData.clear();
		tileEntitiesData.clear();
	}
}
