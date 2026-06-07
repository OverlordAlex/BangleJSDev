// SportTime - Workout mode for Bangle.js 2
// Deploy: save to sporttime.app.js in device storage, then register once:
// require("Storage").write("sporttime.info",{"id":"sporttime","name":"SportTime","type":"app","src":"sporttime.app.js"});

const AGE = 30;
const MHR = 220 - AGE;
const SAMPLE_INTERVAL = 5;
const MAX_SAMPLES = 1440;
const T = 10;

// ---- State ----
let state = 'READY';
let startTime = 0;
let elapsedMs = 0;
let lastResumeTime = 0;
let displayTimer = null;
let sampleTimer = null;

// ---- HR recording (RAM only) ----
let hrSamples = new Uint8Array(MAX_SAMPLES);
let sampleCount = 0;
let hrSum = 0, hrMax = 0, hrMin = 255, hrCount = 0;
let calories = 0;
let stepTotal = 0;

// ---- Live HRM ----
let hrmBpm = 0;
let hrmConf = 0;
let lastGoodHrm = Date.now();

// ---- Rest countdown ----
let restDuration = 60;
let restRemaining = 0;
let restTimer = null;

// ---- Health bridge: maintain ScrollTime's data during workout ----
let sv = require("Storage").readJSON("scrolltime.data", true) || {};
let sI = sv.I || 0;
let sBpm = sv.bpm || new Uint8Array(24);
let sSteps = sv.steps || new Uint16Array(24);
let sBpmAvg = sv.bpmAvg || 0;
let sResting = sv.resting || 0;
let sSetResting = true;
let sStepTotal = sv.stepTotal || 0;
let sStepCalDay = sv.stepCalDay || 0;
let sBpmCalDay = sv.bpmCalDay || 0;
let sSleep = sv.sleep || new Uint8Array(24);
let sSleepTotal = sv.sleepTotal || 0;
let sSleepCount = sv.sleepCount || 0;
let sAsleep = sv.asleep || 0;
let sSleepResetDone = sv.sleepResetDone || 0;
let sHist = sv.hist || new Uint8Array(600);
let sHistLen = sv.histLen || 0;
let sHistStart = sv.histStart || Math.floor(Date.now() / 1000);
let sPrevAct = 0;
sv = undefined;

function writeScrollTimeData() {
    require("Storage").writeJSON("scrolltime.data", {
        I: sI, bpm: sBpm, steps: sSteps, bpmAvg: sBpmAvg, resting: sResting,
        stepTotal: sStepTotal, stepCalDay: sStepCalDay, bpmCalDay: sBpmCalDay,
        sleep: sSleep, sleepTotal: sSleepTotal, sleepCount: sSleepCount,
        asleep: sAsleep, sleepResetDone: sSleepResetDone,
        hist: sHist, histLen: sHistLen, histStart: sHistStart,
    });
}

Bangle.on('health', (info) => {
    "ram";
    let stepPerMin = info.steps / T;
    if (stepPerMin > 120) sStepCalDay += T * 6.125;
    else if (stepPerMin > 80) sStepCalDay += T * 5.25;
    else if (stepPerMin > 50) sStepCalDay += T * 3.5;
    else sStepCalDay += T * 1.75;
    sBpmCalDay += T * (0.6309 * info.bpm - 28.5608) / 4.184;

    sI = (sI + 1) % 24;
    sBpmAvg = (info.bpm + sBpmAvg * 24) / 25;
    if (sSetResting || sBpmAvg < sResting) { sSetResting = false; sResting = sBpmAvg; }
    sBpm[sI] = info.bpm;
    sSteps[sI] = info.steps;
    sStepTotal += info.steps;

    let actDelta = info.movement - sPrevAct;
    if (actDelta < 0) actDelta += 255;
    sPrevAct = info.movement;

    let h = new Date().getHours();
    let sleepLike = info.steps < 5 && info.bpm > 0 && info.bpm < sResting + 5;
    if (h >= 18 && !sSleepResetDone && info.steps > 30) {
        sSleepTotal = 0; sSleepResetDone = 1; sAsleep = 0; sSleepCount = 0;
    }
    sSleep[sI] = 0;
    if (sleepLike) {
        sSleepCount++;
        if (sAsleep || sSleepCount >= ((h >= 23 || h < 7) ? 2 : 3)) {
            if (!sAsleep) {
                for (let j = 1; j < sSleepCount; j++) {
                    sSleep[(sI - j + 24) % 24] = 1;
                    let hOff = (sHistLen - j) * 4 + 1;
                    if (hOff >= 1) sHist[hOff] |= 1;
                }
                sSleepTotal += (sSleepCount - 1) * T;
            }
            sSleep[sI] = 1; sSleepTotal += T; sAsleep = 1;
        }
    } else { sAsleep = 0; sSleepCount = 0; }

    if (sHistLen < 150) {
        if (!sHistLen) sHistStart = Math.floor(Date.now() / 1000);
        let off = sHistLen * 4;
        sHist[off] = info.bpm;
        sHist[off + 1] = ((info.steps >> 8) << 1) | sSleep[sI];
        sHist[off + 2] = info.steps & 0xFF;
        sHist[off + 3] = actDelta & 0xFF;
        sHistLen++;
    }
});

// ---- HRM ----
Bangle.setHRMPower(1);
Bangle.on('HRM', (hrm) => {
    "ram";
    hrmConf = hrm.confidence;
    if (hrm.confidence > 30) {
        hrmBpm = hrmBpm ? hrmBpm * 0.7 + hrm.bpm * 0.3 : hrm.bpm;
        lastGoodHrm = Date.now();
    }
});

Bangle.on('step', () => { if (state === 'RUNNING') stepTotal++; });

// ---- Helpers ----
function getElapsed() {
    if (state === 'RUNNING') return elapsedMs + (Date.now() - lastResumeTime);
    return elapsedMs;
}

function formatTime(ms) {
    let s = Math.floor(ms / 1000);
    let h = Math.floor(s / 3600); s %= 3600;
    let m = Math.floor(s / 60); s %= 60;
    if (h > 0) return h + ":" + ("0" + m).slice(-2) + ":" + ("0" + s).slice(-2);
    return m + ":" + ("0" + s).slice(-2);
}

function getZone(bpm) {
    if (bpm < MHR * 0.6) return 1;
    if (bpm < MHR * 0.7) return 2;
    if (bpm < MHR * 0.8) return 3;
    if (bpm < MHR * 0.9) return 4;
    return 5;
}

function zoneColor(z) {
    if (z === 1) return [0, 1, 0];
    if (z === 2) return [0, 1, 1];
    if (z === 3) return [0, 0, 1];
    if (z === 4) return [1, 1, 0];
    return [1, 0, 0];
}

// ---- Display ----
let pulseTick = 0;

let updateDisplay = function() {
    "ram";
    g.clear(true);
    pulseTick++;

    if (state === 'READY') {
        g.setColor(1, 1, 1).setFont("Vector", 25).setFontAlign(0, -1, 0);
        g.drawString("SportTime", 88, 20);
        g.setFont("Vector", 20).setFontAlign(0, 0, 0);
        g.drawString("Tap to start", 88, 80);
        if (hrmBpm > 0) {
            let z = getZone(hrmBpm);
            let c = zoneColor(z);
            g.setColor(c[0], c[1], c[2]).setFont("Vector", 30);
            g.drawString(Math.round(hrmBpm), 88, 130);
        } else {
            g.setColor(1, 0, 0).setFont("Vector", 30).drawString("--", 88, 130);
        }
        return;
    }

    if (state === 'STOPPED') {
        g.setColor(0, 1, 0).setFont("Vector", 20).setFontAlign(0, -1, 0);
        g.drawString("COMPLETE", 88, 8);
        g.setColor(1, 1, 1).setFont("Vector", 28);
        g.drawString(formatTime(getElapsed()), 88, 35);
        g.setFont("Vector", 18);
        let avg = hrCount > 0 ? Math.round(hrSum / hrCount) : 0;
        g.drawString("Avg " + avg + "  Max " + hrMax, 88, 72);
        g.drawString("Min " + (hrMin === 255 ? 0 : hrMin) + "  Cal " + Math.round(calories), 88, 96);
        g.drawString("Steps " + stepTotal, 88, 120);
        if (pulseTick & 1) {
            g.setColor(0, 1, 1).setFont("6x15").drawString("waiting for sync...", 88, 150);
        }
        g.setColor(1, 1, 1).setFont("6x15").setFontAlign(0, 1, 0);
        g.drawString("tap to skip", 88, 172);
        return;
    }

    // RUNNING or PAUSED
    let timeStr = formatTime(getElapsed());
    let bpmVal = Math.round(hrmBpm);
    let z = getZone(bpmVal);
    let c = zoneColor(z);
    let hasRest = restRemaining > 0;

    // Timer
    g.setColor(1, 1, 1).setFontAlign(0, -1, 0);
    g.setFont("Vector", hasRest ? 32 : 40);
    g.drawString(timeStr, 88, 5);

    // HR
    let hrY = hasRest ? 48 : 55;
    if (hrmConf > 30) {
        g.setColor(c[0], c[1], c[2]);
    } else {
        g.setColor(1, 1, 1);
    }
    g.setFont("Vector", hasRest ? 28 : 35);
    g.drawString(bpmVal > 0 ? "" + bpmVal : "--", 70, hrY);
    g.setFont("Vector", 18);
    g.drawString("Z" + z, 145, hrY + 5);

    // Zone bar
    let barY = hrY + (hasRest ? 32 : 40);
    let barW = 136;
    let barH = 10;
    let barX = 20;
    g.setColor(0.3, 0.3, 0.3);
    g.fillRect(barX, barY, barX + barW, barY + barH);
    let fill = bpmVal > 0 ? Math.min(1, Math.max(0, (bpmVal - 50) / 150)) : 0;
    g.setColor(c[0], c[1], c[2]);
    g.fillRect(barX, barY, barX + Math.round(fill * barW), barY + barH);

    // Rest countdown
    if (hasRest) {
        let rm = Math.floor(restRemaining / 60);
        let rs = restRemaining % 60;
        let restStr = rm + ":" + ("0" + rs).slice(-2);
        if (restRemaining <= 5) g.setColor(1, 0, 0);
        else if (restRemaining <= 10) g.setColor(1, 1, 0);
        else g.setColor(0, 1, 1);
        g.setFont("Vector", 28).setFontAlign(0, -1, 0);
        g.drawString("REST " + restStr, 88, 120);
    }

    // Status
    if (state === 'PAUSED') {
        g.setColor(1, 1, 0).setFont("Vector", 20).setFontAlign(0, 1, 0);
        g.drawString("PAUSED", 88, 162);
        g.setColor(1, 1, 1).setFont("6x15");
        g.drawString("swipe down to stop", 88, 174);
    } else {
        g.setColor(0, 1, 0).setFont("6x15").setFontAlign(1, 1, 0);
        g.drawString("RUNNING", 172, 172);
    }

    // Auto-pause: no good HRM for 10 minutes
    if (state === 'RUNNING' && Date.now() - lastGoodHrm > 600000) {
        pauseWorkout();
        Bangle.buzz(500, 1);
    }
};

// ---- Workout lifecycle ----
function startWorkout() {
    state = 'RUNNING';
    startTime = Date.now();
    lastResumeTime = startTime;
    elapsedMs = 0;
    sampleCount = 0;
    hrSum = 0; hrMax = 0; hrMin = 255; hrCount = 0;
    calories = 0;
    stepTotal = 0;
    lastGoodHrm = Date.now();

    sampleTimer = setInterval(() => {
        if (state === 'RUNNING' && sampleCount < MAX_SAMPLES) {
            let b = Math.round(hrmBpm) & 0xFF;
            hrSamples[sampleCount++] = b;
            if (b > 0) {
                hrSum += b; hrCount++;
                if (b > hrMax) hrMax = b;
                if (b < hrMin) hrMin = b;
                calories += (SAMPLE_INTERVAL / 60) * (0.6309 * b - 28.5608) / 4.184;
            }
        }
    }, SAMPLE_INTERVAL * 1000);

    displayTimer = setInterval(updateDisplay, 1000);
    updateDisplay();
}

function pauseWorkout() {
    if (state !== 'RUNNING') return;
    state = 'PAUSED';
    elapsedMs += Date.now() - lastResumeTime;
    if (restTimer) { clearInterval(restTimer); restTimer = null; restRemaining = 0; }
    updateDisplay();
}

function resumeWorkout() {
    if (state !== 'PAUSED') return;
    state = 'RUNNING';
    lastResumeTime = Date.now();
    lastGoodHrm = Date.now();
    updateDisplay();
}

function stopWorkout() {
    if (state === 'RUNNING') elapsedMs += Date.now() - lastResumeTime;
    state = 'STOPPED';
    if (sampleTimer) { clearInterval(sampleTimer); sampleTimer = null; }
    if (restTimer) { clearInterval(restTimer); restTimer = null; restRemaining = 0; }
    updateDisplay();
}

function exitToScrollTime() {
    if (displayTimer) clearInterval(displayTimer);
    if (sampleTimer) clearInterval(sampleTimer);
    if (restTimer) clearInterval(restTimer);
    Bangle.setHRMPower(0);
    Bangle.removeAllListeners('HRM');
    Bangle.removeAllListeners('step');
    Bangle.removeAllListeners('health');
    writeScrollTimeData();
    load("scrolltime.app.js");
}

// ---- Rest countdown ----
function startRest() {
    restRemaining = restDuration;
    if (restTimer) clearInterval(restTimer);
    restTimer = setInterval(() => {
        restRemaining--;
        if (restRemaining <= 0) {
            clearInterval(restTimer);
            restTimer = null;
            restRemaining = 0;
            Bangle.buzz(200, 1);
            setTimeout(() => Bangle.buzz(200, 1), 500);
        }
    }, 1000);
}

// ---- BLE sync ----
NRF.wake();
NRF.on('connect', () => { LoopbackA.setConsole(true); });

Bluetooth.on('data', (cmd) => {
    cmd = cmd.trim();
    if (cmd === "WSYNC") {
        if (state === 'RUNNING') stopWorkout();

        if (!sampleCount) {
            Bluetooth.write("NODATA\n");
            return;
        }
        let hdr = new Uint8Array(20);
        let s = Math.floor(startTime / 1000);
        hdr[0] = s & 0xFF; hdr[1] = (s >> 8) & 0xFF; hdr[2] = (s >> 16) & 0xFF; hdr[3] = (s >> 24) & 0xFF;
        let e = Math.floor(getElapsed() / 1000);
        hdr[4] = e & 0xFF; hdr[5] = (e >> 8) & 0xFF; hdr[6] = (e >> 16) & 0xFF; hdr[7] = (e >> 24) & 0xFF;
        hdr[8] = sampleCount & 0xFF; hdr[9] = (sampleCount >> 8) & 0xFF;
        hdr[10] = hrCount > 0 ? Math.round(hrSum / hrCount) : 0;
        hdr[11] = hrMax;
        hdr[12] = hrMin === 255 ? 0 : hrMin;
        hdr[13] = E.getBattery();
        hdr[14] = stepTotal & 0xFF; hdr[15] = (stepTotal >> 8) & 0xFF;
        let cal = Math.round(calories);
        hdr[16] = cal & 0xFF; hdr[17] = (cal >> 8) & 0xFF;
        hdr[18] = SAMPLE_INTERVAL;
        hdr[19] = 0;
        Bluetooth.write(hdr);
        Bluetooth.write(hrSamples.slice(0, sampleCount));
    } else if (cmd === "WACK") {
        Bluetooth.write("OK\n");
        setTimeout(() => exitToScrollTime(), 1000);
    }
});

// ---- Input ----
Bangle.setUI({ mode: "custom" });

setWatch(() => {
    if (state === 'READY') startWorkout();
    else if (state === 'RUNNING') pauseWorkout();
    else if (state === 'PAUSED') resumeWorkout();
}, BTN, { repeat: true, edge: 'rising' });

Bangle.on('touch', () => {
    if (state === 'READY') startWorkout();
    else if (state === 'RUNNING') startRest();
    else if (state === 'STOPPED') exitToScrollTime();
});

Bangle.on('swipe', (lr, ud) => {
    if (state === 'RUNNING') {
        if (ud === -1) restDuration = Math.min(300, restDuration + 15);
        if (ud === 1) restDuration = Math.max(15, restDuration - 15);
    } else if (state === 'PAUSED' && ud === 1) {
        stopWorkout();
    }
});

// ---- Initial display ----
updateDisplay();
