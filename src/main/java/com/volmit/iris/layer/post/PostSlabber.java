package com.volmit.iris.layer.post;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.generator.PostBlockChunkGenerator;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.RNG;

@Post("slabber")
public class PostSlabber extends IrisPostBlockFilter
{
	public static final Material AIR = Material.AIR;
	public static final Material WATER = Material.WATER;
	private RNG rng;

	public PostSlabber(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
		rng = gen.getMasterRandom().nextParallelRNG(166456);
	}

	public PostSlabber(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z)
	{
		int h = highestTerrainBlock(x, z);
		int ha = highestTerrainBlock(x + 1, z);
		int hb = highestTerrainBlock(x, z + 1);
		int hc = highestTerrainBlock(x - 1, z);
		int hd = highestTerrainBlock(x, z - 1);

		if(ha == h + 1 || hb == h + 1 || hc == h + 1 || hd == h + 1)
		{
			BlockData d = gen.sampleTrueBiome(x, z).getBiome().getSlab().get(rng, x, h, z);

			if(d != null)
			{
				if(d.getMaterial().equals(AIR))
				{
					return;
				}

				if(isAir(x, h + 2, z) || getPostBlock(x, h + 2, z).getMaterial().equals(WATER))
				{
					queue(() ->
					{
						setPostBlock(x, h + 1, z, d);
						updateHeight(x, z, h + 1);
					});
				}
			}
		}
	}
}