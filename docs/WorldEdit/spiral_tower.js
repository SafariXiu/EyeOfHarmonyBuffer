// 螺旋塔：以站位为底，每层旋转 15°，向上 24 层
// 用法：把本文件放入 run/craftscripts/ 或服务器根目录 craftscripts/，游戏内执行 //cs spiral_tower.js
var origin = context.origin;
var height = 24;
context.remember();
var x, y, z;
for (y = 0; y < height; y++) {
    var angle = y * 15 * Math.PI / 180;
    for (var i = 0; i < 4; i++) {
        var a = angle + i * Math.PI / 2;
        x = Math.floor(origin.getX() + Math.cos(a) * 3);
        z = Math.floor(origin.getZ() + Math.sin(a) * 3);
        context.setBlock(x, Math.floor(origin.getY()) + y, z, 155, 0); // 石英柱
    }
    // 每 6 层加一圈横梁
    if (y % 6 == 0) {
        for (var a2 = 0; a2 < 2 * Math.PI; a2 += 0.1) {
            x = Math.floor(origin.getX() + Math.cos(a2) * 3);
            z = Math.floor(origin.getZ() + Math.sin(a2) * 3);
            context.setBlock(x, Math.floor(origin.getY()) + y, z, 41, 0); // 金环
        }
    }
}
context.print("螺旋塔生成完毕");
