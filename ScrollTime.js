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

// TODO: refactor code storage
// TODO: write to app via bluetooth
// TODO: step counter image
// TODO: activity -> better display?
// TODO: activity -> sleep / sleep phases?
// TODO: write arrays instead of objects? hm - also write rounded values?
// TODO: minimize bytes with 1char globals (incl Graphics)
// TODO jit/compile

const heartImg = {
  width : 15, height : 15, bpp : 4,
  buffer : require("heatshrink").decompress(atob("gEM5gCDAYXMAQQGDAAYGGAEvAAw0AAwwHEAwQHDAwYHCAwgHBAwY"))
};

const lScreen = Graphics.createArrayBuffer(176, 176, 4, {msb:true});
const cScreen = Graphics.createArrayBuffer(176, 176, 4, {msb:true});
const rScreen = Graphics.createArrayBuffer(176, 176, 4, {msb:true});

const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
let now = new Date();
let date = days[now.getDay()] + "\n" + now.getDate() + "\n" + months[now.getMonth()] + "\n" + now.getFullYear();

let update = function(scrollPosition) {
    let viewPortX = scrollPosition * -1;
    g.drawImage(cScreen, viewPortX, 0);

    if (viewPortX > 0) {
        g.drawImage(lScreen, -176 + viewPortX, 0);
    } else if (viewPortX < 0) {
        g.drawImage(rScreen, 176 + viewPortX, 0);
    }
};

let screen = 0;

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

/*require("Storage").writeJSON("scrolltime.data", {
    bpm: new Uint8Array(24),
    steps: new Uint16Array(24),
    I : 0,
    bpmAvg : 0,
    stepTotal : 0,
    stepCalDay : 0,
    bpmCalDay : 0,
    resting: 0,
    sleep: new Uint8Array(24),
    sleepTotal: 0,
    sleepCount: 0,
    asleep: 0,
    sleepResetDone: 0,
    hist: new Uint8Array(600),
    histLen: 0,
    histStart: 0,
});*/

let storedVals = require("Storage").readJSON("scrolltime.data", true);

let I = storedVals.I; // index into arrays
let T = 10; // health event interval in minutes
let steps = storedVals.steps;
let stepTotal = storedVals.stepTotal;

let bpm = storedVals.bpm;
let bpmAvg = storedVals.bpmAvg;

let bpmResting = storedVals.resting;
let setResting = true; // TODO: disable when possible
let bpmMax = -1;
let bpmMaxIndex = 23;
let bpmMin = 200;
let bpmMinIndex = 23;

let prevAct = new Date();
let activity = new Uint8Array(24);
let stepCalDay = storedVals.stepCalDay;
let bpmCalDay = storedVals.bpmCalDay;

let sleep = storedVals.sleep || new Uint8Array(24);
let sleepTotal = storedVals.sleepTotal || 0;
let sleepCount = storedVals.sleepCount || 0;
let asleep = storedVals.asleep || 0;
let sleepResetDone = storedVals.sleepResetDone || 0;

let hist = storedVals.hist || new Uint8Array(600);
let histLen = storedVals.histLen || 0;
let histStart = storedVals.histStart || Math.floor(now / 1000);

// Flash wear: ~10 unlocks/day x ~2.5KB writeJSON = 25KB/day.
// nRF52840: 10,000 erase cycles per 4KB page, ~500KB storage.
// Compaction every ~20 days, ~18 erases/page/year → ~555 year lifespan.
// No flash health API on nRF52840.
let store = function() {
    require("Storage").writeJSON("scrolltime.data", {
        I : I,

        steps: steps,
        stepTotal : stepTotal,

        bpm : bpm,
        bpmAvg : bpmAvg,
        resting : bpmResting,

        stepCalDay : stepCalDay,
        bpmCalDay : bpmCalDay,

        sleep : sleep,
        sleepTotal : sleepTotal,
        sleepCount : sleepCount,
        asleep : asleep,
        sleepResetDone : sleepResetDone,

        hist : hist,
        histLen : histLen,
        histStart : histStart,
    });
};

/**
 * When the old min/max value has slid out of the window then we need to look at the entire history to find the new one.
 * This effective updates the min/max cached values without having to recalculate on every reading.
 */
// Espruino crashes if this is `let minMaxBPM = function()` even when defined before use
function minMaxBPM() {
    bpmMin = 220;
    bpmMax = 0;

    for (let i = 0; i < 24; i++) {
        let measurement = bpm[i];
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

minMaxBPM();

let syncTimeout = null;
let syncBattery = E.getBattery();

// Redirect REPL away from BLE so the sync data handler gets clean binary I/O.
// force=true prevents Espruino from auto-switching the console back on connection state changes.
// NOTE: The Espruino Web IDE holds a BLE connection open. Disconnect the IDE before testing sync,
// otherwise the IDE's connection keeps the REPL on Bluetooth and the sync handler never fires cleanly.
NRF.on('connect', function() {
    LoopbackA.setConsole(true);
});

let doMidnightReset = function() {
    stepTotal = 0;
    stepCalDay = 0;
    bpmCalDay = 0;
    setResting = true;
    hist.fill(0);
    histLen = 0;
};

let endSync = function() {
    if (syncTimeout) { clearTimeout(syncTimeout); syncTimeout = null; }
    setTimeout(() => NRF.sleep(), 2000);
};

let mid = function() {
    let now = new Date();
    date = days[now.getDay()] + "\n" + now.getDate() + "\n" + months[now.getMonth()] + "\n" + now.getFullYear();
    sleepResetDone = 0;

    syncBattery = E.getBattery();
    NRF.wake();
    syncTimeout = setTimeout(() => {
        doMidnightReset();
        endSync();
        store();
    }, 3600000);
};
Bangle.on('midnight', mid);

let health = function(info) {
    "ram";
    let stepPerMin = info.steps / T;
    if (stepPerMin > 120) {
        // assume METS 3.5
        stepCalDay += T * 6.125;
    } else if (stepPerMin > 80) {
        // assume METS 3 -- https://bjsm.bmj.com/content/52/12/776
        stepCalDay += T * 5.25;
    } else if (stepPerMin > 50) {
        // assume METS 2
        stepCalDay += T * 3.5;
    } else {
        stepCalDay += T * 1.75;
    }
    // https://www.omnicalculator.com/sports/calories-burned-by-heart-rate
    bpmCalDay += T * (0.6309 * info.bpm - 28.5608) / 4.184;

    // record the new value, and update the sliding min/max indexes accordingly
    I = (I + 1) % 24;
    bpmAvg = (info.bpm + bpmAvg * 24) / 25;
    if (setResting || bpmAvg < bpmResting) {
        setResting = false;
        bpmResting = bpmAvg;
    }

    bpm[I] = info.bpm;
    steps[I] = info.steps;
    stepTotal += info.steps;
    if (prevAct > info.movement) {
        activity[I] = (info.movement + 255) - prevAct;
    } else {
        activity[I] = info.movement - prevAct;
    }
    prevAct = info.movement;

    let h = new Date().getHours();
    // Sleep detection: low steps and HR near resting (no movement check - too noisy)
    let sleepLike = info.steps < 5 && info.bpm > 0
        && info.bpm < bpmResting + 5;

    if (h >= 18 && !sleepResetDone && info.steps > 30) {
        sleepTotal = 0;
        sleepResetDone = 1;
        asleep = 0;
        sleepCount = 0;
    }

    sleep[I] = 0;
    if (sleepLike) {
        sleepCount++;
        if (asleep || sleepCount >= ((h >= 23 || h < 7) ? 2 : 3)) {
            if (!asleep) {
                for (let j = 1; j < sleepCount; j++) {
                    sleep[(I - j + 24) % 24] = 1;
                    let hOff = (histLen - j) * 4 + 1;
                    if (hOff >= 1) hist[hOff] |= 1;
                }
                sleepTotal += (sleepCount - 1) * T;
            }
            sleep[I] = 1;
            sleepTotal += T;
            asleep = 1;
        }
    } else {
        asleep = 0;
        sleepCount = 0;
    }

    if (histLen < 150) {
        if (!histLen) histStart = Math.floor(Date.now() / 1000);
        let off = histLen * 4;
        hist[off] = info.bpm;
        hist[off + 1] = ((info.steps >> 8) << 1) | sleep[I];
        hist[off + 2] = info.steps & 0xFF;
        hist[off + 3] = activity[I] & 0xFF;
        histLen++;
    }

    // if the old min/max have exited the window then they are older than 8 hours
    if (bpmMinIndex == I || bpmMaxIndex == I) {
        minMaxBPM();
    } else if (bpmMin > info.bpm && info.bpm > 0) {
        // guard against 0 readings updating the min
        // also do a simple cache update if newest reading is a new minimum
        bpmMin = info.bpm;
        bpmMinIndex = I;
    } else if (bpmMax < info.bpm) {
        // similarly do a simple cache update if newest reading is a new maximum
        bpmMax = info.bpm;
        bpmMaxIndex = I;
    }

    draw();
};

Bangle.on('health', (info) => health(info));

Bluetooth.on('data', function(cmd) {
    cmd = cmd.trim();
    if (cmd === "SYNC") {
        let hdr = new Uint8Array(16);
        let ts = histStart;
        hdr[0]=ts&0xFF; hdr[1]=(ts>>8)&0xFF; hdr[2]=(ts>>16)&0xFF; hdr[3]=(ts>>24)&0xFF;
        hdr[4]=histLen; hdr[5]=syncBattery;
        let a=Math.round(bpmAvg*10); hdr[6]=a&0xFF; hdr[7]=(a>>8)&0xFF;
        let r=Math.round(bpmResting*10); hdr[8]=r&0xFF; hdr[9]=(r>>8)&0xFF;
        let sc=Math.round(stepCalDay); hdr[10]=sc&0xFF; hdr[11]=(sc>>8)&0xFF;
        let bc=Math.round(bpmCalDay); hdr[12]=bc&0xFF; hdr[13]=(bc>>8)&0xFF;
        hdr[14]=sleepTotal&0xFF; hdr[15]=(sleepTotal>>8)&0xFF;
        Bluetooth.write(hdr);
        Bluetooth.write(hist.slice(0, histLen * 4));
    } else if (cmd.startsWith("TIME:")) {
        setTime(parseInt(cmd.substring(5)));
        doMidnightReset();
        store();
        Bluetooth.write("OK\n");
        endSync();
    }
});

let charge = false;
Bangle.on('charging', (charging) => {
    charge = charging;
    if (charging) Bangle.buzz(100, 1);
    update(0);
});

let updateC = function() {
    "ram";
    cScreen.clear(true);
    let bpmScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    graph.drawBar(bpmScreen.setRotation(1, 1).setColor(1, 0, 0), bpm.slice(I + 1, 24).concat(bpm.slice(0, I + 1)), {
        miny: bpmMin - 10,
        maxy: bpmMax + 10
    });
    cScreen.drawImage(bpmScreen, 0, 20).setColor(0, 1, 1).setFont("6x15").drawString(bpm[I], 5, 5);

    let now = new Date();
    // example: "12:54"
    let timeString = now.getHours().toString().padStart(2, 0) + ":" + now.getMinutes().toString().padStart(2, 0);

    cScreen.setColor(1, 1, 1).setFontAlign(0, -1, 0)
        .setFont("Vector", 35).drawString(timeString, 90, 10)
        .setFont("Vector", 25).drawString(date, 90, 50);

    let batteryString = E.getBattery() + "%";
    if (charge || syncTimeout) batteryString = "[[[ " + batteryString + " ]]]";
    cScreen
        .setColor(0, 1, 1)
        .setFont("Vector", 15)
        .setFontAlign(0, 1, 0)
        .drawString(batteryString, 58, 174)
        .drawString(Math.floor(sleepTotal/60) + "h" + (sleepTotal%60) + "m", 118, 174);

    let stepScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    graph.drawBar(stepScreen.setRotation(3).setColor(0, 1, 0), steps.slice(I + 1, 24).concat(steps.slice(0, I + 1)), {
        miny: 20,
        maxy: 750
    });
    cScreen.drawImage(stepScreen, 144, 20).setFont("6x15").setFontAlign(1, -1, 0).setColor(0, 1, 1).drawString(steps[I], 171, 5);
};

let drawL = function() {
    "ram";
    lScreen.clear(true);
    let bpmScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    bpmScreen.setColor(0, 1, 1);
    bpmScreen.drawLine(0, 39, 15, 39);
    bpmScreen.drawLine(0, 78, 15, 78);
    bpmScreen.drawLine(0, 117, 15, 117);
    for (let n = 0; n < 24; n++) {
        if (sleep[(n + I + 1) % 24])
            bpmScreen.fillRect(0, n*6|0, 2, ((n+1)*6|0)+5);
    }
    let bpmGraph = graph.drawBar(bpmScreen.setRotation(3).setColor(1, 0, 0), bpm.slice(I + 1, 24).concat(bpm.slice(0, I + 1)), {
        miny: 45,
        maxy: 180
    });
    graph.drawLine(bpmScreen.setRotation(3).setColor(0, 0, 1), activity.slice(I + 1, 24).concat(activity.slice(0, I + 1)), {
        miny: 0,
        maxy: 255
    });
    lScreen.drawImage(bpmScreen, 144, 20).drawImage(heartImg, 158, 5);

    lScreen.setFontAlign(1, 0, 0).setFont("6x15");
    for (let i = 1; i < 24; i += 3) {
        let val = bpm[(i + I) % 24];
        lScreen.setColor(1, 1, 1);
        if (val >= 120) lScreen.setColor(1, 1, 0);
        lScreen.drawString(val, 139, 176 - bpmGraph.getx(i));
    }

    lScreen.setColor(1, 0, 0).setFontAlign(1, -1, 0).drawString("max : ", 35, 141).drawString("min : ", 35, 156);
    lScreen.setColor(1, 1, 1).setFontAlign(-1, -1, 0).drawString(bpmMax, 35, 141).drawString(bpmMin, 35, 156);

    lScreen.setFont("Vector", 35).drawString(bpmAvg.toFixed(1), 8, 8);
    lScreen.setFont("Vector", 25).setColor(0, 1, 0).drawString(bpmResting.toFixed(1), 8, 45);
};

let drawR = function() {
    "ram";
    rScreen.clear(true);
    let stepScreen = Graphics.createArrayBuffer(32, 156, 4, {msb:true});
    stepScreen.setColor(0, 1, 1);
    stepScreen.drawLine(16, 39, 31, 39);
    stepScreen.drawLine(16, 78, 31, 78);
    stepScreen.drawLine(16, 117, 31, 117);
    for (let n = 0; n < 24; n++) {
        if (sleep[(n + I + 1) % 24])
            stepScreen.fillRect(29, n*6|0, 31, ((n+1)*6|0)+5);
    }
    let stepGraph = graph.drawBar(stepScreen.setRotation(1, 1).setColor(0, 1, 0), steps.slice(I + 1, 24).concat(steps.slice(0, I + 1)), {
        miny: 20,
        maxy: 750
    });
    graph.drawLine(stepScreen.setColor(0, 0, 1), activity.slice(I + 1, 24).concat(activity.slice(0, I + 1)), {
        miny: 0,
        maxy: 255
    });
    rScreen.drawImage(stepScreen, 0, 20).setColor(0, 1, 1).setFont("6x15").drawString(steps[I], 5, 5);

    rScreen.setFontAlign(-1, 0, 0).setFont("6x15");
    for (let i = 1; i < 24; i += 3) {
        let val = steps[(i + I) % 24];
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
    if (locked) {
        require("widget_utils").hide();
        draw()
    } else {
        drawL();
        drawR();
        draw();
        store();
    }
});

//////////////////////////////////
//bpm=bpm.map(()=>Math.floor(Math.random()*100+60));
/*steps=steps.map(()=>Math.floor(Math.random()*500));
bpmAvg = bpm.reduce((accumulator, currentValue) => accumulator + currentValue/24, 0);
stepTotal=0;
minMaxBPM();*/
/*mid();
//var avTime = 0;
for (let i = 0; i < 100; i++) {
    //let t = Date.now();
    //draw();
    health({steps: i, bpm:i, movement:Math.floor(Math.random()*255)});
    //avTime += process.memory(true).usage;
    //avTime += Date.now()-t;
}*/
//console.log(avTime/100.0);
// DRAW:    9.99; 9.73; 9.76                    10.16  9.83  9.94
// HEALTH: 10.84; 11.53; 11.71   11.735         11     10.26  10.06
// USAGE 7858  // 3763 (with gc)                8259 // 3765
/////////////////////////////////
/*bpm[0] = 1;
console.log(bpm);
health({steps:321, bpm:48});
console.log(bpm);
store();*/

//drawL();
//drawR();
draw();
Bangle.setUI("clock");
require("widget_utils").hide();

setTimeout(() => NRF.sleep(), 60000); // sleep after 1 minute, allowing manual sync
/*setInterval(_ => {
    draw();
}, 60000);*/
