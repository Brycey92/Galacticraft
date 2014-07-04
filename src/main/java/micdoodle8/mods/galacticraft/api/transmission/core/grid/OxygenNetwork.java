package micdoodle8.mods.galacticraft.api.transmission.core.grid;

import cpw.mods.fml.common.FMLLog;
import micdoodle8.mods.galacticraft.api.transmission.NetworkType;
import micdoodle8.mods.galacticraft.api.transmission.core.path.Pathfinder;
import micdoodle8.mods.galacticraft.api.transmission.core.path.PathfinderChecker;
import micdoodle8.mods.galacticraft.api.transmission.tile.*;
import micdoodle8.mods.galacticraft.api.vector.BlockVec3;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.*;

/**
 * An Oxygen Network specifies a wire connection. Each wire connection line will
 * have its own oxygen network.
 * 
 * !! Do not include this class if you do not intend to have custom wires in
 * your mod. This will increase future compatibility. !!
 * 
 * @author Calclavia
 * 
 */
public class OxygenNetwork implements IOxygenNetwork
{
	public Map<TileEntity, ForgeDirection> oxygenTiles = new HashMap<TileEntity, ForgeDirection>();

	private final Set<ITransmitter> pipes = new HashSet<ITransmitter>();

	@Override
	public float produce(float totalOxygen, TileEntity... ignoreTiles)
	{
		float remainingUsableOxygen = totalOxygen;

		if (!this.oxygenTiles.isEmpty())
		{
			final float totalOxygenRequest = this.getRequest(ignoreTiles);

			if (totalOxygenRequest > 0)
			{
				List<TileEntity> ignoreTilesList = Arrays.asList(ignoreTiles);
				for (TileEntity tileEntity : new HashSet<TileEntity>(this.oxygenTiles.keySet()))
				{
					if (!ignoreTilesList.contains(tileEntity))
					{
						if (tileEntity instanceof IOxygenReceiver)
						{
							IOxygenReceiver oxygenTile = (IOxygenReceiver) tileEntity;

							if (oxygenTile.shouldPullOxygen())
							{
								for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
								{
									TileEntity tile = new BlockVec3(tileEntity).modifyPositionFromSide(direction, 1).getTileEntity(tileEntity.getWorldObj());

									if (oxygenTile.canConnect(direction, NetworkType.OXYGEN) && this.pipes.contains(tile))
									{
										float oxygenToSend = Math.max(totalOxygen, totalOxygen * (oxygenTile.getOxygenRequest(direction) / totalOxygenRequest));

										if (oxygenToSend > 0)
										{
											remainingUsableOxygen -= oxygenTile.receiveOxygen(direction, oxygenToSend, true);
										}
									}
								}
							}
						}
						//						else if (NetworkConfigHandler.isMekanismV6Loaded() && tileEntity instanceof IGasHandler)
						//						{
						//							IGasHandler gasHandler = (IGasHandler) tileEntity;
						//
						//							for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
						//							{
						//								TileEntity tile = new Vector3(tileEntity).modifyPositionFromSide(direction).getTileEntity(tileEntity.worldObj);
						//
						//								if (gasHandler.canReceiveGas(direction, (Gas) NetworkConfigHandler.gasOxygen) && this.getTransmitters().contains(tile))
						//								{
						//									int oxygenToSend = (int) Math.floor(totalOxygen / this.oxygenTiles.size());
						//
						//									if (oxygenToSend > 0)
						//									{
						//										remainingUsableOxygen -= gasHandler.receiveGas(direction, (new GasStack((Gas) NetworkConfigHandler.gasOxygen, oxygenToSend)));
						//									}
						//								}
						//							}
						//						}
						//						else if (NetworkConfigHandler.isMekanismLoaded() && tileEntity instanceof IGasAcceptor)
						//						{
						//							IGasAcceptor gasAcceptor = (IGasAcceptor) tileEntity;
						//
						//							for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
						//							{
						//								TileEntity tile = new Vector3(tileEntity).modifyPositionFromSide(direction).getTileEntity(tileEntity.worldObj);
						//
						//								if (gasAcceptor.canReceiveGas(direction, (Gas) NetworkConfigHandler.gasOxygen) && this.getTransmitters().contains(tile))
						//								{
						//									int oxygenToSend = (int) Math.floor(totalOxygen / this.oxygenTiles.size());
						//
						//									if (oxygenToSend > 0)
						//									{
						//										remainingUsableOxygen -= gasAcceptor.receiveGas(new GasStack((Gas) NetworkConfigHandler.gasOxygen, oxygenToSend));
						//									}
						//								}
						//							}
						//						}
					}
				}
			}
		}

		return remainingUsableOxygen;
	}

	/**
	 * @return How much oxygen this network needs.
	 */
	@Override
	public float getRequest(TileEntity... ignoreTiles)
	{
		List<Float> requests = new ArrayList<Float>();

		List<TileEntity> ignoreTilesList = Arrays.asList(ignoreTiles);
		for (TileEntity tileEntity : new HashSet<TileEntity>(this.oxygenTiles.keySet()))
		{
			if (ignoreTilesList.contains(tileEntity))
			{
				continue;
			}

			if (tileEntity instanceof IOxygenReceiver && ((IOxygenReceiver) tileEntity).shouldPullOxygen())
			{
				if (!tileEntity.isInvalid())
				{
					if (tileEntity.getWorldObj().getTileEntity(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord) == tileEntity)
					{
						for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
						{
							BlockVec3 tileVec = new BlockVec3(tileEntity);
							TileEntity tile = tileVec.modifyPositionFromSide(direction, 1).getTileEntity(tileEntity.getWorldObj());

							if (((IOxygenReceiver) tileEntity).canConnect(direction, NetworkType.OXYGEN) && this.pipes.contains(tile))
							{
								requests.add(((IOxygenReceiver) tileEntity).getOxygenRequest(direction));
                            }
						}
					}
				}
			}
		}

		float total = 0.0F;

		for (Float f : requests)
		{
			total += f;
		}

		return total;
	}

	/**
	 * @return Returns all producers in this oxygen network.
	 */
	@Override
	public Set<TileEntity> getAcceptors()
	{
		return this.oxygenTiles.keySet();
	}

	/**
	 * @param tile
	 *            The tile to get connections for
	 * @return The list of directions that can be connected to for the provided
	 *         tile
	 */
	@Override
	public ForgeDirection getPossibleDirections(TileEntity tile)
	{
		return this.oxygenTiles.containsKey(tile) ? this.oxygenTiles.get(tile) : null;
	}

	/**
	 * This function is called to refresh all conductors in this network
	 */
	@Override
	public void refresh()
	{
		this.oxygenTiles.clear();

		try
		{
			Iterator<ITransmitter> it = this.pipes.iterator();

			while (it.hasNext())
			{
				ITransmitter transmitter = it.next();

				if (transmitter == null)
				{
					it.remove();
					continue;
				}

				transmitter.onNetworkChanged();

				if (((TileEntity) transmitter).isInvalid() || ((TileEntity) transmitter).getWorldObj() == null)
				{
					it.remove();
					continue;
				}
				else if (((TileEntity) transmitter).getWorldObj().getTileEntity(((TileEntity) transmitter).xCoord, ((TileEntity) transmitter).yCoord, ((TileEntity) transmitter).zCoord) != transmitter)
				{
					it.remove();
					continue;
				}
				else
				{
					transmitter.setNetwork(this);
				}

				for (int i = 0; i < transmitter.getAdjacentConnections().length; i++)
				{
					TileEntity acceptor = transmitter.getAdjacentConnections()[i];

					if (!(acceptor instanceof ITransmitter) && acceptor instanceof IConnector)
					{
						this.oxygenTiles.put(acceptor, ForgeDirection.getOrientation(i));
					}
				}
			}
		}
		catch (Exception e)
		{
			FMLLog.severe("Failed to refresh oxygen pipe network.");
			e.printStackTrace();
		}
	}

	@Override
	public Set<ITransmitter> getTransmitters()
	{
		return this.pipes;
	}

	@Override
	public IOxygenNetwork merge(IOxygenNetwork network)
	{
		if (network != null && network != this)
		{
			OxygenNetwork newNetwork = new OxygenNetwork();
			newNetwork.pipes.addAll(this.pipes);
			newNetwork.pipes.addAll(network.getTransmitters());
			newNetwork.refresh();
			return newNetwork;
		}

		return this;
	}

	@Override
	public void split(ITransmitter splitPoint)
	{
		if (splitPoint instanceof TileEntity)
		{
			this.pipes.remove(splitPoint);

			/**
			 * Loop through the connected blocks and attempt to see if there are
			 * connections between the two points elsewhere.
			 */
			TileEntity[] connectedBlocks = splitPoint.getAdjacentConnections();

			for (TileEntity connectedBlockA : connectedBlocks)
			{
				if (connectedBlockA instanceof INetworkConnection)
				{
					for (final TileEntity connectedBlockB : connectedBlocks)
					{
						if (connectedBlockA != connectedBlockB && connectedBlockB instanceof INetworkConnection)
						{
							Pathfinder finder = new PathfinderChecker(((TileEntity) splitPoint).getWorldObj(), (INetworkConnection) connectedBlockB, NetworkType.OXYGEN, splitPoint);
							finder.init(new BlockVec3(connectedBlockA));

							if (finder.results.size() > 0)
							{
								/**
								 * The connections A and B are still intact
								 * elsewhere. Set all references of wire
								 * connection into one network.
								 */

								for (BlockVec3 node : finder.closedSet)
								{
									TileEntity nodeTile = node.getTileEntity(((TileEntity) splitPoint).getWorldObj());

									if (nodeTile instanceof INetworkProvider)
									{
										if (nodeTile != splitPoint)
										{
											((INetworkProvider) nodeTile).setNetwork(this);
										}
									}
								}
							}
							else
							{
								/**
								 * The connections A and B are not connected
								 * anymore. Give both of them a new network.
								 */
								IOxygenNetwork newNetwork = new OxygenNetwork();

								for (BlockVec3 node : finder.closedSet)
								{
									TileEntity nodeTile = node.getTileEntity(((TileEntity) splitPoint).getWorldObj());

									if (nodeTile instanceof INetworkProvider)
									{
										if (nodeTile != splitPoint)
										{
											newNetwork.getTransmitters().add((ITransmitter) nodeTile);
										}
									}
								}

								newNetwork.refresh();
							}
						}
					}
				}
			}
		}
	}

	@Override
	public String toString()
	{
		return "OxygenNetwork[" + this.hashCode() + "|Pipes:" + this.pipes.size() + "|Acceptors:" + this.oxygenTiles.size() + "]";
	}
}
