import zlib, struct, os

# 16x16 上朝向箭头(白色 + 黑描边),用于小地图/大地图的玩家方向标记
ART = [
    "................",
    ".......##.......",
    "......#WW#......",
    "......#WW#......",
    ".....#WWWW#.....",
    ".....#WWWW#.....",
    "....#WWWWWW#....",
    "....#WWWWWW#....",
    "...#WWWWWWWW#...",
    "...#WWWWWWWW#...",
    "..#WWWWWWWWWW#..",
    "..#WWWWWWWWWW#..",
    ".#WWWWWWWWWWWW#.",
    ".##############.",
    "................",
    "................",
]
COLORS = {".": (0, 0, 0, 0), "#": (0, 0, 0, 255), "W": (255, 255, 255, 255)}

def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

w = h = 16
raw = b""
for row in ART:
    raw += b"\x00" + bytes(v for c in row for v in COLORS[c])
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw, 9))
       + chunk(b"IEND", b""))
out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "src/client/resources/assets/minimap/textures/gui/arrow.png")
with open(out, "wb") as f:
    f.write(png)
print("wrote", out, len(png), "bytes")
