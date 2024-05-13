const graph = require("graph");

// Build using: https://www.espruino.com/ide/#
/* Deploy:
save file to scrolltime.app.js in device storage
    then at least once in console:

require("Storage").write("scrolltime.info",{
"id":"scrolltime",
"name":"ScrollTime",
"type":"clock",
"src":"scrolltime.app.js"
});*/

// TODO: fix resting BPM on left
// TODO: minimize bytes with 1char globals
// TODO jit/compile
// TODO: storage.save 

const heartImg = {
  width : 15, height : 15, bpp : 4,
  buffer : require("heatshrink").decompress(atob("gEM5gCDAYXMAQQGDAAYGGAEvAAw0AAwwHEAwQHDAwYHCAwgHBAwY"))
};

const lScreen = Graphics.createArrayBuffer(176, 176, 4, {msb:true});
const cScreen = Graphics.createArrayBuffer(176, 176, 4, {msb:true});
const rScreen = Graphics.createArrayBuffer(176, 176, 4, {msb:true});

const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

let update = function(scrollPosition) {
    let viewPortX = scrollPosition * -1;
    g.drawImage(cScreen, viewPortX, 0);

    if (viewPortX > 0) {
        g.drawImage(lScreen, -176 + viewPortX, 0);
    } else if (viewPortX < 0) {
        g.drawImage(rScreen, 176 + viewPortX, 0);
    }
};

var screen = 0;

let scroll = function(direction, speed) {
    //scroll left moves to the screen on the right, and vice versa
    if (direction == 1 && screen == 1) return;
    if (direction == -1 && screen == -1) return;

    let scrollAmount = 176 / speed;
    if (direction == -1) scrollAmount = -scrollAmount;
    let scrollTotal = 0;
    if (screen == -1) {
        scrollTotal = -176;
    } else if (screen == 1) {
        scrollTotal = 176;
    }

    let doScroll = setInterval(() => {
        scrollTotal += scrollAmount;
        update(scrollTotal);
    }, 440 / speed);

    setTimeout(() => {
        clearInterval(doScroll);
        process.memory(true);
    }, 440);
    screen += direction;
};

Bangle.on('swipe', (directionLR, directionUD) => {
    if (directionLR == -1) {
        scroll(1, 2);
    } else if (directionLR == 1) {
        scroll(-1, 2);
    }
});

var bpm = new Uint8Array(24);
var bpmI = 0;
var bpmMax = -1;
var bpmMaxIndex = 23;
var bpmMin = 200;
var bpmMinIndex = 23;
var bpmAvg= 60;

var steps = new Uint16Array(24);
var stepsI = 0;
var stepTotal = 0;

var stepCalDay = 0;
var bpmCalDay = 0;

/**
 * When the old min/max value has slid out of the window then we need to look at the entire history to find the new one.
 * This effective updates the min/max cached values without having to recalculate on every reading.
 */
function minMaxBPM() {
    bpmMin = 220;
    bpmMax = 0;

    for (let i = 0; i < 24; i++) {
        measurement = bpm[i];
        if (measurement > 0 && bpmMin > measurement) {
            bpmMin = measurement;
            bpmMinIndex = i;
        }
        if (bpmMax < measurement) {
            bpmMax = measurement;
            bpmMaxIndex = i;
        }
    }
}

var date = "";
let mid = function() {
    let now = new Date();
    date = days[now.getDay()] + "\n" + now.getDate() + "\n" + months[now.getMonth()] + "\n" + now.getFullYear();
    stepTotal = 0;
    stepCalDay = 0;
    bpmCalDay = 0;
};
mid();
Bangle.on('midnight', mid);

let health = function(info) {
    stepsI = (stepsI + 1) % 24;
    steps[stepsI] = info.steps;
    stepTotal += info.steps;

    // TODO better??
    if (info.steps > 300) {
        // assume METS 3.5
        stepCalDay += 18.375;
    } else if (info.steps > 200) {
        // assume METS 3 -- https://bjsm.bmj.com/content/52/12/776
        stepCalDay += 15.75;
    } else if (info.steps > 50) {
        // assume METS 2
        stepCalDay += 10.5;
    } else {
        stepCalDay += 5.25;
    }
    // https://www.omnicalculator.com/sports/calories-burned-by-heart-rate
    bpmCalDay += (1.8927 * info.bpm - 85.6824) / 4.184;

    // record the new value, and update the sliding min/max indexes accordingly
    bpmI = (bpmI + 1) % 24;
    bpmAvg = (info.bpm + bpmAvg * 24) / 25;
    bpm[bpmI] = info.bpm;

    //bpmMinIndex--;
    //bpmMaxIndex--;
    // if the old min/max have exited the window then they are older than 8 hours
    if (bpmMinIndex == bpmI || bpmMaxIndex == bpmI) {
        minMaxBPM();
    } else if (bpmMin > info.bpm && info.bpm > 0) {
        // guard against 0 readings updating the min
        // also do a simple cache update if newest reading is a new minimum
        bpmMin = info.bpm;
        bpmMinIndex = bpmI;
    } else if (bpmMax < info.bpm) {
        // similarly do a simple cache update if newest reading is a new maximum
        bpmMax = info.bpm;
        bpmMaxIndex = bpmI;
    }

    draw();
};

Bangle.on('health', (info) => health(info));

var charge = false;
Bangle.on('charging', (charging) => {
    charge = charging;
    if (charging) Bangle.buzz(50, 1);
    update(0);
});

let updateC = function() {
    cScreen.clear(true);
    let bpmScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    graph.drawBar(bpmScreen.setRotation(1, 1).setColor(1, 0, 0), bpm.slice(bpmI + 1, 24).concat(bpm.slice(0, bpmI + 1)), {
        miny: bpmMin - 10,
        maxy: bpmMax + 10
    });
    cScreen.drawImage(bpmScreen, 0, 20).setColor(0, 1, 1).setFont("6x15").drawString(bpm[bpmI], 5, 5);

    let now = new Date();
    // example: "12:54"
    let hours = now.getHours(), minutes = now.getMinutes();
    let timeString = now.getHours().toString().padStart(2, 0) + ":" + now.getMinutes().toString().padStart(2, 0);

    cScreen.setColor(1, 1, 1).setFontAlign(0, -1, 0)
        .setFont("Vector", 35).drawString(timeString, 90, 10)
        .setFont("Vector", 25).drawString(date, 90, 50);

    let batteryString = E.getBattery() + "%";
    if (charge) batteryString = "[[[ " + batteryString + " ]]]";
    cScreen
        .setColor(0, 1, 1)
        .setFont("Vector", 15)
        .setFontAlign(0, 1, 0)
        .drawString(batteryString, 90, 174);

    let stepScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    graph.drawBar(stepScreen.setRotation(3).setColor(0, 1, 0), steps.slice(stepsI + 1, 24).concat(steps.slice(0, stepsI + 1)), {
        miny: 20,
        maxy: 750
    });
    cScreen.drawImage(stepScreen, 144, 20).setFont("6x15").setFontAlign(1, -1, 0).setColor(0, 1, 1).drawString(steps[stepsI], 171, 5);
};

let updateL = function() {
    lScreen.clear(true);
    let bpmScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    let bpmGraph = graph.drawBar(bpmScreen.setRotation(3).setColor(1, 0, 0), bpm.slice(bpmI + 1, 24).concat(bpm.slice(0, bpmI + 1)), {
        miny: 45,
        maxy: 180
    });
    lScreen.drawImage(bpmScreen, 144, 20).drawImage(heartImg, 158, 5);

    lScreen.setFontAlign(1, 0, 0).setFont("6x15");
    for (let i = 1; i < 24; i += 3) {
        val = bpm[(i + bpmI) % 24];
        lScreen.setColor(1, 1, 1);
        if (val >= 120) lScreen.setColor(1, 1, 0);
        lScreen.drawString(val, 139, 176 - bpmGraph.getx(i));
    }

    lScreen.setColor(1, 0, 0).setFontAlign(1, -1, 0).drawString("max : ", 35, 141).drawString("min : ", 35, 156);
    lScreen.setColor(1, 1, 1).setFontAlign(-1, -1, 0).drawString(bpmMax, 35, 141).drawString(bpmMin, 35, 156);

    lScreen.setFont("Vector", 35).drawString(bpmAvg.toFixed(1), 8, 8);
    //lScreen.setFont("Vector", 25).setColor(0, 1, 0).drawString(bpmRest.toFixed(1), 8, 45);
};

let updateR = function() {
    rScreen.clear(true);
    let stepScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    let stepGraph = graph.drawBar(stepScreen.setRotation(1, 1).setColor(0, 1, 0), steps.slice(stepsI + 1, 24).concat(steps.slice(0, stepsI + 1)), {
        miny: 20,
        maxy: 750
    });
    rScreen.drawImage(stepScreen, 0, 20).setColor(0, 1, 1).setFont("6x15").drawString(steps[stepsI], 5, 5);

    rScreen.setFontAlign(-1, 0, 0).setFont("6x15");
    for (let i = 1; i < 24; i += 3) {
        val = steps[(i + stepsI) % 24];
        rScreen.setColor(1, 1, 1);
        if (val >= 250) rScreen.setColor(1, 1, 0);
        rScreen.drawString(val, 37, 176 - stepGraph.getx(i));
    }

    rScreen.setColor(1, 1, 1).setFont("Vector", 35).setFontAlign(1, -1, 0).drawString(stepTotal, 176, 8)
    .setFont("Vector", 22).drawString((stepTotal*0.00080).toFixed(1) + "km", 176, 45)
    .drawString("cal", 175, 134);

    rScreen.setFontAlign(1, 1, 0).setColor(1, 0, 0).drawString(bpmCalDay.toFixed(), 144, 174)
    .setColor(0, 1, 0).drawString(stepCalDay.toFixed(), 144, 144);
};

let draw = function() {
    screen = 0;
    updateC();
    update(0);
};

// draw on unlock
Bangle.on('lock', (locked, reason) => {
    if (!locked) {
        updateL();
        updateR();
        draw();
    }
});

//////////////////////////////////
//bpm=bpm.map(()=>Math.floor(Math.random()*100+60));
/*steps=steps.map(()=>Math.floor(Math.random()*500));
bpmAvg = bpm.reduce((accumulator, currentValue) => accumulator + currentValue/24, 0);
stepTotal=0;
minMaxBPM();*/
/*var avTime = 0;
for (let i = 0; i < 100; i++) {
    let t = Date.now();
    //draw();
    health({steps:i*3, bpm:i});
    //avTime += process.memory(true).usage;
    avTime += Date.now()-t;
}
console.log(avTime/100.0);
// DRAW:    9.99; 9.73; 9.76                    10.16  9.83  9.94
// HEALTH: 10.84; 11.53; 11.71   11.735         11     10.26  10.06
// USAGE 7858  // 3763 (with gc)                8259 // 3765
/////////////////////////////////
updateL();
updateR();*/

draw();
Bangle.setUI("clock");
/*setInterval(_ => {
    draw();
}, 60000);*/
