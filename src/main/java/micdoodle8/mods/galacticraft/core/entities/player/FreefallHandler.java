package micdoodle8.mods.galacticraft.core.entities.player;

import micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase;
import micdoodle8.mods.galacticraft.core.GCBlocks;
import micdoodle8.mods.galacticraft.core.dimension.SpinManager;
import micdoodle8.mods.galacticraft.core.dimension.WorldProviderZeroGravity;
import micdoodle8.mods.galacticraft.core.entities.EntityLanderBase;
import micdoodle8.mods.galacticraft.core.util.ConfigManagerCore;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class FreefallHandler
{
    private double pPrevMotionX;
    public double pPrevMotionY;
    private double pPrevMotionZ;
    private float jetpackBoost;
    private double pPrevdY;
    public boolean sneakLast;

    private int pjumpticks = 0;

    public boolean testFreefall(EntityPlayer player)
    {
        //Test whether feet are on a block, also stops the login glitch
        int playerFeetOnY = (int) (player.getEntityBoundingBox().minY - 0.01D);
        int xx = MathHelper.floor_double(player.posX);
        int zz = MathHelper.floor_double(player.posZ);
        BlockPos pos = new BlockPos(xx, playerFeetOnY, zz);
        IBlockState state = player.worldObj.getBlockState(pos);
        Block b = state.getBlock();
        if (b.getMaterial() != Material.air && !(b instanceof BlockLiquid))
        {
            double blockYmax = playerFeetOnY + b.getBlockBoundsMaxY();
            if (player.getEntityBoundingBox().minY - blockYmax < 0.01D && player.getEntityBoundingBox().minY - blockYmax > -0.5D)
            {
                player.onGround = true;
                if (player.getEntityBoundingBox().minY - blockYmax > 0D)
                {
                    player.posY -= player.getEntityBoundingBox().minY - blockYmax;
                    player.getEntityBoundingBox().offset(0, blockYmax - player.getEntityBoundingBox().minY, 0);
                }
                else if (b.canCollideCheck(player.worldObj.getBlockState(new BlockPos(xx, playerFeetOnY, zz)), false))
                {
                    AxisAlignedBB collisionBox = b.getCollisionBoundingBox(player.worldObj, new BlockPos(xx, playerFeetOnY, zz), state);
                    if (collisionBox != null && collisionBox.intersectsWith(player.getEntityBoundingBox()))
                    {
                        player.posY -= player.getEntityBoundingBox().minY - blockYmax;
                        player.getEntityBoundingBox().offset(0, blockYmax - player.getEntityBoundingBox().minY, 0);
                    }
                }
                return false;
            }
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    private boolean testFreefall(EntityPlayerSP p, boolean flag)
    {
        World world = p.worldObj;
        WorldProvider worldProvider = world.provider;
        if (!(worldProvider instanceof WorldProviderZeroGravity))
        {
            return false;
        }
        WorldProviderZeroGravity worldProviderOrbit = (WorldProviderZeroGravity) worldProvider;
        SpinManager spinManager = worldProviderOrbit.getSpinManager();
        GCPlayerStatsClient stats = GCPlayerStatsClient.get(p);
        if (this.pjumpticks > 0 || (stats.ssOnGroundLast && p.movementInput.jump))
        {
            return false;
        }

        if (p.ridingEntity != null)
        {
            Entity e = p.ridingEntity;
            if (e instanceof EntitySpaceshipBase)
            {
                return ((EntitySpaceshipBase) e).getLaunched();
            }
            if (e instanceof EntityLanderBase)
            {
                return false;
            }
            //TODO: should check whether lander has landed (whatever that means)
            //TODO: could check other ridden entities - every entity should have its own freefall check :(
        }

        //This is an "on the ground" check
        if (!flag)
        {
            return false;
        }
        else
        {
            float rY = p.rotationYaw % 360F;
            double zreach = 0D;
            double xreach = 0D;
            if (rY < 80F || rY > 280F)
            {
                zreach = 0.2D;
            }
            if (rY < 170F && rY > 10F)
            {
                xreach = 0.2D;
            }
            if (rY < 260F && rY > 100F)
            {
                zreach = -0.2D;
            }
            if (rY < 350F && rY > 190F)
            {
                xreach = -0.2D;
            }
            AxisAlignedBB playerReach = p.getEntityBoundingBox().addCoord(xreach, 0, zreach);

            if (playerReach.maxX >= spinManager.ssBoundsMinX && playerReach.minX <= spinManager.ssBoundsMaxX && playerReach.maxY >= spinManager.ssBoundsMinY && playerReach.minY <= spinManager.ssBoundsMaxY && playerReach.maxZ >= spinManager.ssBoundsMinZ && playerReach.minZ <= spinManager.ssBoundsMaxZ)
            //Player is somewhere within the space station boundaries
            {
                //Check if the player's bounding box is in the same block coordinates as any non-vacuum block (including torches etc)
                //If so, it's assumed the player has something close enough to grab onto, so is not in freefall
                //Note: breatheable air here means the player is definitely not in freefall
                int xm = MathHelper.floor_double(playerReach.minX);
                int xx = MathHelper.floor_double(playerReach.maxX);
                int ym = MathHelper.floor_double(playerReach.minY);
                int yy = MathHelper.floor_double(playerReach.maxY);
                int zm = MathHelper.floor_double(playerReach.minZ);
                int zz = MathHelper.floor_double(playerReach.maxZ);
                for (int x = xm; x <= xx; x++)
                {
                    for (int y = ym; y <= yy; y++)
                    {
                        for (int z = zm; z <= zz; z++)
                        {
                            //Blocks.air is hard vacuum - we want to check for that, here
                            Block b = world.getBlockState(new BlockPos(x, y, z)).getBlock();
                            if (Blocks.air != b && GCBlocks.brightAir != b)
                            {
                                return false;
                            }
                        }
                    }
                }
            }
        }

		/*
        if (freefall)
		{
			//If that check didn't produce a result, see if the player is inside the walls
			//TODO: could apply special weightless movement here like Coriolis force - the player is inside the walls,  not touching them, and in a vacuum
			int quadrant = 0;
			double xd = p.posX - this.spinCentreX;
			double zd = p.posZ - this.spinCentreZ;
			if (xd<0)
			{
				if (xd<-Math.abs(zd))
				{
					quadrant = 2;
				} else
					quadrant = (zd<0) ? 3 : 1;
			} else
				if (xd>Math.abs(zd))
				{
					quadrant = 0;
				} else
					quadrant = (zd<0) ? 3 : 1;

			int ymin = MathHelper.floor_double(p.boundingBox.minY)-1;
			int ymax = MathHelper.floor_double(p.boundingBox.maxY);
			int xmin, xmax, zmin, zmax;

			switch (quadrant)
			{
			case 0:
				xmin = MathHelper.floor_double(p.boundingBox.maxX);
				xmax = this.ssBoundsMaxX - 1;
				zmin = MathHelper.floor_double(p.boundingBox.minZ)-1;
				zmax = MathHelper.floor_double(p.boundingBox.maxZ)+1;
				break;
			case 1:
				xmin = MathHelper.floor_double(p.boundingBox.minX)-1;
				xmax = MathHelper.floor_double(p.boundingBox.maxX)+1;
				zmin = MathHelper.floor_double(p.boundingBox.maxZ);
				zmax = this.ssBoundsMaxZ - 1;
				break;
			case 2:
				zmin = MathHelper.floor_double(p.boundingBox.minZ)-1;
				zmax = MathHelper.floor_double(p.boundingBox.maxZ)+1;
				xmin = this.ssBoundsMinX;
				xmax = MathHelper.floor_double(p.boundingBox.minX);
				break;
			case 3:
			default:
				xmin = MathHelper.floor_double(p.boundingBox.minX)-1;
				xmax = MathHelper.floor_double(p.boundingBox.maxX)+1;
				zmin = this.ssBoundsMinZ;
				zmax = MathHelper.floor_double(p.boundingBox.minZ);
				break;
			}

			//This block search could cost a lot of CPU (but client side) - maybe optimise later
			BLOCKCHECK0:
			for(int x = xmin; x <= xmax; x++)
				for (int z = zmin; z <= zmax; z++)
					for (int y = ymin; y <= ymax; y++)
						if (Blocks.air != this.worldProvider.worldObj.getBlock(x, y, z))
						{
							freefall = false;
							break BLOCKCHECK0;
						}
		}*/

        return true;
    }

    public void setupFreefallPre(EntityPlayerSP p)
    {
        double dY = p.motionY - pPrevMotionY;
        jetpackBoost = 0F;
        pPrevdY = dY;
        pPrevMotionX = p.motionX;
        pPrevMotionY = p.motionY;
        pPrevMotionZ = p.motionZ;
    }

    public void freefallMotion(EntityPlayerSP p)
    {
        boolean jetpackUsed = false;
        double dX = p.motionX - pPrevMotionX;
        double dY = p.motionY - pPrevMotionY;
        double dZ = p.motionZ - pPrevMotionZ;

        double posOffsetX = -p.motionX;
        double posOffsetY = -p.motionY;// + WorldUtil.getGravityForEntity(p);
        double posOffsetZ = -p.motionZ;
        //if (p.capabilities.isFlying)

        ///Undo whatever vanilla tried to do to our y motion
        if (dY < 0D && p.motionY != 0.0D)
        {
            p.motionY = pPrevMotionY;
        }
        else if (dY > 0.01D && GCPlayerStatsClient.get(p).inFreefallLast)
        {
            //Impulse upwards - it's probably a jetpack from another mod
            if (dX < 0.01D && dZ < 0.01D)
            {
                float pitch = p.rotationPitch / 57.29578F;
                jetpackBoost = (float) dY * MathHelper.cos(pitch) * 0.1F;
                float factor = 1 + MathHelper.sin(pitch) / 5;
                p.motionY -= dY * factor;
                jetpackUsed = true;
            }
            else
            {
                p.motionY -= dY / 2;
            }
        }

        p.motionX -= dX;
//        p.motionY -= dY;    //Enabling this will disable jetpacks
        p.motionZ -= dZ;

        if (p.movementInput.moveForward != 0)
        {
            p.motionX -= p.movementInput.moveForward * MathHelper.sin(p.rotationYaw / 57.29578F) / (ConfigManagerCore.hardMode ? 600F : 200F);
            p.motionZ += p.movementInput.moveForward * MathHelper.cos(p.rotationYaw / 57.29578F) / (ConfigManagerCore.hardMode ? 600F : 200F);
        }

        if (jetpackBoost != 0)
        {
            p.motionX -= jetpackBoost * MathHelper.sin(p.rotationYaw / 57.29578F);
            p.motionZ += jetpackBoost * MathHelper.cos(p.rotationYaw / 57.29578F);
        }

        if (p.movementInput.sneak)
        {
            if (!sneakLast)
            {
                posOffsetY += 0.0268;
                sneakLast = true;
            }
            p.motionY -= ConfigManagerCore.hardMode ? 0.002D : 0.0032D;
        }
        else if (sneakLast)
        {
            sneakLast = false;
            posOffsetY -= 0.0268;
        }

        if (!jetpackUsed && p.movementInput.jump)
        {
            p.motionY += ConfigManagerCore.hardMode ? 0.002D : 0.0032D;
        }

        float speedLimit = ConfigManagerCore.hardMode ? 0.9F : 0.7F;

        if (p.motionX > speedLimit)
        {
            p.motionX = speedLimit;
        }
        if (p.motionX < -speedLimit)
        {
            p.motionX = -speedLimit;
        }
        if (p.motionY > speedLimit)
        {
            p.motionY = speedLimit;
        }
        if (p.motionY < -speedLimit)
        {
            p.motionY = -speedLimit;
        }
        if (p.motionZ > speedLimit)
        {
            p.motionZ = speedLimit;
        }
        if (p.motionZ < -speedLimit)
        {
            p.motionZ = -speedLimit;
        }
        pPrevMotionX = p.motionX;
        pPrevMotionY = p.motionY;
        pPrevMotionZ = p.motionZ;
        p.moveEntity(p.motionX + posOffsetX, p.motionY + posOffsetY, p.motionZ + posOffsetZ);
    }

	/*				double dyaw = p.rotationYaw - p.prevRotationYaw;
    p.rotationYaw -= dyaw * 0.8D;
	double dyawh = p.rotationYawHead - p.prevRotationYawHead;
	p.rotationYawHead -= dyawh * 0.8D;
	while (p.rotationYaw > 360F)
	{
		p.rotationYaw -= 360F;
	}
	while (p.rotationYaw < 0F)
	{
		p.rotationYaw += 360F;
	}
	while (p.rotationYawHead > 360F)
	{
		p.rotationYawHead -= 360F;
	}
	while (p.rotationYawHead < 0F)
	{
		p.rotationYawHead += 360F;
	}
*/


    public void updateFreefall(EntityPlayer p)
    {
        pPrevMotionX = p.motionX;
        pPrevMotionY = p.motionY;
        pPrevMotionZ = p.motionZ;
    }

    @SideOnly(Side.CLIENT)
    public void preVanillaMotion(EntityPlayerSP p)
    {
        this.setupFreefallPre(p);
        GCPlayerStatsClient stats = GCPlayerStatsClient.get(p);
        stats.ssOnGroundLast = p.onGround;
    }

    @SideOnly(Side.CLIENT)
    public void postVanillaMotion(EntityPlayerSP p)
    {
        World world = p.worldObj;
        WorldProvider worldProvider = world.provider;
        if (!(worldProvider instanceof WorldProviderZeroGravity))
        {
            return;
        }
        WorldProviderZeroGravity worldProviderOrbit = (WorldProviderZeroGravity) worldProvider;
        SpinManager spinManager = worldProviderOrbit.getSpinManager();
        GCPlayerStatsClient stats = GCPlayerStatsClient.get(p);
        boolean freefall = stats.inFreefall;
//        if (freefall) p.ySize = 0F;  //Undo the sneak height adjust TODO Fix this for 1.8
        freefall = this.testFreefall(p, freefall);
        stats.inFreefall = freefall;
        stats.inFreefallFirstCheck = true;

        boolean doGravity = true;

        if (freefall)
        {
            doGravity = false;
            this.pjumpticks = 0;
            //Do spinning
            if (spinManager.doSpinning && spinManager.angularVelocityRadians != 0F)
            {
                //TODO maybe need to test to make sure xx and zz are not too large (outside sight range of SS)
                //TODO think about server + network load (loading/unloading chunks) when movement is rapid
                //Maybe reduce chunkloading radius?
                float angle;
                final double xx = p.posX - spinManager.spinCentreX;
                final double zz = p.posZ - spinManager.spinCentreZ;
                double arc = Math.sqrt(xx * xx + zz * zz);
                if (xx == 0D)
                {
                    angle = zz > 0 ? 3.1415926535F / 2 : -3.1415926535F / 2;
                }
                else
                {
                    angle = (float) Math.atan(zz / xx);
                }
                if (xx < 0D)
                {
                    angle += 3.1415926535F;
                }
                angle += spinManager.angularVelocityRadians / 3F;
                arc = arc * spinManager.angularVelocityRadians;
                double offsetX = -arc * MathHelper.sin(angle);
                double offsetZ = arc * MathHelper.cos(angle);

                //Check for block collisions here - if so move the player appropriately
                //First check that there are no existing collisions where the player is now (TODO: bounce the player away)
                if (world.getCollidingBoundingBoxes(p, p.getEntityBoundingBox()).size() == 0)
                {
                    //Now check for collisions in the new direction and if there are some, try reducing the movement
                    int collisions = 0;
                    do
                    {
                        List<AxisAlignedBB> list = world.getCollidingBoundingBoxes(p, p.getEntityBoundingBox().addCoord(offsetX, 0.0D, offsetZ));
                        collisions = list.size();
                        if (collisions > 0)
                        {
                            if (!doGravity)
                            {
                                p.motionX += -offsetX;
                                p.motionZ += -offsetZ;
                            }
                            offsetX /= 2D;
                            offsetZ /= 2D;
                            if (offsetX < 0.01D && offsetX > -0.01D)
                            {
                                offsetX = 0D;
                            }
                            if (offsetZ < 0.01D && offsetZ > -0.01D)
                            {
                                offsetZ = 0D;
                            }
                            doGravity = true;

                        }
                    }
                    while (collisions > 0);

                    p.posX += offsetX;
                    p.posZ += offsetZ;
                    p.getEntityBoundingBox().offset(offsetX, 0.0D, offsetZ);
                }

                p.rotationYaw += spinManager.skyAngularVelocity;
                p.prevRotationYaw += spinManager.skyAngularVelocity;
                while (p.rotationYaw > 360F)
                {
                    p.rotationYaw -= 360F;
                }
                while (p.rotationYaw < 0F)
                {
                    p.rotationYaw += 360F;
                }
                while (p.prevRotationYaw > 360F)
                {
                    p.prevRotationYaw -= 360F;
                }
                while (p.prevRotationYaw < 0F)
                {
                    p.prevRotationYaw += 360F;
                }

				/*				//Just started freefall - give some impulse
                                if (!p.inFreefall && p.inFreefallFirstCheck)
								{
									p.motionX += offsetX * 0.91F;
									p.motionZ += offsetZ * 0.91F;
								}*/
            }

            //Reverse effects of deceleration
            p.motionX /= 0.91F;
            p.motionZ /= 0.91F;
            p.motionY /= 0.9800000190734863D;

            //Do freefall motion
            if (!p.capabilities.isCreativeMode)
            {
                this.freefallMotion(p);
            }
            else
            {
                //Half the normal acceleration in Creative mode
                double dx = p.motionX - this.pPrevMotionX;
                double dy = p.motionY - this.pPrevMotionY;
                double dz = p.motionZ - this.pPrevMotionZ;
                p.motionX -= dx / 2;
                p.motionY -= dy / 2;
                p.motionZ -= dz / 2;

                if (p.motionX > 1.2F)
                {
                    p.motionX = 1.2F;
                }
                if (p.motionX < -1.2F)
                {
                    p.motionX = -1.2F;
                }
                if (p.motionY > 0.7F)
                {
                    p.motionY = 0.7F;
                }
                if (p.motionY < -0.7F)
                {
                    p.motionY = -0.7F;
                }
                if (p.motionZ > 1.2F)
                {
                    p.motionZ = 1.2F;
                }
                if (p.motionZ < -1.2F)
                {
                    p.motionZ = -1.2F;
                }
            }
            //TODO: Think about endless drift?
            //Player may run out of oxygen - that will kill the player eventually if can't get back to SS
            //Could auto-kill + respawn the player if floats too far away (config option whether to lose items or not)
            //But we want players to be able to enjoy the view of the spinning space station from the outside
            //Arm and leg movements could start tumbling the player?
        }
        else
        //Not freefall - within arm's length of something or jumping
        {
            double dy = p.motionY - this.pPrevMotionY;
            //if (p.motionY < 0 && this.pPrevMotionY >= 0) p.posY -= p.motionY;
            //if (p.motionY != 0) p.motionY = this.pPrevMotionY;
            if (p.movementInput.jump)
            {
                if (p.onGround || stats.ssOnGroundLast)
                {
                    this.pjumpticks = 20;
                    p.motionY -= 0.015D;
                    p.onGround = false;
                    p.posY -= 0.1D;
                    p.getEntityBoundingBox().offset(0, -0.1D, 0);
                }
                else
                {
                    p.motionY += 0.015D;
                    if (this.pjumpticks == 0)
                    {
                        p.motionY -= dy;
                    }
                }
            }
            else if (p.movementInput.sneak)
            {
                if (!p.onGround)
                {
                    p.motionY -= 0.015D;
                    if (!this.sneakLast)
                    {
                        p.getEntityBoundingBox().offset(0D, 0.0268D, 0D);
                        this.sneakLast = true;
                    }
                }
                this.pjumpticks = 0;
            }
            else if (this.sneakLast)
            {
                this.sneakLast = false;
                p.getEntityBoundingBox().offset(0D, -0.0268D, 0D);
            }

            if (this.pjumpticks > 0)
            {
                this.pjumpticks--;
                p.motionY -= dy;
                if (this.pjumpticks >= 17)
                {
                    p.motionY += 0.03D;
                }
            }
        }

        //Artificial gravity
        if (doGravity && !p.onGround)
        {
            int quadrant = 0;
            double xd = p.posX - spinManager.spinCentreX;
            double zd = p.posZ - spinManager.spinCentreZ;
            double accel = Math.sqrt(xd * xd + zd * zd) * spinManager.angularVelocityRadians * spinManager.angularVelocityRadians * 4D;

            if (xd < 0)
            {
                if (xd < -Math.abs(zd))
                {
                    quadrant = 2;
                }
                else
                {
                    quadrant = zd < 0 ? 3 : 1;
                }
            }
            else if (xd > Math.abs(zd))
            {
                quadrant = 0;
            }
            else
            {
                quadrant = zd < 0 ? 3 : 1;
            }

            switch (quadrant)
            {
            case 0:
                p.motionX += accel;
                break;
            case 1:
                p.motionZ += accel;
                break;
            case 2:
                p.motionX -= accel;
                break;
            case 3:
            default:
                p.motionZ -= accel;
            }
        }
        this.pPrevMotionX = p.motionX;
        this.pPrevMotionY = p.motionY;
        this.pPrevMotionZ = p.motionZ;
    }
}
