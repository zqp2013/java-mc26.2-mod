# -*- coding: utf-8 -*-
"""从原版 Minecraft 贴图重新上色,生成超级钻石/超级下界合金的部分贴图。

用法:  python gen_textures.py [minecraft客户端jar路径]
生成:  src/main/resources/assets/superdiamond/textures/ 下的 3 张物品贴图
       (宝石/锭/锻造模板)、4+2 张护甲穿着层贴图、2 张紫色护甲 HUD 图标。
两套装备的 20 件物品图标不在这里 —— 它们用 slice_sheet.py 从参考图
E:\\Mile\\超级套.png 里切出来(配色也从那张图采样)。
可删除本脚本,不影响模组运行。
"""
import os
import struct
import sys
import zipfile
import zlib

JAR = sys.argv[1] if len(sys.argv) > 1 else (
    r"C:\Users\qpzhou\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft"
    r"\minecraft-clientonly-deobf\26.2\minecraft-clientonly-deobf-26.2.jar"
)
OUT = r"src\main\resources\assets\superdiamond\textures"

# 渐变配色: (暗部, 亮部)。两套装备的色板采样自参考图 超级套.png(见 slice_sheet.py)
SUPER_DIAMOND = ((25, 60, 85), (185, 245, 250))       # 冰青 -> 亮青蓝
SUPER_NETHERITE = ((33, 24, 45), (158, 108, 232))     # 暗紫 -> 亮紫
TEMPLATE = ((90, 42, 138), (217, 179, 255))           # 紫
PURPLE_PIP = ((123, 47, 190), (229, 198, 255))        # 紫色护甲图标
# 锭/方块两张材质图专用:高光端比 SUPER_NETHERITE 更亮的紫,不然色阶重排后
# 中段挤在暗部和阴影糊在一起,分不出"亮紫高光"的层次
NETHERITE_MAT = ((33, 24, 45), (208, 165, 255))


# ---------- 纯 Python PNG 解码/编码(支持 8bit RGBA/RGB/调色板/灰度) ----------
def parse_png(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a png"
    pos, width, height, bitdepth, ctype = 8, 0, 0, 0, 0
    palette, trns, idat = [], b"", b""
    while pos < len(data):
        (length,), typ = struct.unpack(">I", data[pos:pos + 4]), data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if typ == b"IHDR":
            width, height, bitdepth, ctype = struct.unpack(">IIBB", body[:10])
        elif typ == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif typ == b"tRNS":
            trns = body
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
    assert ctype in (0, 2, 3, 6), f"unsupported color type {ctype}"
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    if bitdepth == 8:
        bpp = channels
    elif bitdepth < 8 and ctype in (0, 3):
        bpp = 1  # 亚字节只允许灰度/调色板,滤波按字节
    else:
        raise AssertionError(f"unsupported bit depth {bitdepth} for color type {ctype}")
    stride = (width * bitdepth * channels + 7) // 8
    out, prev = bytearray(), bytearray(stride)
    p = 0
    for _ in range(height):
        f = raw[p]
        line = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                c = prev[i - bpp] if i >= bpp else 0
                b = prev[i]
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out += line
        prev = line

    def samples(row):
        """把一行字节展开成每像素的通道值列表。"""
        if bitdepth == 8:
            return [tuple(row[x * channels:(x + 1) * channels]) for x in range(width)]
        per_byte = 8 // bitdepth
        mask = (1 << bitdepth) - 1
        vals = []
        for byte in row:
            for k in range(per_byte):
                vals.append((byte >> (8 - bitdepth * (k + 1))) & mask)
        vals = vals[:width]
        if ctype == 3:
            return [(v,) for v in vals]  # 调色板索引,绝不能缩放(缩放会越界->被当成透明)
        scale = 255 // mask
        return [(v * scale,) for v in vals]  # 灰度

    px = []
    for y in range(height):
        row = out[y * stride:(y + 1) * stride]
        sm = samples(row)
        line = []
        for x in range(width):
            s = sm[x]
            if ctype == 6:
                line.append((s[0], s[1], s[2], s[3]))
            elif ctype == 2:
                line.append((s[0], s[1], s[2], 255))
            elif ctype == 3:
                i = s[0]
                if i >= len(palette):  # 未使用的调色板索引
                    line.append((0, 0, 0, 0))
                    continue
                a = trns[i] if i < len(trns) else 255
                line.append(palette[i] + (a,))
            else:
                v = s[0]
                line.append((v, v, v, 255))
        px.append(line)
    return width, height, px


def write_png(path, px):
    h, w = len(px), len(px[0])
    raw = bytearray()
    for line in px:
        raw.append(0)
        for (r, g, b, a) in line:
            raw += bytes((r, g, b, a))

    def chunk(typ, body):
        return struct.pack(">I", len(body)) + typ + body + struct.pack(">I", zlib.crc32(typ + body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def lerp(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def recolor(px, lo, hi, boost=0.0, stretch=False, contrast=1.0):
    """按亮度把原颜色映射到 lo->hi 渐变,保留原 alpha。
    stretch=True: 先把源图实际用到的亮度区间拉满到 0..1(护甲层用——原版
    明暗只有约 0.4~0.9,不拉开的话整件护甲挤在渐变中段,明暗不分发糊)。
    contrast: 中点为轴的对比度系数(>1 拉开明暗)。"""
    mn, mx = 0.0, 1.0
    if stretch:
        ls = [(0.299 * r + 0.587 * g + 0.114 * b) / 255.0
              for line in px for (r, g, b, a) in line if a]
        mn, mx = min(ls), max(ls)
    out = []
    for line in px:
        nl = []
        for (r, g, b, a) in line:
            if a == 0:
                nl.append((0, 0, 0, 0))
                continue
            l = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            if stretch and mx > mn:
                l = (l - mn) / (mx - mn)
            l = min(1.0, max(0.0, 0.5 + (l - 0.5) * contrast))
            l = min(1.0, l * (1.0 + boost))
            nr, ng, nb = lerp(lo, hi, l)
            nl.append((nr, ng, nb, a))
        out.append(nl)
    return out


def upscale2x(px):
    """PIL 双线性放大 2 倍(护甲层 64x32 -> 128x64)。实体贴图 UV 归一化,
    高清尺寸原生支持;不新增细节,只让模型上的斜边/弧边平滑不锯齿。"""
    import numpy as np
    from PIL import Image
    arr = np.asarray(px, dtype=np.uint8)
    img = Image.fromarray(arr, "RGBA").resize((arr.shape[1] * 2, arr.shape[0] * 2), Image.BILINEAR)
    return np.asarray(img).tolist()


def recolor_palette(px, lo, hi, k=6, iters=30, seed=7):
    """护甲层专用:原版护甲是干净的色阶画(cel shading),但源图明暗分布
    不均匀(大片像素挤在暗部),按亮度连续映射会把整件甲压进渐变中段发糊。
    这里把源图不透明像素 k-means 成 k 档色阶,按亮度排序后【均匀】铺满
    lo->hi 渐变 —— 明暗必然拉开,色块边界干净,和原版一样的水晶质感。"""
    import numpy as np
    arr = np.asarray(px, dtype=float)
    m = arr[..., 3] > 0
    op = arr[m][:, :3]
    if len(op) == 0:
        return px
    rng = np.random.default_rng(seed)
    cents = op[rng.choice(len(op), size=min(k, len(op)), replace=False)].copy()
    for _ in range(iters):
        assign = ((op[:, None, :] - cents[None, :, :]) ** 2).sum(2).argmin(1)
        for j in range(len(cents)):
            pts = op[assign == j]
            if len(pts):
                cents[j] = pts.mean(0)
    order = np.argsort(cents @ np.array([0.299, 0.587, 0.114]))
    cents = cents[order]
    lut = np.array([lerp(lo, hi, t) for t in np.linspace(0.0, 1.0, len(cents))])
    out = arr.copy()
    out[m, :3] = lut[((op[:, None, :] - cents[None, :, :]) ** 2).sum(2).argmin(1)]
    out[~m] = (0, 0, 0, 0)
    return np.round(out).astype(int).tolist()


def hex2rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def recolor_item(px, tint, frac=0.30, strength=0.85, darken=0.85):
    """原版工具/护甲图标轻度改造(16x16 原生尺寸):先整体压暗一点,再把
    金属部分里【最暗的 frac 比例】像素染成 tint 色 —— 超级钻石用品染暗蓝,
    超级下界合金用品染亮紫,一眼区分于原版。木柄(红棕色调)与近黑描边
    同样只压暗不染色。"""
    import numpy as np
    arr = np.asarray(px, dtype=float)
    m = arr[..., 3] > 0
    op = arr[m][:, :3]
    if len(op) == 0:
        return px
    lum = op @ np.array([0.299, 0.587, 0.114])
    wood = (op[:, 0] - np.maximum(op[:, 1], op[:, 2])) > 15  # 红棕色 = 木柄
    spread = op.max(1) - op.min(1)
    outline = (lum < 40) & (spread < 15)                      # 近黑且低彩 = 描边
                                                            # (钻石的暗青色影 lum 也低,但有彩度,要染色)
    eligible = ~(wood | outline)
    if not eligible.any():
        return px
    thr = np.quantile(lum[eligible], frac)
    hit = eligible & (lum <= thr)
    tint = np.array(tint, dtype=float)
    newop = np.round(op * darken)                             # 其余部分整体压暗
    newop[hit] = np.round(tint * strength + newop[hit] * (1.0 - strength))
    out = arr.copy()
    out[m, :3] = newop
    return out.astype(int).tolist()


def main():
    for sub in ("item", r"gui\sprites\hud"):
        os.makedirs(os.path.join(OUT, sub), exist_ok=True)
    zf = zipfile.ZipFile(JAR)

    def vanilla(name):
        return parse_png(zf.read("assets/minecraft/textures/" + name))

    def out_item(name, src, lo, hi, boost=0.0):
        w, h, px = vanilla("item/" + src + ".png")
        write_png(fr"{OUT}\item\{name}.png", recolor(px, lo, hi, boost))


    # ---- 物品贴图(宝石/锭/模板/矛手持) ----
    # 宝石/锭用色阶重排(护甲层同款):原版这两张明暗挤在中段,按亮度连续
    # 映射会整块糊成一个色;聚成色阶均匀铺满渐变才有水晶的分档明暗。
    for name, src, pal in (
        ("super_diamond", "diamond", SUPER_DIAMOND),
        ("super_netherite_ingot", "netherite_ingot", NETHERITE_MAT),
    ):
        w, h, px = vanilla("item/" + src + ".png")
        write_png(fr"{OUT}\item\{name}.png", recolor_palette(px, *pal, k=6))
    out_item("super_netherite_upgrade_smithing_template", "netherite_upgrade_smithing_template", *TEMPLATE, boost=0.25)
    for m in ("diamond", "netherite"):
        lo, hi = (SUPER_DIAMOND if m == "diamond" else SUPER_NETHERITE)
        out_item(f"super_{m}_spear_in_hand", f"{m}_spear_in_hand", lo, hi)

    # ---- 两套装备的 20 件物品图标(整体压暗 + 金属暗部约 30% 区域染色) ----
    # 用户定稿的方案:其余部分比原版暗一点;超级钻石把金属最暗的约三成
    # 区域染成暗蓝,超级下界合金把这块区域染成亮紫。
    ITEM_TINT = {
        "diamond":   (22, 42, 150),    # 暗蓝(饱和度拉高些,不然和暗青影太接近、
                                       # 一眼看只是"更暗的钻石"而不是蓝)
        "netherite": (168, 115, 250),  # 亮紫
    }
    for m, tint in ITEM_TINT.items():
        for part in ("sword", "pickaxe", "axe", "shovel", "hoe", "spear",
                     "helmet", "chestplate", "leggings", "boots"):
            w, h, px = vanilla(f"item/{m}_{part}.png")
            write_png(fr"{OUT}\item\super_{m}_{part}.png", recolor_item(px, tint))

    # ---- 护甲穿着层(26.2: textures/entity/equipment/<类型>/<名字>.png) ----
    for layer in ("humanoid", "humanoid_baby", "humanoid_leggings"):
        os.makedirs(os.path.join(OUT, "entity", "equipment", layer), exist_ok=True)
        for name, src, pal in (
            ("super_diamond", "diamond", SUPER_DIAMOND),
            ("super_netherite", "netherite", SUPER_NETHERITE),
        ):
            w, h, px = vanilla(f"entity/equipment/{layer}/{src}.png")
            px = recolor_palette(px, *pal)
            write_png(os.path.join(OUT, "entity", "equipment", layer, f"{name}.png"), upscale2x(px))

    # ---- 紫色护甲 HUD 图标(9x9,盖住白色图标用) ----
    for kind in ("full", "half"):
        w, h, px = vanilla(f"gui/sprites/hud/armor_{kind}.png")
        write_png(fr"{OUT}\gui\sprites\hud\armor_purple_{kind}.png", recolor(px, *PURPLE_PIP, boost=0.35))

    # ---- 存储方块贴图(16x16,原版方块重上色 + 色阶重排拉开明暗) ----
    os.makedirs(os.path.join(OUT, "block"), exist_ok=True)
    for name, src, pal in (
        ("super_diamond_block", "diamond_block", SUPER_DIAMOND),
        ("super_netherite_block", "netherite_block", NETHERITE_MAT),
    ):
        w, h, px = vanilla(f"block/{src}.png")
        # 方块色阶数少一档:k 多了原版细微暗纹会被拆成相邻两档,看起来像噪点
        write_png(os.path.join(OUT, "block", f"{name}.png"), recolor_palette(px, *pal, k=4))

    print("全部贴图生成完毕")


if __name__ == "__main__":
    main()
