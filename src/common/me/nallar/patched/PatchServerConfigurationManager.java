package me.nallar.patched;

import cpw.mods.fml.common.registry.GameRegistry;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.concurrent.TwoWayReentrantReadWriteLock;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet41EntityEffect;
import net.minecraft.network.packet.Packet9Respawn;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public abstract class PatchServerConfigurationManager extends ServerConfigurationManager {
	@Declare
	public java.util.concurrent.locks.Lock playerUpdateLock_;
	@Declare
	public java.util.concurrent.locks.Lock playersUpdateLock_;

	public void construct() {
		TwoWayReentrantReadWriteLock reentrantReadWriteLock = new TwoWayReentrantReadWriteLock();
		playersUpdateLock = reentrantReadWriteLock.writeLock();
		playerUpdateLock = reentrantReadWriteLock.readLock();
	}

	@Override
	public void readPlayerDataFromFile(EntityPlayerMP par1EntityPlayerMP) {
		NBTTagCompound var2 = this.mcServer.worldServers[0].getWorldInfo().getPlayerNBTTagCompound();

		if (par1EntityPlayerMP.getCommandSenderName().equals(this.mcServer.getServerOwner()) && var2 != null) {
			par1EntityPlayerMP.readFromNBT(var2);
		} else {
			this.playerNBTManagerObj.readPlayerData(par1EntityPlayerMP);
		}
		par1EntityPlayerMP.posY += 0.05;
	}

	@Override
	public void transferPlayerToDimension(EntityPlayerMP entityPlayerMP, int toDimensionId, Teleporter teleporter) {
		synchronized (entityPlayerMP.loadedChunks) {
			int fromDimensionId = entityPlayerMP.dimension;
			WorldServer fromDimension = this.mcServer.worldServerForDimension(fromDimensionId);
			entityPlayerMP.dimension = toDimensionId;
			WorldServer toDimension = this.mcServer.worldServerForDimension(toDimensionId);
			Log.info(entityPlayerMP + " from " + Log.name(fromDimension) + " to " + Log.name(toDimension));
			if (fromDimension == toDimension) {
				if (!toDimension.playerEntities.contains(entityPlayerMP)) {
					toDimension.spawnEntityInWorld(entityPlayerMP);
				}
				Log.severe("Can't transfer player to the dimension they are already in! " + entityPlayerMP + ", dimension: " + Log.name(toDimension));
				return;
			}
			entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(new Packet9Respawn(entityPlayerMP.dimension, (byte) entityPlayerMP.worldObj.difficultySetting, toDimension.getWorldInfo().getTerrainType(), toDimension.getHeight(), entityPlayerMP.theItemInWorldManager.getGameType()));
			fromDimension.playerEntities.remove(entityPlayerMP);
			fromDimension.loadedEntityList.remove(entityPlayerMP);
			fromDimension.releaseEntitySkin(entityPlayerMP);
			fromDimension.updateAllPlayersSleepingFlag();
			int x = entityPlayerMP.chunkCoordX;
			int z = entityPlayerMP.chunkCoordZ;
			if (entityPlayerMP.addedToChunk && fromDimension.getChunkProvider().chunkExists(x, z)) {
				fromDimension.getChunkFromChunkCoords(x, z).removeEntity(entityPlayerMP);
			}
			entityPlayerMP.setWorld(toDimension);
			this.func_72375_a(entityPlayerMP, fromDimension);
			if (!toDimension.playerEntities.contains(entityPlayerMP)) {
				toDimension.spawnEntityInWorld(entityPlayerMP);
			}
			entityPlayerMP.playerNetServerHandler.setPlayerLocation(entityPlayerMP.posX, entityPlayerMP.posY, entityPlayerMP.posZ, entityPlayerMP.rotationYaw, entityPlayerMP.rotationPitch);
			entityPlayerMP.theItemInWorldManager.setWorld(toDimension);
			this.updateTimeAndWeatherForPlayer(entityPlayerMP, toDimension);
			this.syncPlayerInventory(entityPlayerMP);

			for (PotionEffect potionEffect : (Iterable<PotionEffect>) entityPlayerMP.getActivePotionEffects()) {
				entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(new Packet41EntityEffect(entityPlayerMP.entityId, potionEffect));
			}

			GameRegistry.onPlayerChangedDimension(entityPlayerMP);
		}
	}

	public PatchServerConfigurationManager(MinecraftServer par1MinecraftServer) {
		super(par1MinecraftServer);
	}
}
