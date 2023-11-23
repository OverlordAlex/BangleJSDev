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

var letters = [];
numLetters = 90-47;
for (let chr = 0; chr < numLetters; chr++) {
  var letter = Graphics.createArrayBuffer(8, 8, 1, {msb: true}).setFontVector(8);
  letter.drawString(String.fromCharCode(47 + chr), 0, 0);
  letterImg = {width: 8, height: 8, buffer: letter.buffer};
  letters.push(letterImg);
}

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

function drawLCDtimeSegment(onBuffer, x, y, num, colorChance) {
  for (i=lcdTime[num].length-1; i>=0; i--) {
    rand = Math.random();
    color = rand < (colorChance+0.02) ? colorMap.cyan : colorMap.green;
    letter = rand < (colorChance+0.02) ? letters[num+1] : letters[1 + Math.floor(rand * 10)];
    onBuffer.setColor(color).drawImage(letter, x + lcdTime[num][i][0], y + lcdTime[num][i][1]);
  }
}

function drawLCDtime(onBuffer, h, m, colorChance) {
  drawLCDtimeSegment(onBuffer, 14, 72, Math.floor(h / 10), colorChance);
  drawLCDtimeSegment(onBuffer, 46, 72, h % 10, colorChance);

  drawLCDtimeSegment(onBuffer, 78, 72, 10, colorMap.cyan);

  drawLCDtimeSegment(onBuffer, 110, 72, Math.floor(m / 10), colorChance);
  drawLCDtimeSegment(onBuffer, 142, 72, m % 10, colorChance);
}


maxTrailLength = 6;
trailSpeed = 4;

class trail {
  constructor(x, y) {
    this.x = x;
    this.y = y;
    this.speed = Math.floor(Math.random()*trailSpeed)+4;
    this.length = 1;
  }

  draw(onBuffer) {
    this.y += this.speed;
    this.length = Math.max(this.length++, maxTrailLength);
    for (chr = 1; chr < this.length; chr++) {
      letter = letters[Math.floor(Math.random() * numLetters)];
      // draw upwards
      onBuffer.setColor(colorMap.green).drawImage(letter, this.x, this.y - (chr*8));
    }
    onBuffer.setColor(colorMap.cyan).drawImage(letter, this.x, this.y);
  }
}

function drawClock() {
  // 176 x 176 pixels, 3 color bits (must set msb)
  var screen = Graphics.createArrayBuffer(176, 176, 4, {msb: true});

  screen.setBgColor(0, 0 , 0);
  //screen.clear();


  /*for (i = 0; i < 176/8; i++) {
    img = {width: 8, height: 8, buffer: letters[i]};
    //screen.setBgColor(1, 0, 1);
    screen.setColor(colorMap.green).drawImage(img, i*8, 0);
    //screen.setColor(7).drawImage(img, i*8+1, 1);
    //screen.drawImage(letters[i], i*8, 0);
  }*/
  
  /*img = {width: 176, height: 176, bpp: 4, buffer: screen.buffer, palette: paletteMap};
  g.clear();
  g.drawImage(img, 0, 0);*/
  //g.setColor(255, 0, 0).drawLine(0, 0, 50, 50);
  //g.drawImage(letters[0], 0, 0);
  
  numTrails = 100;
  trails= [];
  for (i=0; i<numTrails; i++) {
    var trl = new trail(Math.floor(Math.random()*172), Math.floor(Math.random()*16));
    trails.push(trl);
  }

  maxIter = 60;
  clockShow = 20;
  for (j = 0; j < maxIter; j++) {
    setTimeout(function (iter) {
      screen.clear();
      for (i=0; i<numTrails; i++) {
        trails[i].draw(screen);
      }
      if (iter > clockShow) {
        drawLCDtime(screen, 12, 34, iter/maxIter);
      }
      
      Bangle.setLCDOverlay(screen, 0, 0);
    }, 50*j, j);
  }

  Bangle.setLCDOverlay(screen, 0, 0);
}

drawClock();
