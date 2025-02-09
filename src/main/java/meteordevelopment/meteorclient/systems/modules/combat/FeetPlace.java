package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public class FeetPlace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("What blocks to use for feet placing.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
        .filter(this::blockFilter)
        .build()
    );
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates you to the block you're placing.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> rotateAngle = sgGeneral.add(new IntSetting.Builder()
        .name("rotate-angle")
        .description("Angle to rotate to when rotating.")
        .range(-360, 360)
        .sliderRange(-360, 360)
        .visible(rotate::get)
        .defaultValue(10)
        .build()
    );
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay, in ticks, between block placements.")
        .min(0)
        .defaultValue(0)
        .build()
    );

    private final Setting<Boolean> doubleHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("double-height")
        .description("Places obsidian on top of the original feet place blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> doubleWidth = sgGeneral.add(new BoolSetting.Builder()
        .name("double-width")
        .description("Places obsidian on both sides of the original feet place blocks.")
        .defaultValue(false)
        .build()
    );
    private Queue<BlockPos> placeQueue = new LinkedList<>();
    private int timer = 0;
    private BlockPos centerPos;
    private BlockPos lastPlayerPos;

    public FeetPlace() {
        super(Categories.Combat, "feet-place", "Place blocks on your feet");
    }

    @Override
    public void onActivate() {
        centerPos = Objects.requireNonNull(mc.player).getBlockPos();
        generatePositions();
        timer = delay.get();
        lastPlayerPos = mc.player.getBlockPos();

        // 移动到方块正中心
        moveToCenter();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (placeQueue.isEmpty() || mc.world == null || mc.player == null) return;

        if (timer <= 0) {
            BlockPos pos = placeQueue.poll();
            if (tryPlaceBlock(pos)) {
                timer = delay.get();
            }
        } else {
            timer--;
        }

        // 检查玩家是否移动
        if (!mc.player.getBlockPos().equals(lastPlayerPos)) {
            toggle();
        }

        // 检查是否完成所有放置任务
        if (placeQueue.isEmpty()) {
            toggle();
        }
    }

    private void generatePositions() {
        List<BlockPos> positions = new ArrayList<>();

        addCrossPositions(positions, centerPos, 1);

        if (doubleWidth.get()) {
            addCrossPositions(positions, centerPos, 2);
            addDiagonalPositions(positions, centerPos);
        }

        if (doubleHeight.get()) {
            List<BlockPos> upperPositions = new ArrayList<>();
            for (BlockPos pos : new ArrayList<>(positions)) {
                upperPositions.add(pos.up());
            }
            positions.addAll(upperPositions);
        }

        // 按距离排序（从近到远）
        positions.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(centerPos)));
        placeQueue.addAll(positions);
    }

    private void addCrossPositions(List<BlockPos> list, BlockPos center, int radius) {
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if ((Math.abs(i) == radius || Math.abs(j) == radius) && (i == 0 || j == 0)) {
                    list.add(center.add(i, 0, j));
                }
            }
        }
    }

    private void addDiagonalPositions(List<BlockPos> list, BlockPos center) {
        list.add(center.add(1, 0, 1));
        list.add(center.add(1, 0, -1));
        list.add(center.add(-1, 0, 1));
        list.add(center.add(-1, 0, -1));
    }

    private boolean tryPlaceBlock(BlockPos pos) {
        if (!Objects.requireNonNull(mc.world).getBlockState(pos).isReplaceable()) return false;
        if (InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem()))).found()) {
            // 检查玩家与放置位置的距离
            if (Objects.requireNonNull(mc.player).getBlockPos().getSquaredDistance(pos) > 6 * 6) {
                return false; // 距离超过6格，取消放置
            }
            return BlockUtils.place(pos, InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem()))), rotateAngle.get());
        }
        return false;
    }

    private boolean blockFilter(Block block) {
        return block == Blocks.OBSIDIAN ||
            block == Blocks.CRYING_OBSIDIAN ||
            block == Blocks.NETHERITE_BLOCK ||
            block == Blocks.ENDER_CHEST ||
            block == Blocks.RESPAWN_ANCHOR;
    }

    private void moveToCenter() {
        if (mc.player == null || mc.world == null) return;

        double x = MathHelper.floor(mc.player.getX()) + 0.5;
        double z = MathHelper.floor(mc.player.getZ()) + 0.5;

        // 计算误差
        double errorX = Math.abs(mc.player.getX() - x);
        double errorZ = Math.abs(mc.player.getZ() - z);

        // 如果误差超过±0.2格方块，移动玩家
        if (errorX > 0.2 || errorZ > 0.2) {
            mc.player.setPosition(x, mc.player.getY(), z);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround()));
        }
    }
}
