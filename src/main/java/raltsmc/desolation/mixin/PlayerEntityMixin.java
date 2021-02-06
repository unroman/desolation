package raltsmc.desolation.mixin;

import me.sargunvohra.mcmods.autoconfig1u.AutoConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import raltsmc.desolation.Desolation;
import raltsmc.desolation.DesolationMod;
import raltsmc.desolation.config.DesolationConfig;
import raltsmc.desolation.entity.effect.DesolationStatusEffects;
import raltsmc.desolation.init.client.DesolationClient;
import raltsmc.desolation.registry.DesolationItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin extends LivingEntity implements PlayerEntityAccess {
    public int cinderDashCooldownMax = 200;
    public int cinderDashCooldown = 200;
    public boolean isDashing = false;
    public int dashLengthMax = 10;
    public int dashLength = 0;
    public Vec3d dashVector;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo info) {
        // ugly but works =)
        // TODO make not ugly but still work (biome tags????)
        if ((Objects.equals(this.world.getRegistryManager().get(Registry.BIOME_KEY).getId(this.world.getBiome(this.getBlockPos())), Desolation.id("charred_forest"))
                || Objects.equals(this.world.getRegistryManager().get(Registry.BIOME_KEY).getId(this.world.getBiome(this.getBlockPos())), Desolation.id("charred_forest_small"))
                || Objects.equals(this.world.getRegistryManager().get(Registry.BIOME_KEY).getId(this.world.getBiome(this.getBlockPos())), Desolation.id("charred_forest_clearing")))
                && this.getY() >= world.getSeaLevel() - 10) {
            if (!this.world.isClient) {
                if (this.getEquippedStack(EquipmentSlot.HEAD).getItem() != DesolationItems.MASK
                && this.getEquippedStack(EquipmentSlot.HEAD).getItem() != DesolationItems.MASK_GOGGLES) {
                //&& this.config.inflictBiomeDebuffs) {
                    this.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 308));
                    this.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 308));
                }
            }
        }
        // TODO make goggles only stop blindness from ash rather than all blindness
        if (this.hasStatusEffect(StatusEffects.BLINDNESS)
                && (this.getEquippedStack(EquipmentSlot.HEAD).getItem() == DesolationItems.GOGGLES
                || this.getEquippedStack(EquipmentSlot.HEAD).getItem() == DesolationItems.MASK_GOGGLES)) {
            this.removeStatusEffect(StatusEffects.BLINDNESS);
        }

        if (this.hasStatusEffect(DesolationStatusEffects.CINDER_SOUL)) {
            if (cinderDashCooldown < cinderDashCooldownMax) {
                ++cinderDashCooldown;
                if (cinderDashCooldown == cinderDashCooldownMax) {

                    PacketByteBuf buf = PacketByteBufs.create();
                    if (ClientPlayNetworking.canSend(DesolationMod.CINDER_SOUL_READY_PACKET_ID)) {
                        ClientPlayNetworking.send(DesolationMod.CINDER_SOUL_READY_PACKET_ID, buf);
                    }
                    this.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1F, 1.2F);
                }
            }

            if (random.nextDouble() < 0.3) {
                PacketByteBuf buf = PacketByteBufs.create();
                if (ClientPlayNetworking.canSend(DesolationMod.CINDER_SOUL_TICK_PACKET_ID)) {
                    ClientPlayNetworking.send(DesolationMod.CINDER_SOUL_TICK_PACKET_ID, buf);
                }
                if (random.nextDouble() < 0.25) {
                    world.playSound((PlayerEntity)null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_FIRE_AMBIENT,SoundCategory.AMBIENT, .8F, 1F);
                }
            }

            if (DesolationClient.cinderDashBinding.isPressed() && cinderDashCooldown >= cinderDashCooldownMax) {
                dashVector = this.getRotationVector().normalize().multiply(0.75);
                cinderDashCooldown = 0;
                isDashing = true;
                world.playSound((PlayerEntity)null, this.getX(), this.getY(), this.getZ(),SoundEvents.ENTITY_ENDER_DRAGON_GROWL,SoundCategory.PLAYERS, 1F, 1.6F);
            }

            if (isDashing) {
                if (dashLength < dashLengthMax) {
                    if (this.world.isClient) {
                        this.setVelocity(dashVector);
                        this.velocityDirty = true;
                    }
                    this.setPose(EntityPose.SWIMMING);
                    this.fallDistance = 0;
                    ++dashLength;
                } else {
                    isDashing = false;
                    dashLength = 0;
                }
            }
        } else {
            dashLength = 0;
            isDashing = false;
        }
    }

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getHealth()F", ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD)
    private void doFireAttackA(Entity target, CallbackInfo info, float f, float h, boolean bl, boolean bl2, int j, boolean bl3, boolean bl4, float k, boolean bl5, int l) {
        if (l <= 0 && !target.isOnFire() && this.hasStatusEffect(DesolationStatusEffects.CINDER_SOUL)) {
            bl5 = true;
            target.setOnFireFor(1);
        }
    }

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;increaseStat(Lnet/minecraft/util/Identifier;I)V", ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD)
    private void doFireAttackB(Entity target, CallbackInfo info, float f, float h, boolean bl, boolean bl2, int j, boolean bl3, boolean bl4, float k, boolean bl5, int l, float n) {
        if (l <= 0 && this.hasStatusEffect(DesolationStatusEffects.CINDER_SOUL)) {
            target.setOnFireFor(6);
        }
    }

    @Shadow
    @Override
    public Iterable<ItemStack> getArmorItems() {
        return null;
    }

    @Shadow
    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        return null;
    }

    @Shadow
    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {

    }

    @Shadow
    @Override
    public Arm getMainArm() {
        return null;
    }
}
