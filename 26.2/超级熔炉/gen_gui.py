# -*- coding: utf-8 -*-
# 生成原版风格的熔炉 GUI 背景贴图:从原版 furnace.png 切边框/槽位/箭头/火焰拼面板。
# 布局公式必须和 SuperFurnaceMenu 保持一致!
import os, struct, zlib

GU = os.path.join(os.environ['TEMP'], 'guisrc', 'assets', 'minecraft', 'textures', 'gui')
FURN = os.path.join(GU, 'container', 'furnace.png')
SPRITES = os.path.join(GU, 'sprites', 'container', 'furnace')
OUT = r'E:\Mile\mc mod\超级熔炉\src\main\resources\assets\superfurnace\textures\gui'

# ---------- PNG 读取(gen_textures.py 同款) ----------
def read_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n'
    pos, w, h, bd, ct = 8, 0, 0, 0, 0
    idat = b''
    plte, trns = b'', b''
    while pos < len(d):
        ln, typ = struct.unpack('>I4s', d[pos:pos+8])
        body = d[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', body[:10])
        elif typ == b'IDAT':
            idat += body
        elif typ == b'PLTE':
            plte = body
        elif typ == b'tRNS':
            trns = body
        pos += 12 + ln
    raw = zlib.decompress(idat)
    ch = {0:1, 2:3, 3:1, 4:2, 6:4}[ct]
    bpp = max(1, ch * bd // 8)
    stride = (w * ch * bd + 7) // 8
    prev = bytearray(stride)
    out = []
    p = 0
    for y in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if ft == 1:
            for i in range(bpp, stride): line[i] = (line[i] + line[i-bpp]) & 255
        elif ft == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif ft == 3:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif ft == 4:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i-bpp] if i >= bpp else 0
                pp = a + b - c
                pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out.append(bytes(line))
        prev = line
    # 转 RGBA8888
    px = [[(0,0,0,0)]*w for _ in range(h)]
    for y in range(h):
        row = out[y]
        for x in range(w):
            if ct == 6:
                o = x*4
                px[y][x] = (row[o], row[o+1], row[o+2], row[o+3])
            elif ct == 2:
                o = x*3
                px[y][x] = (row[o], row[o+1], row[o+2], 255)
            elif ct == 3:
                i = row[x] if bd == 8 else (row[x//2] >> 4 if x%2==0 else row[x//2] & 15) if bd == 4 else (row[x//8] >> (7-x%8)) & 1
                r, g, b = plte[i*3:i*3+3]
                a = trns[i] if i < len(trns) else 255
                px[y][x] = (r, g, b, a)
            elif ct == 0:
                v = row[x] if bd == 8 else (row[x//2] >> 4 if x%2==0 else row[x//2] & 15) * 255 >> 4 if bd == 4 else (row[x//8] >> (7-x%8)) & 1
                v = v if bd == 8 else (v*255 if bd < 8 else v)
                px[y][x] = (v, v, v, 255)
    return w, h, px

def write_png(path, w, h, px):
    raw = b''.join(b'\x00' + b''.join(bytes(px[y][x]) for x in range(w)) for y in range(h))
    def chunk(t, b):
        c = t + b
        return struct.pack('>I', len(b)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9))
    png += chunk(b'IEND', b'')
    open(path, 'wb').write(png)

fw, fh, furn = read_png(FURN)
bw, bh, burn = read_png(os.path.join(SPRITES, 'burn_progress.png'))  # 24×16 箭头填充
lw, lh, lit = read_png(os.path.join(SPRITES, 'lit_progress.png'))    # 14×14 点燃火焰
print('furnace.png', fw, fh, '| burn', bw, bh, '| lit', lw, lh)

def crop(src, x, y, w, h):
    return [[src[y+j][x+i] for i in range(w)] for j in range(h)]

def hflip(t):
    return [row[::-1] for row in t]

def vflip(t):
    return t[::-1]

# 原版素材:输入槽 18×18 在 (55,16),火焰 14×14 在 (56,36),箭头 24×16 在 (79,35)
SLOT = crop(furn, 55, 16, 18, 18)
FLAME = crop(furn, 56, 36, 14, 14)
ARROW = crop(furn, 79, 35, 24, 16)
TL = crop(furn, 0, 0, 6, 6)          # 面板圆角边框块
TOPMID = crop(furn, 30, 0, 1, 6)     # 顶部边框平直 1px 竖条
SIDEMID = crop(furn, 0, 30, 6, 1)    # 左侧边框平直 1px 横条

# 等级表(和 FurnaceTier 一致):(name, fuelSlots, inputSlots, outputSlots)
TIERS = [
    ('copper_furnace', 1, 1, 1),
    ('iron_furnace', 1, 1, 1),
    ('gold_furnace', 2, 4, 5),
    ('diamond_furnace', 3, 5, 8),
    ('netherite_furnace', 5, 10, 15),
]

TEXH = 256
SHELF_Y = 210   # 动态精灵存放行:点燃火焰(0,210) 箭头填充(24,210)

os.makedirs(OUT, exist_ok=True)
for name, fuelSlots, inputSlots, outputSlots in TIERS:
    # ---- 布局(镜像 Java) ----
    inputCols = min(inputSlots, 5)
    outputCols = min(outputSlots, 5)
    inputRows = (inputSlots + inputCols - 1) // inputCols
    outputRows = (outputSlots + outputCols - 1) // outputCols
    gridY = 17
    gridRows = max(fuelSlots, inputRows, outputRows)
    FLAME0, FUEL0, INPUT0 = 7, 25, 51
    OUTPUT0 = INPUT0 + inputCols * 18 + 28
    contentRight0 = OUTPUT0 + outputCols * 18
    imageWidth = max(contentRight0 + 5, 176)
    shift = max(0, (imageWidth - contentRight0 - 4) // 2)
    flameX, fuelX, inputX, outputX = FLAME0 + shift, FUEL0 + shift, INPUT0 + shift, OUTPUT0 + shift
    arrowX = inputX + inputCols * 18 + 2
    arrowY = gridY + (gridRows * 18 - 16) // 2
    flameY = gridY + (gridRows * 18 - 14) // 2
    barsY = gridY + gridRows * 18 + 4
    invY = barsY + 15
    imageHeight = invY + 76
    texW = 512 if imageWidth > 256 else 256

    # ---- 画布 ----
    px = [[(0,0,0,0)] * texW for _ in range(TEXH)]
    W, H = imageWidth, imageHeight
    # 主体浅灰
    for y in range(3, H-3):
        for x in range(3, W-3):
            px[y][x] = (198, 198, 198, 255)
    # 边框:四角 + 平直条拉伸
    for j in range(6):
        for i in range(6):
            px[j][i] = TL[j][i]                                  # 左上
            px[j][W-6+i] = hflip(TL)[j][i]                       # 右上
            px[H-6+j][i] = vflip(TL)[j][i]                       # 左下
            px[H-6+j][W-6+i] = vflip(hflip(TL))[j][i]            # 右下
    for x in range(6, W-6):
        for j in range(6):
            px[j][x] = TOPMID[j][0]
            px[H-6+j][x] = TOPMID[5-j][0]
    for y in range(6, H-6):
        for i in range(6):
            px[y][i] = SIDEMID[0][i]
            px[y][W-6+i] = hflip(SIDEMID)[0][i]

    def paste(tile, dx, dy):
        for j in range(len(tile)):
            for i in range(len(tile[0])):
                if tile[j][i][3] > 0:
                    px[dy+j][dx+i] = tile[j][i]

    # 熔炉槽位
    for f in range(fuelSlots):
        paste(SLOT, fuelX - 1, gridY + f * 18 - 1)  # slot y = gridY+f*18 → 盒子 y-1
    for i in range(inputSlots):
        paste(SLOT, inputX + (i % inputCols) * 18 - 1, gridY + (i // inputCols) * 18 - 1)
    for o in range(outputSlots):
        paste(SLOT, outputX + (o % outputCols) * 18 - 1, gridY + (o // outputCols) * 18 - 1)
    # 玩家背包(addStandardInventorySlots: 3 行 @invY + 快捷栏 @invY+58, x=8)
    for r in range(3):
        for c in range(9):
            paste(SLOT, 8 + c * 18 - 1, invY + r * 18 - 1)
    for c in range(9):
        paste(SLOT, 8 + c * 18 - 1, invY + 58 - 1)

    # 火焰轮廓 + 箭头轮廓
    paste(FLAME, flameX, flameY)
    paste(ARROW, arrowX, arrowY)
    # 动态精灵货架:点燃火焰 + 箭头填充
    paste(lit, 0, SHELF_Y)
    paste(burn, 24, SHELF_Y)

    write_png(os.path.join(OUT, name + '.png'), texW, TEXH, px)
    print('%s: W=%d H=%d texW=%d flame=(%d,%d) fuel=(%d) input=(%d) arrow=(%d,%d) output=(%d) invY=%d'
          % (name, imageWidth, imageHeight, texW, flameX, flameY, fuelX, inputX, arrowX, arrowY, outputX, invY))
print('done')
