# -*- coding: utf-8 -*-
"""从参考图 E:\\Mile\\超级套.png 切出两套装备(超级钻石/超级下界合金)共 20 件物品的
64x64 HD 物品贴图,直接写入 assets/superdiamond/textures/item/。

参考图是大尺寸平滑上色稿(不是放大像素画),流程:
背景洪水填充抠图(保住深色描边) -> 收边去光晕 -> 高质量缩到 <=64x64 ->
k-means 量化成有限色板 -> 居中放进 64x64 画布。
MC 的物品模型 UV 按精灵归一化,64x64 贴图在 GUI/手持下原生高清渲染。
需要 numpy / scipy / PIL。可删除本脚本,不影响模组运行。
"""
import numpy as np
from PIL import Image
from scipy import ndimage

SHEET = r"E:\Mile\超级套.png"
OUT = r"src\main\resources\assets\superdiamond\textures\item"
BG = np.array([15, 18, 24])

# 参考图两组精灵的包围盒(x, y, w, h),由连通域分析得到;靴子左右脚/下界合金斧头
# 的部件在图里本就断开,这里手动合并成一件。
# 锄头是特意收紧的框:只取头部(顶杠+右侧下折的刃)带一小段弯柄——整件等比缩到
# 64px 后刃会塌成一团,跟镐分不清;收紧后 L 形刃能占满图标,一眼区别于镐的对称拱。
GROUPS = {
    "super_diamond": {
        "pickaxe":    (13, 223, 161, 192),
        "axe":        (217, 210, 130, 206),
        "sword":      (370, 198, 156, 217),
        "shovel":     (548, 233, 132, 183),
        "spear":      (707, 179, 146, 238),
        "hoe":        (908, 227, 120, 150),
        "helmet":     (1071, 251, 111, 130),
        "chestplate": (1230, 251, 145, 152),
        "leggings":   (1407, 245, 105, 156),
        "boots":      [(1540, 275, 63, 116), (1617, 275, 52, 116)],
    },
    "super_netherite": {
        "pickaxe":    (11, 741, 162, 181),
        "axe":        [(208, 793, 131, 140), (233, 731, 65, 93)],
        "sword":      (368, 720, 157, 208),
        "shovel":     (549, 765, 131, 163),
        "spear":      (714, 718, 138, 221),
        "hoe":        (898, 782, 133, 125),
        "helmet":     (1079, 779, 102, 131),
        "chestplate": (1230, 771, 135, 149),
        "leggings":   (1408, 766, 101, 156),
        "boots":      [(1540, 789, 61, 125), (1617, 789, 52, 123)],
    },
}

OUT_SIZE = 64      # 输出图标边长(HD;GUI 缩放 4 下正好 1:1 锐利)
K_COLORS = 7       # 每件物品量化后的最大颜色数(对齐原版:金属 3-4 档 + 木柄 2 档
                   # + 描边,色一多就变照片式渐变,在 GUI 里反而显软)
SATURATE = 1.15    # 提饱和:原版物品颜色都很"平"很饱满
PALETTE_CONTRAST = 1.25  # 色板对比:色心往均值两侧拉开。k-means 的色心间距跟着
                         # 原稿直方图密度走,不拉开的话各档明度挨太近,看起来仍是渐变
EDGE_ERODE = 2     # 抠图后往里收的像素数,去掉深色背景光晕


def boxes_of(entry):
    return [entry] if isinstance(entry[0], int) else entry


def sprite_mask(a, boxes):
    """背景洪水填充抠图:与图片边界连通的'纯背景色'区域 = 背景,其余 = 精灵本体
    (这样深色描边/深紫色部件不会被误当成背景)。"""
    strict_bg = np.sqrt(((a - BG) ** 2).sum(axis=2)) < 25
    lab, _ = ndimage.label(strict_bg)
    border_labels = set(lab[0, :]) | set(lab[-1, :]) | set(lab[:, 0]) | set(lab[:, -1])
    border_labels.discard(0)
    is_bg = np.isin(lab, list(border_labels))
    sprite = ndimage.binary_fill_holes(~is_bg)
    # 只保留落在目标包围盒里的部分
    keep = np.zeros_like(sprite)
    for (x, y, w, h) in boxes:
        keep[y:y + h, x:x + w] = sprite[y:y + h, x:x + w]
    return keep, None


def kmeans(colors, k, iters=25, seed=7):
    rng = np.random.default_rng(seed)
    colors = np.asarray(colors, dtype=float)
    centroids = colors[rng.choice(len(colors), size=min(k, len(colors)), replace=False)]
    for _ in range(iters):
        d = ((colors[:, None, :] - centroids[None, :, :]) ** 2).sum(axis=2)
        assign = d.argmin(axis=1)
        for j in range(len(centroids)):
            pts = colors[assign == j]
            if len(pts):
                centroids[j] = pts.mean(axis=0)
    return centroids


def extract(a, boxes):
    x0 = min(b[0] for b in boxes)
    y0 = min(b[1] for b in boxes)
    x1 = max(b[0] + b[2] for b in boxes)
    y1 = max(b[1] + b[3] for b in boxes)
    keep, _ = sprite_mask(a, [(x0, y0, x1 - x0, y1 - y0)])
    m = keep[y0:y1, x0:x1]
    if EDGE_ERODE:
        m = ndimage.binary_erosion(m, iterations=EDGE_ERODE)
    crop = a[y0:y1, x0:x1].astype(float)
    # 提饱和(围绕亮度向彩色方向推):对齐原版那种平而饱满的颜色观感
    lum = crop @ np.array([0.299, 0.587, 0.114])[..., None]
    crop = np.clip(lum + (crop - lum) * SATURATE, 0, 255)

    # 透明像素的 RGB 用最近的不透明色填充,避免缩放时黑边渗色
    if m.sum() == 0:
        raise RuntimeError("empty sprite at %r" % (boxes,))
    fill = ndimage.distance_transform_edt(~m, return_distances=False, return_indices=True)
    rgb = crop[fill[0], fill[1]]

    scale = OUT_SIZE / max(m.shape)
    tw, th = max(1, round(m.shape[1] * scale)), max(1, round(m.shape[0] * scale))
    rgba = np.dstack([rgb, m.astype(float) * 255])
    small = Image.fromarray(rgba.astype(np.uint8), "RGBA").resize((tw, th), Image.LANCZOS)
    s = np.asarray(small).astype(int)
    alpha = s[..., 3] > 115
    if not alpha.any():
        alpha = s[..., 3] > 60

    # k-means 量化:色板从"原始大图"的不透明像素里取(木柄这类少数色在高分辨率下
    # 才够多,缩小后取样会把柄并进金属色里)
    full_opaque = crop[m]
    if len(full_opaque) > 4000:
        idx = np.random.default_rng(3).choice(len(full_opaque), 4000, replace=False)
        full_opaque = full_opaque[idx]
    cents = kmeans(full_opaque, K_COLORS)
    # 合并过近的色心
    merged = []
    for c in cents:
        if all(np.linalg.norm(c - m2) > 14 for m2 in merged):
            merged.append(c)
    cents = np.array(merged)
    # 拉开色板明暗对比(围绕色板均值放大)
    if len(cents) > 1:
        mean = cents.mean(axis=0)
        cents = np.clip(mean + (cents - mean) * PALETTE_CONTRAST, 0, 255)
    out = np.zeros((th, tw, 4), dtype=np.uint8)
    pix = s[..., :3].astype(float)
    d = ((pix[..., None, :] - cents[None, None, :, :]) ** 2).sum(axis=3)
    out[..., :3] = cents[d.argmin(axis=2)].astype(np.uint8)
    out[..., 3] = alpha.astype(np.uint8) * 255

    canvas = np.zeros((OUT_SIZE, OUT_SIZE, 4), dtype=np.uint8)
    ox, oy = (OUT_SIZE - tw) // 2, (OUT_SIZE - th) // 2
    canvas[oy:oy + th, ox:ox + tw] = out
    return canvas


def main():
    a = np.asarray(Image.open(SHEET).convert("RGB")).astype(int)
    contact = []
    for prefix, items in GROUPS.items():
        for part, entry in items.items():
            canvas = extract(a, boxes_of(entry))
            name = f"{prefix}_{part}.png"
            Image.fromarray(canvas, "RGBA").save(rf"{OUT}\{name}")
            print("wrote", name)
            contact.append(canvas)
    # 拼一张预览图方便肉眼检查
    sheet = np.zeros((2 * OUT_SIZE, 10 * OUT_SIZE, 4), dtype=np.uint8)
    for i, c in enumerate(contact):
        sheet[(i // 10) * OUT_SIZE:(i // 10 + 1) * OUT_SIZE,
              (i % 10) * OUT_SIZE:(i % 10 + 1) * OUT_SIZE] = c
    Image.fromarray(sheet, "RGBA").save(r"E:\Mile\超级套_sliced_preview.png")
    print("preview -> E:\\Mile\\超级套_sliced_preview.png")


if __name__ == "__main__":
    main()
