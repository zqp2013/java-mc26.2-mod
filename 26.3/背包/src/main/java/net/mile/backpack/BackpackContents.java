package net.mile.backpack;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 背包内容数据组件:一个槽位列表,每格数量是裸 int——
 * 储存终端的"每格无堆叠上限"靠它实现(原版 ItemStack 磁盘编解码把 count 钳到 1..99,
 * 网络流编解码用 VAR_INT 不钳制,所以磁盘用自定义 Entry 编解码,网络直接委托 ItemStack 流编解码)。
 */
public record BackpackContents(List<ItemStack> stacks) {

	public static final BackpackContents EMPTY = new BackpackContents(List.of());

	/** 读物品上的内容组件,没有就返回空列表 */
	public static List<ItemStack> read(ItemStack backpack) {
		if (backpack == null) {
			return List.of();
		}
		BackpackContents contents = backpack.get(BackpackMod.BACKPACK_CONTENTS);
		return contents == null ? List.of() : contents.stacks;
	}

	/** 磁盘编解码:id + count(无上限) + 组件补丁 */
	public static final Codec<BackpackContents> CODEC =
			Entry.CODEC.listOf().xmap(BackpackContents::fromEntries, BackpackContents::entries);

	private static BackpackContents fromEntries(List<Entry> entries) {
		List<ItemStack> list = new ArrayList<>(entries.size());
		for (Entry entry : entries) {
			list.add(entry.stack);
		}
		return new BackpackContents(list);
	}

	private static List<Entry> entries(BackpackContents contents) {
		List<Entry> list = new ArrayList<>(contents.stacks.size());
		for (ItemStack stack : contents.stacks) {
			list.add(new Entry(stack));
		}
		return list;
	}

	private record Entry(ItemStack stack) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(e -> e.stack.getItem()),
				Codec.INT.fieldOf("count").forGetter(e -> e.stack.getCount()),
				DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
						.forGetter(e -> e.stack.getComponentsPatch())
		).apply(instance, Entry::fromDisk));

		static Entry fromDisk(net.minecraft.world.item.Item item, int count, DataComponentPatch patch) {
			ItemStack stack = new ItemStack(item, Math.max(1, count));
			if (!patch.isEmpty()) {
				try {
					stack.applyComponents(patch);
				} catch (Exception ignored) {
					// 坏补丁宁可用默认组件,也别让整个背包读不出来
				}
			}
			return new Entry(stack);
		}
	}

	/** 网络编解码:逐格委托 ItemStack.OPTIONAL_STREAM_CODEC(count 走 VAR_INT 不受 99 限制) */
	public static final StreamCodec<RegistryFriendlyByteBuf, BackpackContents> STREAM_CODEC =
			StreamCodec.of(BackpackContents::writeToNetwork, BackpackContents::readFromNetwork);

	private static void writeToNetwork(RegistryFriendlyByteBuf buf, BackpackContents contents) {
		buf.writeVarInt(contents.stacks.size());
		for (ItemStack stack : contents.stacks) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
		}
	}

	private static BackpackContents readFromNetwork(RegistryFriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<ItemStack> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
		}
		return new BackpackContents(list);
	}
}
