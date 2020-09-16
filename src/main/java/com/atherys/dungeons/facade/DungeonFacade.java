package com.atherys.dungeons.facade;

import com.atherys.dungeons.AtherysDungeons;
import com.atherys.dungeons.AtherysDungeonsConfig;
import com.atherys.dungeons.config.DungeonConfig;
import com.atherys.dungeons.exception.DungeonsCommandException;
import com.atherys.dungeons.model.Dungeon;
import com.atherys.dungeons.model.InstanceSettings;
import com.atherys.dungeons.model.QueuedParty;
import com.atherys.dungeons.service.DungeonInstantiationService;
import com.atherys.party.AtherysParties;
import com.atherys.party.entity.Party;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

@Singleton
public class DungeonFacade {

    @Inject
    private AtherysDungeonsConfig dungeonsConfig;

    @Inject
    private DungeonInstantiationService dungeonInstantiationService;

    private Map<String, Dungeon> dungeons = new HashMap<>();

    private Queue<QueuedParty> queue = new LinkedList<>();

    private Task queueChecker;

    public void init() {
        dungeonsConfig.DUNGEONS.forEach(this::registerDungeon);

        queueChecker = Sponge.getScheduler().createTaskBuilder()
                .async()
                .interval(1, TimeUnit.SECONDS)
                .execute(this::updateQueue)
                .submit(AtherysDungeons.getInstance());
    }

    public void queuePlayerParty(Player source, Dungeon dungeon) throws DungeonsCommandException {
        Party playerParty = getPlayerParty(source);

        if (dungeon == null) {
            throw new DungeonsCommandException("Invalid dungeon!");
        }

        QueuedParty queuedParty = new QueuedParty();
        queuedParty.setParty(playerParty);
        queuedParty.setDungeon(dungeon);

        queue.add(queuedParty);

        AtherysParties.getInstance().getPartyMessagingFacade().sendInfoToParty(playerParty, "Your party has been queued for the dungeon ", dungeon.getName());
    }

    public void dequePlayerParty(Player source) throws DungeonsCommandException {
        Party playerParty = getPlayerParty(source);

        QueuedParty queuedParty = new QueuedParty();
        queuedParty.setParty(playerParty);

        queue.removeIf((queued) -> queuedParty.getParty().getId().equals(queued.getParty().getId()));
        AtherysParties.getInstance().getPartyMessagingFacade().sendInfoToParty(playerParty, "Your party has been dequeued from the dungeon queue!");
    }

    public Map<String, Dungeon> getDungeons() {
        return dungeons;
    }

    private void updateQueue() {
        int numberOfAvailableInstances = dungeonInstantiationService.fetchNumberOfAvailableInstances();

        if ( numberOfAvailableInstances < 1 ) {
            return; // there are no instances available, it is pointless to continue
        }

        QueuedParty queuedParty = queue.poll();

        if (queuedParty == null) {
            return; // the queue was empty and no party could be retrieved
        }

        AtherysParties.getInstance().getPartyMessagingFacade().sendInfoToParty(queuedParty.getParty(), "Prepare to enter ", queuedParty.getDungeon().getName(), "!");

        // TODO: Provide some sort of time interval, with a warning, before players are reconnected.
        // TODO: Decrement number of available instances by 1, ensuring that if the party falls apart before the dungeon instance is ready, it can be re-incremented
        // TODO: Create dungeon server instance and reconnect players there
    }

    private void registerDungeon(DungeonConfig dungeonConfig) {
        Dungeon dungeon = new Dungeon();
        dungeon.setPackId(dungeonConfig.PACK_ID);
        dungeon.setName(dungeonConfig.NAME);
        dungeon.setMinPlayers(dungeonConfig.MIN_PLAYERS);
        dungeon.setMaxPlayer(dungeonConfig.MAX_PLAYERS);

        InstanceSettings settings = new InstanceSettings();
        settings.setMaxMemory(dungeonConfig.INSTANCE_CONFIG.MAX_MEMORY);

        dungeon.setInstanceSettings(settings);

        dungeons.put(dungeon.getName(), dungeon);
    }

    private Party getPlayerParty(Player player) throws DungeonsCommandException {
        return AtherysParties.getInstance()
                .getPartyFacade()
                .getPlayerParty(player)
                .orElseThrow(() -> new DungeonsCommandException(
                        "You cannot queue for a dungeon unless you are in a party!"
                ));
    }

}