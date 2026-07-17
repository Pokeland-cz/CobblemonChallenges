package com.github.kuramastone.cobblemonChallenges.challenges.requirements;

import com.github.kuramastone.bUtilities.yaml.YamlConfig;
import com.github.kuramastone.bUtilities.yaml.YamlKey;
import com.github.kuramastone.cobblemonChallenges.CobbleChallengeMod;
import com.github.kuramastone.cobblemonChallenges.challenges.Challenge;
import com.github.kuramastone.cobblemonChallenges.events.BlockBreakEvent;
import com.github.kuramastone.cobblemonChallenges.player.PlayerProfile;
import com.github.kuramastone.cobblemonChallenges.scoreboard.ChallengeScoreboard;
import com.github.kuramastone.cobblemonChallenges.utils.StringUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.UUID;

public class MineBlockRequirement implements Requirement {
    public static final String ID = "Mine_Block";

    @YamlKey("type")
    public String blockType = "any";
    @YamlKey("amount")
    private int amount = 1;

    @YamlKey("preventSilkTouch")
    public boolean preventSilkTouch = true;

    public MineBlockRequirement() {
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
        return new MineBlockProgression(profile, this, parentChallenge);
    }

    // Static nested Progression class
    public static class MineBlockProgression implements Progression<BlockBreakEvent> {

        private PlayerProfile profile;
        public final MineBlockRequirement requirement;
        private int progressAmount;
        private Challenge parentChallenge;

        public MineBlockProgression(PlayerProfile profile, MineBlockRequirement requirement, Challenge parentChallenge) {
            this.profile = profile;
            this.requirement = requirement;
            this.parentChallenge = parentChallenge;
            this.progressAmount = 0;
        }

        @Override
        public Class<BlockBreakEvent> getType() {
            return BlockBreakEvent.class;
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
                    progressAmount = Math.min(progressAmount, this.requirement.amount);

                    ChallengeScoreboard.updateIfTracking(profile, parentChallenge.getName());
                }
            }
        }

        @Override
        public boolean meetsCriteria(BlockBreakEvent event) {

            String itemName = BuiltInRegistries.BLOCK.wrapAsHolder(event.getBlockState().getBlock()).getRegisteredName();

            if (!StringUtils.doesStringContainCategory(requirement.blockType.split("/"), itemName)) {
                return false;
            }

            // --- NEW: 1.21+ Silk Touch Prevention Check ---
            if (requirement.preventSilkTouch && event.getPlayer() != null) {
                ItemStack heldItem = event.getPlayer().getMainHandItem();

                // 1. Get the enchantment registry from the player's world level
                Registry<Enchantment> enchantmentRegistry =
                        event.getPlayer().level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);

                // 2. Get the Holder for Silk Touch using the ResourceKey
                Holder<Enchantment> silkTouchHolder =
                        enchantmentRegistry.getHolderOrThrow(Enchantments.SILK_TOUCH);

                // 3. Check the item's level using the Holder
                int silkTouchLevel = EnchantmentHelper.getItemEnchantmentLevel(
                        silkTouchHolder,
                        heldItem
                );

                return silkTouchLevel <= 0; // Do not count the block break if Silk Touch was used
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