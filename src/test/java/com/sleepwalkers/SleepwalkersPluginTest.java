package com.sleepwalkers;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SleepwalkersPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SleepwalkersPlugin.class);
		RuneLite.main(args);
	}
}
