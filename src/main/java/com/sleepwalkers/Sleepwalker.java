package com.sleepwalkers;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.NPC;

@Getter
@Setter(AccessLevel.PACKAGE)
class Sleepwalker
{
	private final NPC npc;
	// Ticks since it was hidden as dead, or -1 while it isn't
	private int hiddenTicks = -1;
	// Ticks after it was hidden by which the hit has to have landed, or the call was wrong
	private int deadlineTicks;

	Sleepwalker(NPC npc)
	{
		this.npc = npc;
	}

	boolean isKilled()
	{
		return hiddenTicks >= 0;
	}
}
