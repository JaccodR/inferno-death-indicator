package com.deathindicator;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TzHaarDeathIndicatorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TzHaarDeathIndicatorPlugin.class);
		RuneLite.main(args);
	}
}