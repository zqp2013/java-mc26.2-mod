# -*- coding: utf-8 -*-
"""背包模组贴图生成:
- 普通背包 = 原版收纳袋 + 深灰背带(两条竖带)
- 高级背包 = 主体染蓝 + 背带
- 储存终端 = 主体染金 + 背带
- 装备槽空位图标 = 普通背包剪影
"""
from PIL import Image
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
    sprites = os.path.join(OUT, "textures", "gui", "sprites", "container", "slot")
    os.makedirs(sprites, exist_ok=True)
    silhouette(build(None)).save(os.path.join(sprites, "backpack.png"))
    print("done")


if __name__ == "__main__":
    main()
