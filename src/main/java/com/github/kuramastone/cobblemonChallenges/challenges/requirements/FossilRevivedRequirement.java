package com.github.kuramastone.cobblemonChallenges.challenges.requirements;

import com.cobblemon.mod.common.api.events.pokemon.FossilRevivedEvent;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.kuramastone.bUtilities.yaml.YamlConfig;
import com.github.kuramastone.bUtilities.yaml.YamlKey;
import com.github.kuramastone.cobblemonChallenges.CobbleChallengeMod;
import com.github.kuramastone.cobblemonChallenges.challenges.Challenge;
import com.github.kuramastone.cobblemonChallenges.player.PlayerProfile;
import com.github.kuramastone.cobblemonChallenges.scoreboard.ChallengeScoreboard;
import com.github.kuramastone.cobblemonChallenges.utils.CobblemonUtils;
import com.github.kuramastone.cobblemonChallenges.utils.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class FossilRevivedRequirement implements Requirement {
    public static final String ID = "fossils_revived";

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
    @YamlKey("is_paradox")
    private boolean is_paradox = false;
    @YamlKey("is_legendary")
    private boolean is_legendary = false;
    @YamlKey("is_ultra_beast")
    private boolean is_ultra_beast = false;
    @YamlKey("is_mythical")
    private boolean is_mythical = false;

    public FossilRevivedRequirement() {
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
        return new FossilsRevivedProgression(profile, this, parentChallenge);
    }

    // Static nested Progression class
    public static class FossilsRevivedProgression implements Progression<FossilRevivedEvent> {

        private PlayerProfile profile;
        public FossilRevivedRequirement requirement;
        private int progressAmount;
        private Challenge parentChallenge;

        public FossilsRevivedProgression(PlayerProfile profile, FossilRevivedRequirement requirement, Challenge parentChallenge) {
            this.profile = profile;
            this.requirement = requirement;
            this.parentChallenge = parentChallenge;
            this.progressAmount = 0;
        }

        @Override
        public Class<FossilRevivedEvent> getType() {
            return FossilRevivedEvent.class;
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
        public boolean meetsCriteria(FossilRevivedEvent event) {

            Pokemon pokemon = event.getPokemon();
            String pokename = pokemon.getSpecies().getName();
            boolean shiny = pokemon.getShiny();
            List<ElementalType> types = StreamSupport.stream(pokemon.getTypes().spliterator(), false).collect(Collectors.toUnmodifiableList());
            String ballName = event.getPokemon().getCaughtBall().getName().toString();
            long time_of_day = event.getPlayer().level().getDayTime();
            boolean is_paradox = pokemon.hasLabels(CobblemonPokemonLabels.PARADOX);
            boolean is_legendary = pokemon.isLegendary() || pokemon.isMythical();
            boolean is_ultra_beast = pokemon.isUltraBeast();
            boolean is_mythical = pokemon.isMythical();

            if (!StringUtils.doesStringContainCategory(requirement.pokename.split("/"), pokename)) {
                return false;
            }

            if (requirement.shiny && !shiny) {
                return false;
            }

            if (types.stream().noneMatch(pokeType -> StringUtils.doesStringContainCategory(requirement.type.split("/"), pokeType.getName()))) {
                return false;
            }

            if (!StringUtils.doesStringContainCategory(requirement.ball.split("/"), ballName)) {
                return false;
            }

            if (!CobblemonUtils.doesDaytimeMatch(time_of_day, requirement.time_of_day)) {
                return false;
            }

            boolean hasAnyRequirement = requirement.is_paradox || requirement.is_legendary || requirement.is_ultra_beast || requirement.is_mythical;

            boolean passesAny =
                (requirement.is_paradox && is_paradox) ||
                (requirement.is_legendary && is_legendary) ||
                (requirement.is_ultra_beast && is_ultra_beast) ||
                (requirement.is_mythical && is_mythical);

            if (hasAnyRequirement && !passesAny) {
                return false;
            }

            return true;
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