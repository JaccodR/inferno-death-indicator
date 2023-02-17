package com.deathindicator;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

@Slf4j
@PluginDescriptor(
	name = "TzHaar Death Indicator"
)
public class TzHaarDeathIndicatorPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;
	@Inject
	private Hooks hooks;

	private boolean isInTzHaar = false;

	private final ArrayList<TzHaarNPC> tzHaarNPCS = new ArrayList<>();
	private final ArrayList<TzHaarNPC> deadTzHaarNPCS = new ArrayList<>();
	private final Map<Skill, Integer> previousXpMap = new EnumMap<Skill, Integer>(Skill.class);

	private static final Set<Integer> CHINCHOMPAS = new HashSet<>(Arrays.asList(
			ItemID.CHINCHOMPA_10033,
			ItemID.RED_CHINCHOMPA_10034,
			ItemID.BLACK_CHINCHOMPA
	));

	private static final int dinhs = ItemID.DINHS_BULWARK;

	private static final int BARRAGE_ANIMATION = 1979;
	private static final int INFERNO_REGION_ID = 9043;
	private static final int FIGHTCAVES_REGION_ID = 9551;
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@Override
	protected void startUp() throws Exception
	{
		clientThread.invoke(this::initPreviousXpMap);
		hooks.registerRenderableDrawListener(drawListener);
	}

	@Override
	protected void shutDown() throws Exception
	{
		hooks.unregisterRenderableDrawListener(drawListener);
	}

	private void initPreviousXpMap()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			previousXpMap.clear();
		}
		else
		{
			for (final Skill skill: Skill.values())
			{
				previousXpMap.put(skill, client.getSkillExperience(skill));
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (!isInTzHaar)
		{
			isInTzHaar = isInTzHaar();
		}
		else
		{
			isInTzHaar = isInTzHaar();
			if (!isInTzHaar)
			{
				this.tzHaarNPCS.clear();
				this.deadTzHaarNPCS.clear();
			}

			Iterator<TzHaarNPC> infernoNPCIterator = deadTzHaarNPCS.iterator();
			while (infernoNPCIterator.hasNext())
			{
				TzHaarNPC tzHaarNPC = infernoNPCIterator.next();
				tzHaarNPC.setHidden(tzHaarNPC.getHidden() + 1);

				final boolean isDead = tzHaarNPC.getNpc().getHealthRatio() == 0;
				if (tzHaarNPC.getHidden() > 5 && !isDead)
				{
					tzHaarNPC.setHidden(0);
					infernoNPCIterator.remove();
				}
			}
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		if (!isInTzHaar)
			return;

		for (TzHaarNPC tzHaarNPC : deadTzHaarNPCS)
			tzHaarNPC.getNpc().setDead(true);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied)
	{
		if (!isInTzHaar)
			return;

		Actor actor = hitsplatApplied.getActor();
		if (actor instanceof NPC)
		{
			final int npcIndex = ((NPC) actor).getIndex();
			final int damage = hitsplatApplied.getHitsplat().getAmount();

			for (TzHaarNPC tzHaarNPC : this.tzHaarNPCS)
			{
				if (tzHaarNPC.getNpcIndex() != npcIndex)
					continue;

				if (hitsplatApplied.getHitsplat().getHitsplatType() == HitsplatID.HEAL)
					tzHaarNPC.setHp(tzHaarNPC.getHp() + damage);
				else
					tzHaarNPC.setHp(tzHaarNPC.getHp() - damage);

				tzHaarNPC.setQueuedDamage(Math.max(0, tzHaarNPC.getQueuedDamage() - damage));
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		if (!isInTzHaar)
			return;

		TzHaarNPC tzHaarNPC = null;
		NPC npc = npcSpawned.getNpc();
		int index = npc.getIndex();
		switch(npc.getId())
		{
			// All inferno monsters
			case NpcID.JALNIB:
				tzHaarNPC = new TzHaarNPC(npc, index, 10);
				break;
			case NpcID.JALMEJRAH:
				tzHaarNPC = new TzHaarNPC(npc, index, 25);
				break;
			case NpcID.JALAK:
				tzHaarNPC = new TzHaarNPC(npc, index, 40);
				break;
			case NpcID.JALAKREKXIL:
			case NpcID.JALAKREKMEJ:
			case NpcID.JALAKREKKET:
				tzHaarNPC = new TzHaarNPC(npc, index, 15);
				break;
			case NpcID.JALIMKOT:
				tzHaarNPC = new TzHaarNPC(npc, index, 75);
				break;
			case NpcID.JALXIL:
				tzHaarNPC = new TzHaarNPC(npc, index, 125);
				break;
			case NpcID.JALZEK:
				tzHaarNPC = new TzHaarNPC(npc, index, 220);
				break;
			case NpcID.JALTOKJAD:
				tzHaarNPC = new TzHaarNPC(npc, index, 350);
				break;
			case NpcID.TZKALZUK:
				tzHaarNPC = new TzHaarNPC(npc, index, 1200);
				break;
			case NpcID.JALMEJJAK:
				tzHaarNPC = new TzHaarNPC(npc, index, 75);
				break;
			// All fight caves monsters (npcid. didnt work for these for some reason :))) )
			case 2189: //bats
			case 2190:
			case 3116:
			case 3117:
				tzHaarNPC = new TzHaarNPC(npc, index, 10);
				break;
			case 2191: // blob
			case 2192:
			case 3118:
			case 3119:
				tzHaarNPC = new TzHaarNPC(npc, index, 20);
				break;

			case 3120: // small blobs
				tzHaarNPC = new TzHaarNPC(npc, index, 10);
				break;

			case 2193: // ranger
			case 2194:
			case 3121:
			case 3122:
				tzHaarNPC = new TzHaarNPC(npc, index, 40);
				break;

			case 3123: // melee
			case 3124:
				tzHaarNPC = new TzHaarNPC(npc, index, 80);
				break;

			case 3125: // mage
			case 3126:
				tzHaarNPC = new TzHaarNPC(npc, index, 160);
				break;

			case 3127: // jad
			case 6506:
				tzHaarNPC = new TzHaarNPC(npc, index, 250);
				break;
		}
		if (tzHaarNPC != null)
		{
			log.info(tzHaarNPC.getNpc().getName());
			this.tzHaarNPCS.add(tzHaarNPC);
		}
	}

	@Subscribe
	private void onFakeXpDrop(FakeXpDrop fakeXpDrop)
	{
		processXpDrop(fakeXpDrop.getSkill(), fakeXpDrop.getXp());
	}

	@Subscribe
	private void onStatChanged(StatChanged statChanged)
	{
		preProcessXpDrop(statChanged.getSkill(), statChanged.getXp());
	}

	private void preProcessXpDrop(Skill skill, int xp)
	{
		final int xpAfter = client.getSkillExperience(skill);
		final int xpBefore = previousXpMap.getOrDefault(skill, -1);

		previousXpMap.put(skill, xpAfter);

		if (xpBefore == -1 || xpAfter <= xpBefore)
			return;


		processXpDrop(skill, xpAfter - xpBefore);
	}

	private void processXpDrop(Skill skill, final int xp)
	{
		if (!isInTzHaar)
			return;

		Player player = client.getLocalPlayer();
		if (player == null)
			return;

		PlayerComposition playerComposition = player.getPlayerComposition();
		if (playerComposition == null)
			return;

		int weapon = playerComposition.getEquipmentId(KitType.WEAPON);
		int attackStyle = client.getVarpValue(VarPlayer.ATTACK_STYLE.getId());

		boolean isChin = CHINCHOMPAS.contains(weapon);
		boolean isDinh = (dinhs == weapon);
		if (player.getAnimation() == BARRAGE_ANIMATION)
			return;

		int damage = 0;

		switch(skill)
		{
			case ATTACK:
			case STRENGTH:
			case DEFENCE:
				if (isDinh)
					return;

				damage = (int) ((double) xp / 4.0D);
				break;
			case RANGED:
				if (isChin)
					return;

				if (attackStyle == 3)
					damage = (int) ((double) xp / 2.0D);
				else
					damage = (int) ((double) xp / 4.0D);
				break;

		}
		if (damage <= 0)
			return;


		Actor interacted = player.getInteracting();
		if (interacted instanceof NPC)
		{
			NPC interactedNPC = (NPC) interacted;
			final int npcIndex = interactedNPC.getIndex();

			for (TzHaarNPC tzHaarNPC : this.tzHaarNPCS)
			{
				if (tzHaarNPC.getNpcIndex() != npcIndex)
					continue;

				tzHaarNPC.setQueuedDamage(tzHaarNPC.getQueuedDamage() + damage);
				if (tzHaarNPC.getHp() - tzHaarNPC.getQueuedDamage() <= 0)
				{
					if (deadTzHaarNPCS.stream().noneMatch(deadTzHaarNPCs -> deadTzHaarNPCs.getNpcIndex() == npcIndex))
					{
						deadTzHaarNPCS.add(tzHaarNPC);
					}
				}
			}
		}
	}

	private boolean isInTzHaar()
	{
		if (client.getMapRegions() == null)
			return false;

		if (ArrayUtils.contains(client.getMapRegions(), INFERNO_REGION_ID) || ArrayUtils.contains(client.getMapRegions(), FIGHTCAVES_REGION_ID))
			return true;

		return false;
	}

	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (renderable instanceof NPC)
		{
			return deadTzHaarNPCS.stream().noneMatch(tzHaarNPC -> tzHaarNPC.getNpcIndex() == ((NPC) renderable).getIndex());
		}

		return true;
	}
}
