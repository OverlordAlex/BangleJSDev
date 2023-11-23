// TODO
//  month as string
// turn into app
// quiet hours

const colorMap = {
    "black": 0,
    "blue": 9,
    "green": 7,
    "cyan": 8,
    "red": 12,
    "magenta": 11,
    "yellow": 14,
    "white": 15
};

const lcdTime = [
    [[8, 0], [0, 8], [0, 16], [0, 24], [16, 8], [16, 16], [16, 24], [8, 32]],
    [[8, 0], [8, 8], [8, 16], [8, 24], [8, 32]],
    [[0, 0], [8, 0], [16, 0], [16, 8], [0, 16], [8, 16], [16, 16], [0, 24], [0, 32], [8, 32], [16, 32]],
    [[0, 0], [8, 0], [16, 0], [16, 8], [0, 16], [8, 16], [16, 16], [16, 24], [0, 32], [8, 32], [16, 32]],
    [[0, 0], [0, 8], [0, 16], [8, 16], [16, 0], [16, 8], [16, 16], [16, 24], [16, 32]],
    [[0, 0], [8, 0], [16, 0], [0, 8], [0, 16], [8, 16], [16, 16], [16, 24], [0, 32], [8, 32], [16, 32]],
    [[0, 0], [8, 0], [16, 0], [0, 8], [0, 16], [8, 16], [16, 16], [0, 24], [16, 24], [0, 32], [8, 32], [16, 32]],
    [[0, 0], [8, 0], [16, 0], [16, 8], [16, 16], [16, 24], [16, 32]],
    [[0, 0], [8, 0], [16, 0], [0, 8], [16, 8], [0, 16], [8, 16], [16, 16], [0, 24], [16, 24], [0, 32], [8, 32], [16, 32]],
    [[0, 0], [8, 0], [16, 0], [0, 8], [16, 8], [0, 16], [8, 16], [16, 16], [16, 24], [16, 32]],
    [[8, 8], [8, 24]]
];

function drawLCDtimeSegment(onBuffer, x, y, numberToDraw, colorChance) {
    for (var i = lcdTime[numberToDraw].length - 1; i >= 0; i--) {
        const ltr = Graphics.createArrayBuffer(4, 6, 1, {msb: true});
        const rand = Math.random();
        const color = rand < (colorChance+0.05) ? colorMap.cyan : colorMap.green;
        const letter = rand < (colorChance+0.05) ? ltr.drawString(numberToDraw, 0, 0, true) : ltr.drawString(String.fromCharCode(37 + Math.floor(rand*25)), 0, 0, true);
        onBuffer.setColor(color).drawImage(ltr, x + lcdTime[numberToDraw][i][0], y + lcdTime[numberToDraw][i][1]);
    }
}

function drawLCDtime(hours, mins, colorChance) {
    const screen = Graphics.createArrayBuffer(176, 176, 4, {msb: true});

    drawLCDtimeSegment(screen, 14, 72, Math.floor(hours / 10), colorChance);
    drawLCDtimeSegment(screen, 46, 72, hours % 10, colorChance);

    drawLCDtimeSegment(screen, 78, 72, 10, colorMap.cyan);

    drawLCDtimeSegment(screen, 110, 72, Math.floor(mins / 10), colorChance);
    drawLCDtimeSegment(screen, 142, 72, mins % 10, colorChance);

    return screen;
}

function createTrailOption() {
    const rand = Math.random();

    // unroll loop
    const rng = 25;
    var chrx = Math.floor(rand * rng);

    var ltrs = Graphics.createArrayBuffer(4, 36, 4, {msb: true});
    ltrs.setColor(colorMap.cyan).drawString(String.fromCharCode(37 + chrx), 0, 30, true);

    var strx = String.fromCharCode(37 + chrx) + '\n';
    chrx = (chrx + chrx) % rng;
    strx += String.fromCharCode(37 + chrx) + '\n';
    chrx = (chrx + chrx) % rng;
    strx += String.fromCharCode(37 + chrx) + '\n';
    chrx = (chrx + chrx) % rng;
    strx += String.fromCharCode(37 + chrx) + '\n';
    chrx = (chrx + chrx) % rng;
    strx += String.fromCharCode(37 + chrx);

    ltrs.setColor(colorMap.green).drawString(strx, 0, 0, true);
    return ltrs;
}


trailOpts=[];
for (var i = 0; i < 3; i++) {
    trailOpts.push(createTrailOption());
}

class trail {
    constructor(x, y) {
        const rand = Math.random();
        this.x = x;
        this.y = y;
        this.speed = Math.floor(rand * 8) + 10;
        this.idx = Math.floor(rand * 3);
    }

    draw(onBuffer) {
        onBuffer.drawImage(trailOpts[this.idx], this.x, this.y);
        this.y = this.y + this.speed;
        this.idx = (this.idx + 1) % 3;
    }
}

function drawClock() {
    process.memory();
    trails=[];
    for (i = 0; i < 30; i++) {
        trails.push(new trail(i * 6, Math.floor(Math.random() * 32)));
    }

    var date = new Date();
    var hours = date.getHours(), minutes = date.getMinutes();

    const weekday = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
    const datestring =  weekday[date.getDay()] + " " + date.getDate()  + "/" + (date.getMonth() + 1) ;
    const battery = E.getBattery() + "%";

    const lcds = [
        drawLCDtime(hours, minutes, 0.2),
        drawLCDtime(hours, minutes, 0.4),
        drawLCDtime(hours, minutes, 0.6),
        drawLCDtime(hours, minutes, 0.8),
        drawLCDtime(hours, minutes, 1)
    ];

    // 176 x 176 pixels, 3 color bits (must set msb)
    var screen = Graphics.createArrayBuffer(176, 176, 4, {msb: true});

    const maxIter = 30;
    const clockShow = 8;
    var iteration = 0;

    g.clear();
    const drawScreenIter = setInterval(function() {
        iteration++;
        screen.clear();
        if (iteration > clockShow) {
                screen.drawImage(lcds[Math.ceil(iteration/maxIter * 5)], 0, 0);
                screen.setFont6x15().setColor(colorMap.cyan).drawString(datestring, 10, 10);
                screen.setFont6x15().setColor(colorMap.cyan).drawString(battery, 145, 155);
        }
        for (var trail of trails) {
            trail.draw(screen);
        }
        Bangle.setLCDOverlay(screen, 0, 0);
        if (iteration > maxIter) {
            clearInterval();
            clearTimeout();
            trails = null;
            screen = null;
            //process.memory(true);
        }
    }, 200);

    setTimeout(function (interToCancel) {
        clearInterval(interToCancel);
        trails = null;
        screen = null;
    }, 6000, drawScreenIter); // 30 iterations, * 200ms = 5800

}

var drawing = false;
Bangle.on("lock", (locked) => {
    if (!locked && !drawing) {
        g.clear();
        drawing = true;
        drawClock();
        process.memory();
    } else if (locked) {
        clearInterval();
    }
    drawing = false;
});

Bangle.loadWidgets();
Bangle.setUI("clock");
//drawClock();
