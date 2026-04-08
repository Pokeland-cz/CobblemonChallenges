package com.github.kuramastone.cobblemonChallenges.challenges.requirements;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.kuramastone.bUtilities.yaml.YamlConfig;
import com.github.kuramastone.bUtilities.yaml.YamlKey;
import com.github.kuramastone.cobblemonChallenges.CobbleChallengeMod;
import com.github.kuramastone.cobblemonChallenges.challenges.Challenge;
import com.github.kuramastone.cobblemonChallenges.player.PlayerProfile;
import com.github.kuramastone.cobblemonChallenges.scoreboard.ChallengeScoreboard;
import com.github.kuramastone.cobblemonChallenges.utils.CobblemonUtils;
import com.github.kuramastone.cobblemonChallenges.utils.StringUtils;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DefeatBattlerRequirement implements Requirement {
    public static final String ID = "Defeat_Battler";

    @YamlKey("pokename")
    public String pokename = "any";
    @YamlKey("amount")
    private int amount = 1;

    @YamlKey("shiny")
    private boolean shiny = false;
    @YamlKey("type")
    public String type = "any";
    @YamlKey("ball")
    private String ball = "any";
    @YamlKey("time_of_day")
    private String time_of_day = "any";
    @YamlKey("is_legendary")
    private boolean is_legendary = false;
    @YamlKey("is_ultra_beast")
    private boolean is_ultra_beast = false;
    @YamlKey("is_mythical")
    private boolean is_mythical = false;
    @YamlKey("effectiveness")
    private String effectiveness = "any";
    @YamlKey("npc-player-gymleader-wild")
    private String enemyType = "any";

    public DefeatBattlerRequirement() {
    }

    public Requirement load(YamlConfig section) {
        return YamlConfig.loadFromYaml(this, section);
    }

    // The requirement name now returns the ID used to recognize it
    @Override
    public String getName() {
        return ID;
    }

    @Override
    public Progression<?> buildProgression(PlayerProfile profile, Challenge parentChallenge) {
        return new DefeatPokemonProgression(profile, this, parentChallenge);
    }

    // Static nested Progression class
    public static class DefeatPokemonProgression implements Progression<BattleVictoryEvent> {

        private PlayerProfile profile;
        public DefeatBattlerRequirement requirement;
        private int progressAmount;
        private Challenge parentChallenge;

        public DefeatPokemonProgression(PlayerProfile profile, DefeatBattlerRequirement requirement, Challenge parentChallenge) {
            this.profile = profile;
            this.requirement = requirement;
            this.parentChallenge = parentChallenge;
            this.progressAmount = 0;
        }

        @Override
        public Class<BattleVictoryEvent> getType() {
            return BattleVictoryEvent.class;
        }


        @Override
        public boolean isCompleted() {
            return progressAmount >= requirement.amount;
        }

        @Override
        public void progress(Object obj) {
            if (matchesMethod(obj)) {
                if (meetsCriteria(getType().cast(obj))) {
                    progressAmount++;

                    ChallengeScoreboard.updateIfTracking(profile, parentChallenge.getName());
                }
            }
        }

        @Override
        public boolean meetsCriteria(BattleVictoryEvent event) {
            BattleActor player = event.getBattle().getActor(profile.getUUID());

            // make sure battle uses this player
            if(player == null)
                return false;

            // return if the player didnt win
            if(!event.getWinners().contains(player))
                return false;

            // return if it was a wild catch
            if (event.getWasWildCapture())
                return false;

            // check npc/player/gymleader/wild
            StringBuilder enemyType = new StringBuilder();
            List<BattlePokemon> enemyPokemon = new ArrayList<>();
            for (BattleActor participant : event.getLosers()) {
                enemyPokemon.addAll(participant.getPokemonList());
                if (participant instanceof EntityBackedBattleActor<?> entityBackedBattleActor) {
                    if (entityBackedBattleActor.getEntity() instanceof Player)
                        enemyType.append("player/");
                    else
                        enemyType.append("wild/");
                }
                else if (participant instanceof AIBattleActor aiBattleActor) {
                    enemyType.append("npc/");
                }
                else {
                    enemyType.append("wild/");
                }
            }

            // check if acceptable requirements contains any of the enemytypes we inserted
            if (!StringUtils.doesStringContainCategory(requirement.enemyType.split("/"), enemyType.toString())) {
                return false;
            }

            for (BattlePokemon battlePokemon : enemyPokemon) {
                Pokemon pokemon = battlePokemon.getOriginalPokemon();
                String pokename = pokemon.getSpecies().getName();
                boolean shiny = pokemon.getShiny();
                List<ElementalType> types = StreamSupport.stream(pokemon.getTypes().spliterator(), false).collect(Collectors.toUnmodifiableList());
                String ballName = pokemon.getCaughtBall().getName().toString();
                long time_of_day = CobbleChallengeMod.getMinecraftServer().getPlayerList().getPlayer(player.getUuid()).level().getDayTime();
                boolean is_legendary = pokemon.isLegendary() || pokemon.isMythical();
                boolean is_ultra_beast = pokemon.isUltraBeast();
                boolean is_mythical = pokemon.isMythical();

                if (!StringUtils.doesStringContainCategory(requirement.pokename.split("/"), pokename)) {
                    continue;
                }

                if (requirement.shiny && !shiny) {
                    continue;
                }

                if (types.stream().noneMatch(pokeType -> StringUtils.doesStringContainCategory(requirement.type.split("/"), pokeType.getName()))) {
                    continue;
                }

                if (!StringUtils.doesStringContainCategory(requirement.ball.split("/"), ballName)) {
                    continue;
                }

                if (!CobblemonUtils.doesDaytimeMatch(time_of_day, requirement.time_of_day)) {
                    continue;
                }

                boolean hasAnyRequirement = requirement.is_legendary || requirement.is_ultra_beast || requirement.is_mythical;

                boolean passesAny =
                    (requirement.is_legendary && is_legendary) ||
                    (requirement.is_ultra_beast && is_ultra_beast) ||
                    (requirement.is_mythical && is_mythical);

                if (hasAnyRequirement && !passesAny) {
                    continue;
                }

                return true;
            }

            return false;
        }

        @Override
        public boolean matchesMethod(Object obj) {
            return getType().isInstance(obj);
        }

        @Override
        public double getPercentageComplete() {
            return (double) progressAmount / requirement.amount;
        }

        @Override
        public Progression loadFrom(UUID uuid, YamlConfig configurationSection) {
            this.progressAmount = configurationSection.getInt("progressAmount");
            return this;
        }

        @Override
        public void writeTo(YamlConfig configurationSection) {
            configurationSection.set("progressAmount", progressAmount);
        }

        @Override
        public String getProgressString() {
            return CobbleChallengeMod.instance.getAPI().getMessage("challenges.progression-string",
                    "{current}", String.valueOf(this.progressAmount),
                    "{target}", String.valueOf(this.requirement.amount)).getText();
        }
    }
}