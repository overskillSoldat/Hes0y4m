package com.github.lunatrius.schematica.client.printer;

import com.github.lunatrius.core.util.math.BlockPosHelper;
import com.github.lunatrius.core.util.math.MBlockPos;
import com.github.lunatrius.schematica.block.state.BlockStateHelper;
import com.github.lunatrius.schematica.client.printer.nbtsync.NBTSync;
import com.github.lunatrius.schematica.client.printer.nbtsync.SyncRegistry;
import com.github.lunatrius.schematica.client.printer.registry.PlacementData;
import com.github.lunatrius.schematica.client.printer.registry.PlacementRegistry;
import com.github.lunatrius.schematica.client.util.BlockStateToItemStack;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.handler.ConfigurationHandler;
import com.github.lunatrius.schematica.proxy.ClientProxy;
import com.github.lunatrius.schematica.reference.Reference;


import com.mrzak34.thunderhack.Thunderhack;
import com.mrzak34.thunderhack.events.EventSchematicaPlaceBlock;
import com.mrzak34.thunderhack.events.EventSchematicaPlaceBlockFull;
import com.mrzak34.thunderhack.modules.client.PrinterBypass;
import com.mrzak34.thunderhack.util.BlockInteractionHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.BlockFluidBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class SchematicPrinter
{
    public static final SchematicPrinter INSTANCE = new SchematicPrinter();

    private final Minecraft minecraft = Minecraft.getMinecraft();

    private boolean isEnabled = true;
    private boolean isPrinting = false;
    private boolean Stationary = false;

    private SchematicWorld schematic = null;
    private byte[][][] timeout = null;
    private HashMap<BlockPos, Integer> syncBlacklist = new HashMap<BlockPos, Integer>();

    public boolean isEnabled()
    {
        return this.isEnabled;
    }

    public void setEnabled(final boolean isEnabled)
    {
        this.isEnabled = isEnabled;
    }

    public boolean togglePrinting()
    {
        this.isPrinting = !this.isPrinting && this.schematic != null;
        return this.isPrinting;
    }

    public boolean isPrinting()
    {
        return this.isPrinting;
    }

    public void setPrinting(final boolean isPrinting)
    {
        this.isPrinting = isPrinting;
    }

    public SchematicWorld getSchematic()
    {
        return this.schematic;
    }

    public boolean IsStationary()
    {
        return schematic == null || Stationary;
    }

    public void setSchematic(final SchematicWorld schematic)
    {
        //     this.isPrinting = false;
        this.schematic = schematic;
        refresh();
    }

    public void refresh()
    {
        if (this.schematic != null)
        {
            this.timeout = new byte[this.schematic.getWidth()][this.schematic.getHeight()][this.schematic.getLength()];
        }
        else
        {
            this.timeout = null;
        }
        this.syncBlacklist.clear();
    }

    public boolean print(final WorldClient world, final EntityPlayerSP player)
    {
        final double dX = ClientProxy.playerPosition.x - this.schematic.position.x;
        final double dY = ClientProxy.playerPosition.y - this.schematic.position.y;
        final double dZ = ClientProxy.playerPosition.z - this.schematic.position.z;
        final int x = (int) Math.floor(dX);
        final int y = (int) Math.floor(dY);
        final int z = (int) Math.floor(dZ);
        final int range = ConfigurationHandler.placeDistance;

        final int minX = Math.max(0, x - range);
        final int maxX = Math.min(this.schematic.getWidth() - 1, x + range);
        int minY = Math.max(0, y - range);
        int maxY = Math.min(this.schematic.getHeight() - 1, y + range);
        final int minZ = Math.max(0, z - range);
        final int maxZ = Math.min(this.schematic.getLength() - 1, z + range);

        if (minX > maxX || minY > maxY || minZ > maxZ)
        {
            return false;
        }

        final int slot = player.inventory.currentItem;
        final boolean isSneaking = player.isSneaking();

        switch (schematic.layerMode)
        {
            case ALL:
                break;
            case SINGLE_LAYER:
                if (schematic.renderingLayer > maxY)
                {
                    return false;
                }
                maxY = schematic.renderingLayer;
                //$FALL-THROUGH$
            case ALL_BELOW:
                if (schematic.renderingLayer < minY)
                {
                    return false;
                }
                maxY = schematic.renderingLayer;
                break;
        }

        syncSneaking(player, true);

        // final double blockReachDistance = this.minecraft.playerController.getBlockReachDistance() - 0.1;
        final double blockReachDistance = Thunderhack.moduleManager.getModuleByClass(PrinterBypass.class).reach.getValue();
        final double blockReachDistanceSq = blockReachDistance * blockReachDistance;

        for (final MBlockPos pos : BlockPosHelper.getAllInBoxXZY(minX, minY, minZ, maxX, maxY, maxZ))
        {
            if (pos.distanceSqToCenter(dX, dY, dZ) > blockReachDistanceSq)
            {
                continue;
            }

            try
            {
                if (placeBlock(world, player, pos))
                {
                    Stationary = false;
                    return syncSlotAndSneaking(player, slot, isSneaking, true);
                }
            }
            catch (final Exception e)
            {
                Reference.logger.error("Could not place block!", e);
                return syncSlotAndSneaking(player, slot, isSneaking, false);
            }
        }

        Stationary = true;

        return syncSlotAndSneaking(player, slot, isSneaking, true);
    }

    private boolean syncSlotAndSneaking(final EntityPlayerSP player, final int slot, final boolean isSneaking, final boolean success)
    {
        /// ignore this
        syncSneaking(player, isSneaking);
        return success;
    }

    private boolean placeBlock(final WorldClient world, final EntityPlayerSP player, final BlockPos pos)
    {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        /*if (this.timeout[x][y][z] > 0)
        {
            this.timeout[x][y][z]--;
            return false;
        }*/

        final int wx = this.schematic.position.x + x;
        final int wy = this.schematic.position.y + y;
        final int wz = this.schematic.position.z + z;
        final BlockPos realPos = new BlockPos(wx, wy, wz);

        final IBlockState blockState = this.schematic.getBlockState(pos);
        final IBlockState realBlockState = world.getBlockState(realPos);
        final Block realBlock = realBlockState.getBlock();

        if (BlockStateHelper.areBlockStatesEqual(blockState, realBlockState))
        {
            // TODO: clean up this mess
            final NBTSync handler = SyncRegistry.INSTANCE.getHandler(realBlock);
            if (handler != null)
            {
                this.timeout[x][y][z] = (byte) ConfigurationHandler.timeout;

                Integer tries = this.syncBlacklist.get(realPos);
                if (tries == null)
                {
                    tries = 0;
                }
                else if (tries >= 10)
                {
                    return false;
                }

                Reference.logger.trace("Trying to sync block at {} {}", realPos, tries);
                final boolean success = handler.execute(player, this.schematic, pos, world, realPos);
                if (success)
                {
                    this.syncBlacklist.put(realPos, tries + 1);
                }

                return success;
            }

            return false;
        }

        if (ConfigurationHandler.destroyBlocks && !world.isAirBlock(realPos) && this.minecraft.playerController.isInCreativeMode())
        {
            this.minecraft.playerController.clickBlock(realPos, EnumFacing.DOWN);

            this.timeout[x][y][z] = (byte) ConfigurationHandler.timeout;

            return !ConfigurationHandler.destroyInstantly;
        }

        if (this.schematic.isAirBlock(pos))
        {
            return false;
        }

        if (!realBlock.isReplaceable(world, realPos))
        {
            return false;
        }

        final ItemStack itemStack = BlockStateToItemStack.getItemStack(blockState, new RayTraceResult(player), this.schematic, pos, player);
        if (itemStack.isEmpty())
        {
            Reference.logger.debug("{} is missing a mapping!", blockState);
            return false;
        }

        if (placeBlock(world, player, realPos, blockState, itemStack))
        {
            this.timeout[x][y][z] = (byte) ConfigurationHandler.timeout;

            if (!ConfigurationHandler.placeInstantly)
            {
                return true;
            }
        }

        return false;
    }

    private boolean isSolid(final World world, final BlockPos pos, final EnumFacing side)
    {
        final BlockPos offset = pos.offset(side);

        final IBlockState blockState = world.getBlockState(offset);
        final Block block = blockState.getBlock();

        if (block == null)
        {
            return false;
        }

        if (block.isAir(blockState, world, offset))
        {
            return false;
        }

        if (block instanceof BlockFluidBase)
        {
            return false;
        }

        if (block.isReplaceable(world, offset))
        {
            return false;
        }

        return true;
    }

    private List<EnumFacing> getSolidSides(final World world, final BlockPos pos)
    {
        if (!ConfigurationHandler.placeAdjacent)
        {
            return Arrays.asList(EnumFacing.VALUES);
        }

        final List<EnumFacing> list = new ArrayList<EnumFacing>();

        for (final EnumFacing side : EnumFacing.VALUES)
        {
            if (isSolid(world, pos, side))
            {
                list.add(side);
            }
        }

        if (list.isEmpty() && false)
        {
            BlockInteractionHelper.ValidResult l_Result = BlockInteractionHelper.valid(pos);

            if (l_Result == BlockInteractionHelper.ValidResult.Ok)
                list.add(EnumFacing.UP);
        }

        return list;
    }

    private boolean placeBlock(final WorldClient world, final EntityPlayerSP player, final BlockPos pos, final IBlockState blockState, final ItemStack itemStack)
    {

        if (itemStack.getItem() instanceof ItemBucket)
        {
            return false;
        }

        final PlacementData data = PlacementRegistry.INSTANCE.getPlacementData(blockState, itemStack);
        if (data != null && !data.isValidPlayerFacing(blockState, player, pos, world))
        {
            return false;
        }

        final List<EnumFacing> solidSides = getSolidSides(world, pos);

        if (solidSides.size() == 0)
        {
            return false;
        }



        MinecraftForge.EVENT_BUS.post(new EventSchematicaPlaceBlock(pos));

        final EnumFacing direction;
        final float offsetX;
        final float offsetY;
        final float offsetZ;
        final int extraClicks;

        if (data != null)
        {
            final List<EnumFacing> validDirections = data.getValidBlockFacings(solidSides, blockState);
            if (validDirections.size() == 0)
            {
                return false;
            }

            direction = validDirections.get(0);
            offsetX = data.getOffsetX(blockState);
            offsetY = data.getOffsetY(blockState);
            offsetZ = data.getOffsetZ(blockState);
            extraClicks = data.getExtraClicks(blockState);
        }
        else
        {
            direction = solidSides.get(0);
            offsetX = 0.5f;
            offsetY = 0.5f;
            offsetZ = 0.5f;
            extraClicks = 0;
        }




        Stationary = false;

        EventSchematicaPlaceBlockFull l_Event = new EventSchematicaPlaceBlockFull(pos, itemStack);

        MinecraftForge.EVENT_BUS.post(l_Event);

        // if (l_Event.isCanceled())
        return l_Event.GetResult();
    }




    private void syncSneaking(final EntityPlayerSP player, final boolean isSneaking)
    {
        player.setSneaking(isSneaking);
        player.connection.sendPacket(new CPacketEntityAction(player, isSneaking ? CPacketEntityAction.Action.START_SNEAKING : CPacketEntityAction.Action.STOP_SNEAKING));
    }


}