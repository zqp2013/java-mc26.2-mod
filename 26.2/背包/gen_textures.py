# -*- coding: utf-8 -*-
"""背包模组贴图生成:
- 普通背包 = 原版收纳袋 + 深灰背带(两条竖带)
- 高级背包 = 主体染蓝 + 背带
- 储存终端 = 主体染金 + 背带
- 超级储存终端 = 彩虹渐变 + 背带(彩色特殊外观)
- 合成终端 = 储存终端染金 + 前面挂一张工作台
- 装备槽空位图标 = 普通背包剪影
"""
from PIL import Image
import colorsys
import os

SRC = r"C:\Users\qpzhou\AppData\Local\Temp\bptex\assets\minecraft\textures\item\bundle.png"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src", "main", "resources", "assets", "backpack")

# 原版收纳袋调色板
BODY = [(98, 50, 32), (125, 64, 52), (166, 87, 44), (205, 123, 70)]
ACCENT = [(129, 86, 52), (183, 153, 99), (223, 195, 141)]

BLUE = {
    (98, 50, 32): (25, 45, 130),
    (125, 64, 52): (35, 62, 160),
    (166, 87, 44): (55, 88, 200),
    (205, 123, 70): (90, 125, 240),
    (129, 86, 52): (45, 70, 170),
    (183, 153, 99): (75, 105, 215),
    (223, 195, 141): (120, 150, 250),
}
GOLD = {
    (98, 50, 32): (150, 95, 20),
    (125, 64, 52): (175, 115, 28),
    (166, 87, 44): (205, 145, 38),
    (205, 123, 70): (235, 175, 55),
    (129, 86, 52): (185, 125, 32),
    (183, 153, 99): (215, 160, 48),
    (223, 195, 141): (250, 205, 95),
}

STRAP_TOP = (90, 90, 100)
STRAP = (58, 58, 66)
# 两条竖背带(x 列, y 范围)
STRAPS = [(4, 6, 12), (8, 6, 12)]

# 工作台小图案颜色(挂在合成终端前面)
PLANK_LIGHT = (188, 133, 66)
PLANK = (160, 110, 55)
PLANK_DARK = (112, 74, 36)
LEG = (95, 60, 32)


def build(recolor):
    src = Image.open(SRC).convert("RGBA")
    img = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp = src.load()
    op = img.load()
    for y in range(16):
        for x in range(16):
            c = sp[x, y]
            if c[3] == 0:
                continue
            rgb = c[:3]
            if recolor and rgb in recolor:
                rgb = recolor[rgb]
            op[x, y] = (rgb[0], rgb[1], rgb[2], 255)
    # 背带
    for (sx, y0, y1) in STRAPS:
        for y in range(y0, y1 + 1):
            if op[sx, y][3] > 0:
                op[sx, y] = STRAP_TOP if y <= y0 + 1 else STRAP
    return img


def build_rainbow():
    """超级储存终端:保留包体明暗,色相沿对角线走一圈彩虹。"""
    src = Image.open(SRC).convert("RGBA")
    img = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp = src.load()
    op = img.load()
    for y in range(16):
        for x in range(16):
            c = sp[x, y]
            if c[3] == 0:
                continue
            rgb = c[:3]
            hue = ((x + y) / 31.0) % 1.0
            mx, mn = max(rgb) / 255.0, min(rgb) / 255.0
            value = 0.55 + 0.45 * (mx - mn) if mx > 0 else 0.5
            r, g, b = colorsys.hsv_to_rgb(hue, 0.85, min(1.0, 0.45 + 0.55 * mx))
            op[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)
    # 背带
    for (sx, y0, y1) in STRAPS:
        for y in range(y0, y1 + 1):
            if op[sx, y][3] > 0:
                op[sx, y] = STRAP_TOP if y <= y0 + 1 else STRAP
    return img


def add_crafting_table(img):
    """在染金的终端前面挂一张小工作台:桌面 + 台面网格 + 两条腿。"""
    op = img.load()
    # 桌面
    for x in range(2, 14):
        op[x, 10] = PLANK_LIGHT
        op[x, 11] = PLANK
    # 台面上的四格网格痕迹
    for gx in (4, 7, 10):
        op[gx, 10] = PLANK_DARK
    # 腿
    for y in range(12, 16):
        op[4, y] = LEG
        op[5, y] = LEG
        op[10, y] = LEG
        op[11, y] = LEG
    # 桌面下沿阴影
    for x in range(3, 13):
        op[x, 12] = PLANK_DARK if x not in (4, 5, 10, 11) else LEG
    return img


def silhouette(img):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ip, op = img.load(), out.load()
    for y in range(16):
        for x in range(16):
            c = ip[x, y]
            if c[3] > 0:
                g = int((c[0] * 30 + c[1] * 59 + c[2] * 11) / 100 * 0.55)
                op[x, y] = (g, g, g, 255)
    return out


def main():
    items = os.path.join(OUT, "textures", "item")
    os.makedirs(items, exist_ok=True)
    build(None).save(os.path.join(items, "normal_backpack.png"))
    build(BLUE).save(os.path.join(items, "advanced_backpack.png"))
    build(GOLD).save(os.path.join(items, "storage_terminal.png"))
    build_rainbow().save(os.path.join(items, "super_storage_terminal.png"))
    add_crafting_table(build(GOLD)).save(os.path.join(items, "crafting_terminal.png"))
    sprites = os.path.join(OUT, "textures", "gui", "sprites", "container", "slot")
    os.makedirs(sprites, exist_ok=True)
    silhouette(build(None)).save(os.path.join(sprites, "backpack.png"))
    print("done")


if __name__ == "__main__":
    main()
