package com.sleepwalkers;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SleepwalkersConfig.GROUP)
public interface SleepwalkersConfig extends Config
{
	String GROUP = "sleepwalkers";

	@ConfigSection(
		name = "Highlight",
		description = "Highlight around each sleepwalker that's still coming",
		position = 20
	)
	String highlightSection = "highlight";

	@ConfigItem(
		keyName = "hideKilled",
		name = "Hide killed sleepwalkers",
		description = "Hide a sleepwalker the moment it is hit, unless she is taking it in",
		position = 0
	)
	default boolean hideKilled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fakeXpDrops",
		name = "Fake XP drops",
		description = "Drop 13 hitpoints XP on each kill, since the hit has not landed yet",
		position = 1
	)
	default boolean fakeXpDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlight",
		name = "Highlight sleepwalkers",
		description = "Highlight sleepwalkers that are still coming",
		position = 21,
		section = highlightSection
	)
	default boolean highlight()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightMode",
		name = "Mode",
		description = "How sleepwalkers are highlighted",
		position = 22,
		section = highlightSection
	)
	default HighlightMode highlightMode()
	{
		return HighlightMode.TRUE_TILE;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Color",
		description = "Color of the highlight",
		position = 23,
		section = highlightSection
	)
	default Color highlightColor()
	{
		return Color.CYAN;
	}
}
