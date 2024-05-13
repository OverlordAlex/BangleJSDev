const graph = require("graph");
const appRect = Bangle.appRect;
// TODO replace - not using widgets so can be static
// { "x": 0, "y": 0, "w": 176, "h": 176, "x2": 175, "y2": 175 }

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

// TODO typed arrays
// TODO: fix average and resting BPM on left
// TODO: minimize bytes with 1char globals
// TODO jit/compile
// TODO: storage.save 

const heartImg = {
  width : 15, height : 15, bpp : 4,
  buffer : require("heatshrink").decompress(atob("gEM5gCDAYXMAQQGDAAYGGAEvAAw0AAwwHEAwQHDAwYHCAwgHBAwY"))
};

const lScreen = Graphics.createArrayBuffer(appRect.w, appRect.h, 4, {msb:true});
const cScreen = Graphics.createArrayBuffer(appRect.w, appRect.h, 4, {msb:true});
const rScreen = Graphics.createArrayBuffer(appRect.w, appRect.h, 4, {msb:true});

const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

let update = function(scrollPosition) {
    let viewPortX = scrollPosition * -1;
    g.drawImage(cScreen, viewPortX, 0);

    if (viewPortX > 0) {
        g.drawImage(lScreen, -appRect.w + viewPortX, 0);
    } else if (viewPortX < 0) {
        g.drawImage(rScreen, appRect.w + viewPortX, 0);
    }
};

var screen = 0;
var moving = false;

let scroll = function(direction, speed) {
    //scroll left moves to the screen on the right, and vice versa
    if (moving) return;
    if (direction == 1 && screen == 1) return;
    if (direction == -1 && screen == -1) return;

    let scrollAmount = appRect.w / speed;
    if (direction == -1) scrollAmount = -scrollAmount;
    let scrollTime = 440 / speed;
    let scrollTotal = 0;
    if (screen == -1) {
        scrollTotal = -appRect.w;
    } else if (screen == 1) {
        scrollTotal = appRect.w;
    }

    let doScroll = setInterval(() => {
        scrollTotal += scrollAmount;
        update(scrollTotal);
    }, scrollTime);

    setTimeout(() => {
        clearInterval(doScroll);
        moving = false;
        process.memory(true);
    }, 440);
    moving = true;
    screen += direction;
};

Bangle.on('swipe', (directionLR, directionUD) => {
    if (moving) return;

    if (directionLR == -1) {
        scroll(1, 2);
    } else if (directionLR == 1) {
        scroll(-1, 2);
    }
});

var bpm = new Array(24).fill(60);
var bpmLast = 80;
var bpmMax = -1;
var bpmMaxIndex = 23;
var bpmMin = 200;
var bpmMinIndex = 23;
var bpmAvg= 60;
var bpmRest = 50;
var bpmRestIndex = 23;

var steps = new Array(24).fill(0);
var stepLast = 1;
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
    bpmLast = info.bpm;
    stepLast = info.steps;

    steps.pop();
    steps.unshift(info.steps);
    stepTotal += stepLast;

    // TODO better??
    if (stepLast > 300) {
        // assume METS 3.5
        stepCalDay += 18.375;
    } else if (stepLast > 200) {
        // assume METS 3 -- https://bjsm.bmj.com/content/52/12/776
        stepCalDay += 15.75;
    } else if (stepLast > 50) {
        // assume METS 2
        stepCalDay += 10.5;
    } else {
        stepCalDay += 5.25;
    }
    // https://www.omnicalculator.com/sports/calories-burned-by-heart-rate
    bpmCalDay += (1.8927 * bpmLast - 85.6824) / 4.184;

    // record the new value, and update the sliding min/max indexes accordingly
    bpm.unshift(bpmLast);
    bpmAvg += (bpmLast - bpm.pop()) / 24;

    bpmMinIndex--;
    bpmMaxIndex--;
    // if the old min/max have exited the window then they are older than 8 hours
    if (bpmMinIndex < 0 || bpmMaxIndex < 0) {
        minMaxBPM();
    } else if (bpmMin > bpmLast && bpmLast > 0) {
        // guard against 0 readings updating the min
        // also do a simple cache update if newest reading is a new minimum
        bpmMin = bpmLast;
        bpmMinIndex = 23;
    } else if (bpmMax < bpmLast) {
        // similarly do a simple cache update if newest reading is a new maximum
        bpmMax = bpmLast;
        bpmMaxIndex = 23;
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
    let bpmScreen = Graphics.createArrayBuffer(32, appRect.h, 4, {msb:true});
    graph.drawBar(bpmScreen.setRotation(1).setColor(1, 0, 0), bpm, {
        miny: bpmMin - 10,
        maxy: bpmMax + 10,
        width: appRect.h - 20
    });
    cScreen.drawImage(bpmScreen, 0, 20).setColor(0, 1, 1).setFont("6x15").drawString(bpmLast, 5, 5);

    let now = new Date();
    // example: "12:54"
    let hours = now.getHours(), minutes = now.getMinutes();
    let timeString = now.getHours().toString().padStart(2, 0) + ":" + now.getMinutes().toString().padStart(2, 0);

    cScreen.setColor(1, 1, 1).setFontAlign(0, -1, 0)
        .setFont("Vector", 35).drawString(timeString, appRect.w / 2 + 2, 10)
        .setFont("Vector", 25).drawString(date, appRect.w / 2 + 2, 50);

    let batteryString = E.getBattery() + "%";
    if (charge) batteryString = "[[[ " + batteryString + " ]]]";
    cScreen
        .setColor(0, 1, 1)
        .setFont("Vector", 15)
        .setFontAlign(0, 1, 0)
        .drawString(batteryString, appRect.w/2+2, appRect.h - 2);

    let stepScreen = Graphics.createArrayBuffer(32, appRect.h, 4, {msb:true});
    graph.drawBar(stepScreen.setRotation(3, true).setColor(0, 1, 0), steps, {
        miny: 20,
        maxy: 750,
        width: appRect.h - 20
    });
    cScreen.drawImage(stepScreen, appRect.w - 32, 20).setFont("6x15").setFontAlign(1, -1, 0).setColor(0, 1, 1).drawString(stepLast, appRect.w - 5, 5);
};

let updateL = function() {
    lScreen.clear(true);
    let bpmScreen = Graphics.createArrayBuffer(32, appRect.h, 4, {msb:true});
    let bpmGraph = graph.drawBar(bpmScreen.setRotation(3, true).setColor(1, 0, 0), bpm, {
        miny: 45,
        maxy: 180,
        width: appRect.h - 20
    });
    lScreen.drawImage(bpmScreen, appRect.w - 32, 20).drawImage(heartImg, appRect.w - 18, 5);

    lScreen.setFontAlign(1, 0, 0).setFont("6x15");
    for (let i = 1; i < 24; i += 3) {
        val = bpm[i];
        lScreen.setColor(1, 1, 1);
        if (val >= 120) lScreen.setColor(1, 1, 0);
        lScreen.drawString(val, appRect.w - 37, bpmGraph.getx(i) + 20);
    }

    lScreen.setColor(1, 0, 0).setFontAlign(1, -1, 0).drawString("max : ", 35, appRect.h - 35).drawString("min : ", 35, appRect.h - 20);
    lScreen.setColor(1, 1, 1).setFontAlign(-1, -1, 0).drawString(bpmMax, 35, appRect.h - 35).drawString(bpmMin, 35, appRect.h - 20);

    lScreen.setFont("Vector", 35).drawString(bpmAvg.toFixed(1), 8, 8);
    //lScreen.setFont("Vector", 25).setColor(0, 1, 0).drawString(bpmRest.toFixed(1), 8, 45);
};

let updateR = function() {
    rScreen.clear(true);
    let stepScreen = Graphics.createArrayBuffer(32, appRect.h, 4, {msb:true});
    let stepGraph = graph.drawBar(stepScreen.setRotation(1).setColor(0, 1, 0), steps, {
        miny: 20,
        maxy: 750,
        width: appRect.h - 20
    });
    rScreen.drawImage(stepScreen, 0, 20).setColor(0, 1, 1).setFont("6x15").drawString(stepLast, 5, 5);

    rScreen.setFontAlign(-1, 0, 0).setFont("6x15");
    for (let i = 1; i < 24; i += 3) {
        val = steps[i];
        rScreen.setColor(1, 1, 1);
        if (val >= 250) rScreen.setColor(1, 1, 0);
        rScreen.drawString(val, 37, stepGraph.getx(i) + 20);
    }

    rScreen.setColor(1, 1, 1).setFont("Vector", 35).setFontAlign(1, -1, 0).drawString(stepTotal, appRect.w, 8)
    .setFont("Vector", 22).drawString((stepTotal*0.00080).toFixed(1) + "km", appRect.w, 45)
    .drawString("cal", appRect.w - 1, appRect.h - 42);

    rScreen.setFontAlign(1, 1, 0).setColor(1, 0, 0).drawString(bpmCalDay.toFixed(), appRect.w - 32, appRect.h - 2)
    .setColor(0, 1, 0).drawString(stepCalDay.toFixed(), appRect.w - 32, appRect.h - 32);
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
for (let i = 0; i < 1000; i++) {
    let t = Date.now();
    //draw();
    health({steps:100, bpm:120});
    avTime += Date.now()-t;
}
console.log(avTime/1000.0);*/
// DRAW:    9.99; 9.73; 9.76
// HEALTH: 10.84; 11.53; 11.71   11.735
/////////////////////////////////

draw();
Bangle.setUI("clock");
/*setInterval(_ => {
    draw();
}, 60000);*/
