/* Shown on the screen:
# paradiseWatch.toml
[movement]
  steps = 16 / 3620
  bpm = 0 / [0, 0, 0] / 0
[meteorological]
  altitude = 125.8  [../]
  pressure = 998.229  [\..]
[metadata]
  time = 15 : 26
  date = Wed 17 Apr, 2024
  battery = 93%
*/

// Build using: https://www.espruino.com/ide/#
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
//    raname application (check if DevTimeTOML is available?
//    code:
//        failed promise on reading Pressure does nothing
//    upload app to store and make it official
//    screen should not update every minute during sleep hours
//        should the screen update slower than every minute? - battery to be monitored first
//    tests, including performance (speed+memory - currently ~76ms per render with ~700bytes in memory)
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
var lastBPMUpdate = new Date(0);

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

function drawMovementSection(x, y, healthString, bpmString) {
    g.setColor(1, 0.5, 0).drawString("[movement]", x, y);

    g.setColor(0, 1, 1).drawString("steps", x + 12, y + 15);
    g.setColor(1, 1, 1).drawString("=", x + 48, y + 15);
    g.setColor(0.5, 1, 0).drawString(healthString, x + 60, y + 15);

    g.setColor(0, 1, 1).drawString("bpm", x + 12, y + 30);
    g.setColor(1, 1, 1).drawString("=", x + 36, y + 30);
    g.setColor(1, 1, 1).drawString(bpmString, x + 48, y + 30);

    return 3;
}

function drawMeteorologicalSection(x, y, altitudeString, pressureString) {
    g.setColor(1, 0.5, 0).drawString("[meteorological]", x, y);

    g.setColor(0, 1, 1).drawString("altitude", x + 12, y + 15);
    g.setColor(1, 1, 1).drawString("=", x + 66, y + 15);
    g.setColor(0, 1, 0).drawString(altitudeString, x + 78, y + 15);

    g.setColor(0, 1, 1).drawString("pressure", x + 12, y + 30);
    g.setColor(1, 1, 1).drawString("=", x + 66, y + 30);
    g.setColor(0, 1, 0).drawString(pressureString, x + 78, y + 30);

    return 3;
}

function drawMetadataSection(x, y, timeString, datestring) {
    g.setColor(1, 0.5, 0).drawString("[metadata]", x, y);

    g.setColor(0, 1, 1).drawString("time", x + 12, y + 15);
    g.setColor(1, 1, 1).drawString("=", x + 42, y + 15);
    g.setColor(1, 1, 1).drawString(timeString, x + 54, y + 15);

    g.setColor(0, 1, 1).drawString("date", x + 12, y + 30);
    g.setColor(1, 1, 1).drawString("=", x + 42, y + 30);
    g.setColor(0.5, 1, 0).drawString(datestring, x + 54, y + 30);

    g.setColor(0, 1, 1).drawString("battery", x + 12, y + 45);
    g.setColor(1, 1, 1).drawString("=", x + 58, y + 45);
    g.setColor(0, 1, 0).drawString(E.getBattery() + "%", x + 70, y + 45);

    return 4;
}

/**
 * Given an updated pressure reading, draw the watchface.
 */
function draw(pressureReading) {
    if (pressureReading === "undefined") {
        // protect against broken promises TODO: investigate why this happens
        return;
    }

    let now = new Date();

    // .getHealthStatus returns {movement, steps, bpm, bpmConfidence}
    let healthPerDay = Bangle.getHealthStatus("day");
    let healthPerTenMin = Bangle.getHealthStatus("last");

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

        lastBPMUpdate = now;
    }
    // example: "62 / [79, 76, 78] / 158"
    let bpmString = bpmMin + " / [" + bpmLastThreeReadings.join(", ") + "] / " + bpmMax;

    // record whether altitude/pressure is increasing or decreasing, and display with simple ascii
    // example: "109.6  [../]"
    let altitudeChange = (pressureReading.altitude > lastAltitude) ? "[../]" : "[\\..]";
    lastAltitude = pressureReading.altitude;
    let altitudeString = pressureReading.altitude.toFixed(1) + "  " + altitudeChange;

    // example: "1000.154 [\..]"
    let pressureChange = (pressureReading.pressure > lastPressure) ? "[../]" : "[\\..]";
    lastPressure = pressureReading.pressure;
    let pressureString = pressureReading.pressure.toFixed(3) + "  " + pressureChange;

    // example: "12 : 54"
    let hours = now.getHours(), minutes = now.getMinutes();
    let timeString = now.getHours().toString().padStart(2, 0) + " : " + now.getMinutes().toString().padStart(2, 0);

    // example: "Wed 17 Apr, 2024"
    let datestring = weekdayStrings[now.getDay()] + " " + now.getDate() + " " + monthStrings[now.getMonth()] + ", " + now.getFullYear();

    g.reset();
    g.setBgColor(0,0,0).clear();
    // note this means width x height is 6 x 15
    //     use g.getFonts for options
    g.setFont("6x15");

    // small offset for A E S T H E T I C S
    let xOffset = 5;
    let yOffset = 5;
    var numLines = 0;

    // draw comment/name at the start
    g.setColor(0, 1, 0).drawString("# paradiseWatch.toml", xOffset, yOffset);
    numLines++;

    numLines += drawMovementSection(
        xOffset,
        yOffset + numLines*15,
        healthPerTenMin.steps + " / " + healthPerDay.steps,
        bpmString);

    numLines += drawMeteorologicalSection(
        xOffset,
        yOffset + numLines*15,
        altitudeString,
        pressureString);

    numLines += drawMetadataSection(xOffset,
                        yOffset + numLines*15,
                        timeString,
                        datestring);
}

// Show launcher when middle button pressed
Bangle.setUI("clock");

// draw immediately, then once per minute
Bangle.getPressure().then(draw, _=>{});
let redrawClockEveryMinute = setInterval(function performDraw() {
    // TODO: failed promise on reading Pressure does nothing
    // Note: promise for Pressure can take up to 1s to resolve
    Bangle.getPressure().then(draw, _=>{});
}, 60000);
