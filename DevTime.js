// Built using: https://www.espruino.com/ide/#
/* Deploy:
save file to paradisetoml.app.js in device storage
    then in console:

require("Storage").write("paradisetoml.info",{
"id":"paradisetoml",
"name":"Paradise TOML",
"type":"clock",
"src":"paradisetoml.app.js"
});
*/

// TODO
//    raname application (check if DevTimeToml is available?
//    code:
//        create method to draw with syntax highlighting
//        failed promise on reading Pressure does nothing
//    upload app to store and make it official
//    screen should not update every minute during sleep hours
//        should the screen update slower than every minute? - battery to be monitored first
//    Investigate potential alternative display formats (currently TOML)
//        JSON
//        markdown
//        yaml
//        lisp

const weekdayStrings = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const monthStrings = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// cache last measured values for calculating relative change
var lastAltitude = 0;
var lastPressure = 1000;

// measure BPM min/max over a sliding 8 hour window (results in 48 measurements using 10min windows)
var bpmMin = 220;
var bpmMinIndex = 47;
var bpmMax = 0;
var bpmMaxIndex = 47;
var bpmHistory = new Array(48);

// last 3 BPM readings are kept for immediate display
var bpmLastThreeReadings = [0, 0, 0];

// BPM is updated every 10 minutes, track to make sure we only get changed data without missing identical readings
var lastBPMUpdate = new Date();

/**
 * When the old min/max value has slid out of the window then we need to look at the entire history to find the new one.
 * This effective updates the min/max cached values without having to recalculate on every reading.
 */
function recalculateMinMaxBPM() {
    bpmMin = 220;
    bpmMax = 0;

    for (let i = 0; i < 48; i++) {
        measurement = bpmHistory[i];
        if (bpmMin > measurement) {
            bpmMin = measurement;
            bpmMinIndex = i;
        } else if (bpmMax < measurement) {
            bpmMax = measurement;
            bpmMaxIndex = i;
        }
    }
}

/**
 * Given an updated pressure reading, draw the watchface.
 */
function draw(pressureReading) {
    const now = new Date();

    // .getHealthStatus returns {movement, steps, bpm, bpmConfidence}
    const healthPerDay = Bangle.getHealthStatus("day");
    const healthPerTenMin = Bangle.getHealthStatus("last");

    // health data is recorded every 10minutes - we want to avoid recording updates when data has not changed
    //     10 minutes = 600000 milliseconds
    if (now - lastBPMUpdate > 600000) {
        // record the new value, and update the sliding min/max indexes accordingly
        bpmHistory.push(healthPerTenMin.bpm);
        bpmHistory.shift();
        bpmMinIndex--;
        bpmMaxIndex--;
        // if the old min/max have exited the window then they are older than 8 hours
        if (bpmMinIndex < 0 || bpmMaxIndex < 0) {
            recalculateMinMaxBPM();
        } else if (bpmMin > healthPerTenMin.bpm) {
            // also do a simple cache update if newest reading is a new minimum
            bpmMin = healthPerTenMin.bpm;
            bpmMinIndex = 47;
        } else if (bpmMax < healthPerTenMin.bpm) {
            // similarly do a simple cache update if newest reading is a new maximum
            bpmMax = healthPerTenMin.bpm;
            bpmMaxIndex = 47;
        }

        // update values used for display
        bpmLastThreeReadings.push(healthPerTenMin.bpm);
        bpmLastThreeReadings.shift();

        lastBPMUpdate = date;
    }
    const bpmString = bpmMin + " / [" + bpmLastThreeReadings.join(", ") + "] / " + bpmMax;

    // record whether altitude/pressure is increasing or decreasing, and display with simple ascii
    const altitudeChange = (pressureReading.altitude > lastAltitude) ? "[../]" : "[\\..]";
    lastAltitude = pressureReading.altitude;
    const altitudeString = pressureReading.altitude.toFixed(1) + "  " + altitudeChange;

    const pressureChange = (pressureReading.pressure > lastPressure) ? "[../]" : "[\\..]";
    lastPressure = pressureReading.pressure;
    const pressureString = pressureReading.pressure.toFixed(3) + "  " + pressureChange;

    const hours = now.getHours(), minutes = now.getMinutes();
    const timeString = now.getHours().toString().padStart(2, 0) + " : " + now.getMinutes().toString().padStart(2, 0);

    const datestring = weekdayStrings[now.getDay()] + " " + now.getDate() + " " + monthStrings[now.getMonth()] + ", " + now.getFullYear();

    // TODO: create method to draw with syntax highlighting
    var drawString = [
        "# paradiseWatchface.toml",
        "[health]",
        "  steps = " + healthPerTenMin.steps + " / " + healthPerDay.steps,
        "  bpm = " + bpmString,
        "[meteorological]",
        "  altitude = " + altitudeString,
        "  pressure = " + pressureString,
        "[metadata]",
        "  time = " + timeString,
        "  date = " + datestring,
        "  battery = " + E.getBattery() + "%",
    ].join("\n");

    g.reset();
    g.setFont("6x15"); // g.getFonts for options

    // aling top-left
    g.setFontAlign(-1, -1);

    // small offset for A E S T H E T I C S
    g.drawString(drawString, 5, 5, true);
}

// Show launcher when middle button pressed
Bangle.setUI("clock");

// draw immediately, then once per minute
g.clear();
Bangle.getPressure().then(draw, _=>{});
var redrawClockEveryMinute = setInterval(function performDraw() {
    // TODO: failed promise on reading Pressure does nothing
    // Note: promise for Pressure can take up to 1s to resolve
    Bangle.getPressure().then(draw, _=>{});
}, 60000);
