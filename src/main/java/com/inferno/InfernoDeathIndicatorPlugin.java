package com.inferno;

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
	name = "[S]Inferno Death Indicator"
)
public class InfernoDeathIndicatorPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private InfernoDeathIndicatorConfig config;

	@Inject
	private ClientThread clientThread;
	@Inject
	private Hooks hooks;

	private boolean isInInferno = false;

	private final ArrayList<InfernoNPC> infernoNPCs = new ArrayList<>();
	private final ArrayList<InfernoNPC> deadInfernoNPCs = new ArrayList<>();
	private final Map<Skill, Integer> previousXpMap = new EnumMap<Skill, Integer>(Skill.class);

	private static final Set<Integer> CHINCHOMPAS = new HashSet<>(Arrays.asList(
			ItemID.CHINCHOMPA_10033,
			ItemID.RED_CHINCHOMPA_10034,
			ItemID.BLACK_CHINCHOMPA
	));

	private static final int dinhs = ItemID.DINHS_BULWARK;

	private static final int BARRAGE_ANIMATION = 1979;
	private static final int INFERNO_REGION_ID = 9043;
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
		if (!isInInferno)
		{
			isInInferno = isInInferno();
		}
		else
		{
			isInInferno = isInInferno();
			if (!isInInferno)
			{
				this.infernoNPCs.clear();
				this.deadInfernoNPCs.clear();
			}

			Iterator<InfernoNPC> infernoNPCIterator = deadInfernoNPCs.iterator();
			while (infernoNPCIterator.hasNext())
			{
				InfernoNPC inpc = infernoNPCIterator.next();
				inpc.setHidden(inpc.getHidden() + 1);

				final boolean isDead = inpc.getNpc().getHealthRatio() == 0;
				if (inpc.getHidden() > 5 && !isDead)
				{
					inpc.setHidden(0);
					infernoNPCIterator.remove();
				}
			}
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		if (!isInInferno)
			return;

		for (InfernoNPC infernoNPC: deadInfernoNPCs)
			infernoNPC.getNpc().setDead(true);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied)
	{
		if (!isInInferno)
			return;

		Actor actor = hitsplatApplied.getActor();
		if (actor instanceof NPC)
		{
			final int npcIndex = ((NPC) actor).getIndex();
			final int damage = hitsplatApplied.getHitsplat().getAmount();

			for (InfernoNPC infernoNPC : this.infernoNPCs)
			{
				if (infernoNPC.getNpcIndex() != npcIndex)
					continue;

				if (hitsplatApplied.getHitsplat().getHitsplatType() == HitsplatID.HEAL)
					infernoNPC.setHp(infernoNPC.getHp() + damage);
				else
					infernoNPC.setHp(infernoNPC.getHp() - damage);

				infernoNPC.setQueuedDamage(Math.max(0, infernoNPC.getQueuedDamage() - damage));
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		if (!isInInferno)
			return;

		InfernoNPC infernoNPC = null;
		NPC npc = npcSpawned.getNpc();
		int index = npc.getIndex();
		switch(npcSpawned.getNpc().getId())
		{
			case NpcID.JALNIB:
				infernoNPC = new InfernoNPC(npc, index, 10);
				break;
			case NpcID.JALMEJRAH:
				infernoNPC = new InfernoNPC(npc, index, 25);
				break;
			case NpcID.JALAK:
				infernoNPC = new InfernoNPC(npc, index, 40);
				break;
			case NpcID.JALAKREKXIL:
			case NpcID.JALAKREKMEJ:
			case NpcID.JALAKREKKET:
				infernoNPC = new InfernoNPC(npc, index, 15);
				break;
			case NpcID.JALIMKOT:
				infernoNPC = new InfernoNPC(npc, index, 75);
				break;
			case NpcID.JALXIL:
				infernoNPC = new InfernoNPC(npc, index, 125);
				break;
			case NpcID.JALZEK:
				infernoNPC = new InfernoNPC(npc, index, 220);
				break;
			case NpcID.JALTOKJAD:
				infernoNPC = new InfernoNPC(npc, index, 350);
				break;
			case NpcID.TZKALZUK:
				infernoNPC = new InfernoNPC(npc, index, 1200);
				break;
			case NpcID.JALMEJJAK:
				infernoNPC = new InfernoNPC(npc, index, 75);
				break;
		}
		if (infernoNPC != null)
			this.infernoNPCs.add(infernoNPC);
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
		if (!isInInferno)
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

			for (InfernoNPC infernoNPC : this.infernoNPCs)
			{
				if (infernoNPC.getNpcIndex() != npcIndex)
					continue;

				infernoNPC.setQueuedDamage(infernoNPC.getQueuedDamage() + damage);
				if (infernoNPC.getHp() - infernoNPC.getQueuedDamage() <= 0)
				{
					if (deadInfernoNPCs.stream().noneMatch(deadInfernoNPCs -> deadInfernoNPCs.getNpcIndex() == npcIndex))
					{
						deadInfernoNPCs.add(infernoNPC);
					}
				}
			}
		}
	}

	private boolean isInInferno()
	{
		return client.getMapRegions() != null && ArrayUtils.contains(client.getMapRegions(), INFERNO_REGION_ID);
	}

	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (renderable instanceof NPC)
		{
			return deadInfernoNPCs.stream().noneMatch(infernoNPC -> infernoNPC.getNpcIndex() == ((NPC) renderable).getIndex());
		}

		return true;
	}

	@Provides
	InfernoDeathIndicatorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InfernoDeathIndicatorConfig.class);
	}
}
