package com.inferno;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class InfernoDeathIndicatorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(InfernoDeathIndicatorPlugin.class);
		RuneLite.main(args);
	}
}