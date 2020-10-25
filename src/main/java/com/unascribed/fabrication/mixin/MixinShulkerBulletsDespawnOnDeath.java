package com.unascribed.fabrication.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.unascribed.fabrication.support.EligibleIf;
import com.unascribed.fabrication.support.MixinConfigPlugin.RuntimeChecks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;

@Mixin(ShulkerBulletEntity.class)
@EligibleIf(configEnabled="*.shulker_bullets_despawn_on_death")
public abstract class MixinShulkerBulletsDespawnOnDeath {

	@Inject(at=@At("HEAD"), method="tick()V", cancellable=true)
	public void tick(CallbackInfo ci) {
		Object self = this;
		if (RuntimeChecks.check("*.shulker_bullets_despawn_on_death") && !((Entity)self).world.isClient) {
			Entity owner = ((ProjectileEntity)self).getOwner();
			if (owner == null || !owner.isAlive()) {
				((Entity)self).remove();
				ci.cancel();
			}
		}
	}
	
}