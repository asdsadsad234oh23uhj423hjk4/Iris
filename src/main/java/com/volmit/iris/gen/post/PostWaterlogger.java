package com.volmit.iris.gen.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.IrisPostBlockFilter;

@Post("waterlogger")
public class PostWaterlogger extends IrisPostBlockFilter
{
	private static final BlockData WATER = BlockDataTools.getBlockData("WATER");

	public PostWaterlogger(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
	}

	public PostWaterlogger(PostBlockChunkGenerator gen)
	{
		super(gen);
	}

	@Override
	public void onPost(int x, int z)
	{
		int h = highestTerrainBlock(x, z);
		BlockData b = getPostBlock(x, h, z);

		if(b instanceof Waterlogged)
		{
			Waterlogged ww = (Waterlogged) b;

			if(ww.isWaterlogged())
			{
				return;
			}

			if(isWaterOrWaterlogged(x, h + 1, z) && !ww.isWaterlogged())
			{
				ww.setWaterlogged(true);
				setPostBlock(x, h, z, ww);
			}

			else if(!ww.isWaterlogged() && (isWaterOrWaterlogged(x + 1, h, z) || isWaterOrWaterlogged(x - 1, h, z) || isWaterOrWaterlogged(x, h, z + 1) || isWaterOrWaterlogged(x, h, z - 1)))
			{
				ww.setWaterlogged(true);
				setPostBlock(x, h, z, ww);
			}
		}

		else if(b.getMaterial().equals(Material.AIR) && h <= gen.getFluidHeight())
		{
			if((isWaterOrWaterlogged(x + 1, h, z) || isWaterOrWaterlogged(x - 1, h, z) || isWaterOrWaterlogged(x, h, z + 1) || isWaterOrWaterlogged(x, h, z - 1)))
			{
				setPostBlock(x, h, z, WATER);
			}
		}
	}
}