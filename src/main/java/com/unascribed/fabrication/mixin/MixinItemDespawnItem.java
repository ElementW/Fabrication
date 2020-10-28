package com.unascribed.fabrication.mixin;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.unascribed.fabrication.ParsedTime;
import com.unascribed.fabrication.Resolvable;
import com.unascribed.fabrication.features.FeatureItemDespawn;
import com.unascribed.fabrication.interfaces.SetFromPlayerDeath;
import com.unascribed.fabrication.support.EligibleIf;
import com.unascribed.fabrication.support.SpecialEligibility;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

@Mixin(ItemEntity.class)
@EligibleIf(specialConditions=SpecialEligibility.ITEM_DESPAWN_NOT_ALL_UNSET)
public abstract class MixinItemDespawnItem extends Entity implements SetFromPlayerDeath {

	public MixinItemDespawnItem(EntityType<?> type, World world) {
		super(type, world);
	}

	private int fabrication$extraTime;
	private boolean fabrication$invincible;
	private boolean fabrication$fromPlayerDeath;
	
	@Shadow
	private int age;
	@Shadow
	private UUID thrower;
	
	@Shadow
	public abstract ItemStack getStack();
	
	@Inject(at=@At("HEAD"), method="tick()V")
	public void tickHead(CallbackInfo ci) {
		if (fabrication$extraTime > 0) {
			fabrication$extraTime--;
			age--;
		}
		if (getPos().y < -32) {
			if (fabrication$invincible) {
				teleport(getPos().x, 1, getPos().z);
				setVelocity(0,0,0);
				if (!world.isClient) {
					((ServerWorld)world).getChunkManager().sendToNearbyPlayers(this, new EntityPositionS2CPacket(this));
					((ServerWorld)world).getChunkManager().sendToNearbyPlayers(this, new EntityVelocityUpdateS2CPacket(this));
				}
			}
		}
	}
	
	@Inject(at=@At("HEAD"), method="damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", cancellable=true)
	public void damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
		if (fabrication$invincible) {
			ci.setReturnValue(false);
		}
	}
	
	@Inject(at=@At("TAIL"), method="setStack(Lnet/minecraft/item/ItemStack;)V")
	public void setStack(ItemStack stack, CallbackInfo ci) {
		calculateDespawn();
	}
	
	@Inject(at=@At("TAIL"), method="setThrower(Ljava/util/UUID;)V")
	public void setThrower(UUID id, CallbackInfo ci) {
		calculateDespawn();
	}
	
	@Override
	public void fabrication$setFromPlayerDeath(boolean b) {
		fabrication$fromPlayerDeath = b;
		calculateDespawn();
	}

	@Unique
	private void calculateDespawn() {
		if (world.isClient) return;
		ItemStack stack = getStack();
		ParsedTime time = ParsedTime.UNSET;
		ParsedTime itemTime = FeatureItemDespawn.itemDespawns.get(Resolvable.mapKey(stack.getItem(), Registry.ITEM));
		if (itemTime != null) {
			time = itemTime;
		}
		if (!time.priority) {
			for (Enchantment e : EnchantmentHelper.get(stack).keySet()) {
				if (e.isCursed()) {
					if (FeatureItemDespawn.curseDespawn.overshadows(time)) {
						time = FeatureItemDespawn.curseDespawn;
					}
				} else {
					if (FeatureItemDespawn.normalEnchDespawn.overshadows(time)) {
						time = FeatureItemDespawn.normalEnchDespawn;
					}
					if (e.isTreasure()) {
						if (FeatureItemDespawn.treasureDespawn.overshadows(time)) {
							time = FeatureItemDespawn.treasureDespawn;
						}
					}
				}
				ParsedTime enchTime = FeatureItemDespawn.enchDespawns.get(Resolvable.mapKey(e, Registry.ENCHANTMENT));
				if (enchTime != null && enchTime.overshadows(time)) {
					time = enchTime;
				}
			}
			if (stack.hasTag()) {
				for (Map.Entry<String, ParsedTime> en : FeatureItemDespawn.nbtBools.entrySet()) {
					if (stack.getTag().getBoolean(en.getKey())) {
						if (en.getValue().overshadows(time)) {
							time = en.getValue();
						}
					}
				}
			}
		}
		if (fabrication$fromPlayerDeath && FeatureItemDespawn.playerDeathDespawn.overshadows(time)) {
			time = FeatureItemDespawn.playerDeathDespawn;
		}
		if (time == ParsedTime.UNSET) {
			time = thrower == null ? FeatureItemDespawn.dropsDespawn : FeatureItemDespawn.defaultDespawn;
		}
//		System.out.println(stack+": "+time);
		int origAge = age;
		fabrication$invincible = false;
		if (time == ParsedTime.FOREVER) {
			fabrication$extraTime = 0;
			age = -32768;
		} else if (time == ParsedTime.INVINCIBLE) {
			fabrication$extraTime = 0;
			age = -32768;
			fabrication$invincible = true;
		} else if (time == ParsedTime.INSTANTLY) {
			remove();
		} else if (time == ParsedTime.UNSET) {
			fabrication$extraTime = 0;
		} else {
			int extra = time.timeInTicks-6000;
			extra -= origAge;
			if (extra < 0) {
				age = -extra;
				fabrication$extraTime = 0;
			} else {
				age = 0;
				fabrication$extraTime = extra;
			}
		}
//		System.out.println("age: "+age);
//		System.out.println("extraTime: "+fabrication$extraTime);
//		System.out.println("fromPlayerDeath: "+fabrication$fromPlayerDeath);
//		System.out.println("invincible: "+fabrication$invincible);
	}
	
}