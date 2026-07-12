package com.rfizzle.respite.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rfizzle.respite.Respite;
import com.rfizzle.respite.config.RespiteConfig;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.StringRepresentable;

/**
 * The {@code respite:feature_enabled} resource condition — gates a datapack
 * entry (a recipe, its unlock advancement) on a feature toggle in
 * {@code config/respite.json}. Evaluated at datapack load, so a config change
 * takes effect on the next {@code /reload}. An unknown feature name is a codec
 * parse error, which fails the entry loudly at load rather than silently
 * enabling or disabling it.
 */
public record FeatureEnabledCondition(Feature feature) implements ResourceCondition {

    public static final MapCodec<FeatureEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Feature.CODEC.fieldOf("feature").forGetter(FeatureEnabledCondition::feature))
            .apply(instance, FeatureEnabledCondition::new));

    public static final ResourceConditionType<FeatureEnabledCondition> TYPE =
            ResourceConditionType.create(Respite.id("feature_enabled"), CODEC);

    /** The gateable feature toggles, each bound to its config field. */
    public enum Feature implements StringRepresentable {
        CHRONOMETER("chronometer"),
        CAFFEINATED_BREW("caffeinated_brew"),
        BEDROLL("bedroll");

        public static final com.mojang.serialization.Codec<Feature> CODEC =
                StringRepresentable.fromEnum(Feature::values);

        private final String name;

        Feature(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The pure config read — the seam the unit tests drive. */
        public boolean enabledIn(RespiteConfig config) {
            return switch (this) {
                case CHRONOMETER -> config.enableChronometer;
                case CAFFEINATED_BREW -> config.enableCaffeinatedBrew;
                case BEDROLL -> config.enableBedroll;
            };
        }
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(HolderLookup.Provider registryLookup) {
        return feature.enabledIn(RespiteConfig.get());
    }
}
