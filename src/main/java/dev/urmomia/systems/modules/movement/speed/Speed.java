package dev.urmomia.systems.modules.movement.speed;

import meteordevelopment.orbit.EventHandler;
import dev.urmomia.events.entity.player.PlayerMoveEvent;
import dev.urmomia.events.packets.PacketEvent;
import dev.urmomia.events.world.TickEvent;
import dev.urmomia.settings.*;
import dev.urmomia.systems.modules.Categories;
import dev.urmomia.systems.modules.Module;
import dev.urmomia.systems.modules.Modules;
import dev.urmomia.systems.modules.movement.AutoJump;
import dev.urmomia.systems.modules.movement.speed.modes.NCP;
import dev.urmomia.systems.modules.movement.speed.modes.Vanilla;
import dev.urmomia.systems.modules.world.Timer;
import dev.urmomia.utils.player.PlayerUtils;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public class Speed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVanilla = settings.createGroup("Vanilla");
    private final SettingGroup sgNCP = settings.createGroup("NCP");

    // Main

    public final Setting<SpeedModes> speedMode = sgGeneral.add(new EnumSetting.Builder<SpeedModes>()
            .name("mode")
            .description("The method of applying speed.")
            .defaultValue(SpeedModes.Vanilla)
            .onModuleActivated(speedModesSetting -> onSpeedModeChanged(speedModesSetting.get()))
            .onChanged(this::onSpeedModeChanged)
            .build()
    );

    public final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
            .name("timer")
            .description("Timer override.")
            .defaultValue(1)
            .min(0.01)
            .sliderMin(0.01)
            .sliderMax(10)
            .build()
    );

    public final Setting<Boolean> inLiquids = sgGeneral.add(new BoolSetting.Builder()
            .name("in-liquids")
            .description("Uses speed when in lava or water.")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> whenSneaking = sgGeneral.add(new BoolSetting.Builder()
            .name("when-sneaking")
            .description("Uses speed when sneaking.")
            .defaultValue(false)
            .build()
    );

    // Vanilla

    public final Setting<Double> speed = sgVanilla.add(new DoubleSetting.Builder()
            .name("speed")
            .description("How fast you want to go in blocks per second.")
            .visible(() -> speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(5.6)
            .min(0)
            .sliderMax(50)
            .build()
    );

    public final Setting<Boolean> onlyOnGround = sgVanilla.add(new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Uses speed only when standing on a block.")
            .visible(() -> speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> applySpeedPotions = sgVanilla.add(new BoolSetting.Builder()
            .name("apply-speed-potions")
            .description("Applies the speed effect via potions.")
            .visible(() -> speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> jump = sgVanilla.add(new BoolSetting.Builder()
            .name("jump")
            .description("Automatically jumps.")
            .visible(() -> speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(false)
            .build()
    );

    public final Setting<AutoJump.Mode> jumpMode = sgVanilla.add(new EnumSetting.Builder<AutoJump.Mode>()
            .name("mode")
            .description("The method of jumping.")
            .visible(() -> speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(AutoJump.Mode.Jump)
            .build()
    );

    public final Setting<Double> hopHeight = sgVanilla.add(new DoubleSetting.Builder()
            .name("hop-height")
            .description("The distance that lowhop moves you.")
            .visible(() -> speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(0.25)
            .min(0)
            .sliderMax(2)
            .build()
    );

    public final Setting<AutoJump.JumpWhen> jumpIf = sgVanilla.add(new EnumSetting.Builder<AutoJump.JumpWhen>()
            .name("jump-when")
            .description("Jumps when you are doing said action.")
            .visible(() -> speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(AutoJump.JumpWhen.Walking)
            .build()
    );


    // NCP

    public final Setting<Double> ncpSpeed = sgNCP.add(new DoubleSetting.Builder()
            .name("speed")
            .description("How fast you go.")
            .visible(() -> speedMode.get() == SpeedModes.NCP)
            .defaultValue(1.6)
            .min(0)
            .sliderMax(3)
            .build()
    );

    public final Setting<Boolean> ncpSpeedLimit = sgNCP.add(new BoolSetting.Builder()
            .name("speed-limit")
            .description("Limits your speed on servers with very strict anticheats.")
            .visible(() -> speedMode.get() == SpeedModes.NCP)
            .defaultValue(false)
            .build()
    );

    private SpeedMode currentMode;

    public Speed() {
        super(Categories.Movement, "speed", "Modifies your movement speed when moving on the ground.");

        onSpeedModeChanged(speedMode.get());
    }

    @Override
    public void onActivate() {
        currentMode.onActivate();
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
        currentMode.onDeactivate();
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (event.type != MovementType.SELF || mc.player.isFallFlying() || mc.player.isClimbing() || mc.player.getVehicle() != null) return;
        if (!whenSneaking.get() && mc.player.isSneaking()) return;
        if (onlyOnGround.get() && !mc.player.isOnGround()) return;
        if (!inLiquids.get() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;

        Modules.get().get(Timer.class).setOverride(PlayerUtils.isMoving() ? timer.get() : Timer.OFF);

        currentMode.onMove(event);
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.player.isFallFlying()
                || mc.player.isClimbing()
                || mc.player.getVehicle() != null
                || (!whenSneaking.get() && mc.player.isSneaking())
                || (onlyOnGround.get() && !mc.player.isOnGround())
                || (!inLiquids.get() && (mc.player.isTouchingWater() || mc.player.isInLava()))) {
            return;
        }

        currentMode.onTick();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) currentMode.onRubberband();
    }

    private void onSpeedModeChanged(SpeedModes mode) {
        switch (mode) {
            case Vanilla:   currentMode = new Vanilla(); break;
            case NCP:       currentMode = new NCP(); break;
        }
    }

    @Override
    public String getInfoString() {
        return currentMode.getHudString();
    }
}