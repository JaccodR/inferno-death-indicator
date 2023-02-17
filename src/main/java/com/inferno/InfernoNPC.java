package com.inferno;

import com.sun.tools.javac.comp.Infer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.NPC;

@Getter(AccessLevel.PACKAGE)
public class InfernoNPC
{
    private final NPC npc;
    private final int npcIndex;
    @Setter
    private int hp;
    @Setter
    private int queuedDamage;
    @Setter
    private int hidden;

    InfernoNPC(NPC npc, int npcIndex, int hp)
    {
        this.npc = npc;
        this.npcIndex = npcIndex;
        this.hp = hp;
        this.queuedDamage = 0;
        this.hidden = 0;
    }
}
