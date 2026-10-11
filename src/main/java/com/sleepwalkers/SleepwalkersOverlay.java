package com.sleepwalkers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class SleepwalkersOverlay extends Overlay
{
	private static final Stroke OUTLINE = new BasicStroke(3);

	private final Client client;
	private final SleepwalkersPlugin plugin;
	private final SleepwalkersConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	SleepwalkersOverlay(Client client, SleepwalkersPlugin plugin, SleepwalkersConfig config,
		ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.highlight() && !plugin.getSleepwalkers().isEmpty())
		{
			renderHighlights(graphics);
		}

		return null;
	}

	private void renderHighlights(Graphics2D graphics)
	{
		HighlightMode mode = config.highlightMode();
		Color color = config.highlightColor();
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 5);
		List<Sleepwalker> sleepwalkers = plugin.getSleepwalkers();
		for (int i = 0; i < sleepwalkers.size(); i++)
		{
			Sleepwalker sleepwalker = sleepwalkers.get(i);
			if (isGone(sleepwalker))
			{
				continue;
			}

			NPC npc = sleepwalker.getNpc();
			Shape shape = null;
			switch (mode)
			{
				case TRUE_TILE:
					// The tile the server has it on, rather than where it's drawn mid-walk
					LocalPoint tile = LocalPoint.fromWorld(npc.getWorldView(), npc.getWorldLocation());
					if (tile != null)
					{
						int size = npc.getComposition().getSize();
						shape = Perspective.getCanvasTileAreaPoly(client, tile.plus(
							(size - 1) * Perspective.LOCAL_HALF_TILE_SIZE,
							(size - 1) * Perspective.LOCAL_HALF_TILE_SIZE), size);
					}
					break;
				case TILE:
					shape = npc.getCanvasTilePoly();
					break;
				case HULL:
					shape = npc.getConvexHull();
					break;
				case OUTLINE:
					outlineRenderer.drawOutline(npc, 2, color, 4);
					break;
			}

			if (shape != null)
			{
				OverlayUtil.renderPolygon(graphics, shape, color, fill, new BasicStroke(2));
			}
		}
		graphics.setStroke(OUTLINE);
	}

	/**
	 * Only the ones still worth watching are drawn on, so a sleepwalker drops out of the highlight the
	 * moment it's counted as dead, whether the plugin is hiding it or not.
	 */
	private boolean isGone(Sleepwalker sleepwalker)
	{
		return sleepwalker.isKilled() || sleepwalker.getNpc().getHealthRatio() == 0;
	}
}
