const km = 25

function rounded(x) {
  return +x.toFixed(3)
}

class BlockSs {

  constructor(layer, x0, label) {
    const rect = new Konva.Rect({
      x: x0*km,
      y: 20,
      width: 100,
      height: 100,
      stroke: 'black',
      strokeWidth: 1,
      offsetX: 50,
      draggable: false
    })

    rect.on("dragmove", () => {
      rect.y(20)
      let x = rect.x()
      if (x < 0) x = 0
      if (x > 60*km) x = 60*km
      rect.x(x)
      this.move(x)
      coord.text(rounded(x / km))
    })
    layer.add(rect)

    const column = new Konva.Line({
      points: [0, 120, 0, 200],
      stroke: "black",
      strokeWidth: 1,
      x: x0*km
    })
    layer.add(column)

    const dot = new Konva.Circle({
      x: x0*km,
      y: 200,
      radius: 3,
      fill: "black"
    })
    layer.add(dot)

    const name = new Konva.Text({
      x: x0*km,
      y: 40,
      text: label,
      fontSize: 14,
      fontFamily: "Calibri",
      align: "left",
      offsetX: 40
    })
    name.on("click", () => {
      const newContent = prompt("Введите значение", name.text())
      if (newContent !== "") {
        name.text(newContent)
      }
    })
    layer.add(name)

    const coord = new Konva.Text({
      x: x0*km,
      y: 185,
      text: x0,
      fontSize: 14,
      fontFamily: "Calibri",
      align: "left",
      offsetX: -5
    })
    coord.on("click", () => {
      const newContent = prompt("Введите значение", coord.text())
      if (!newContent) return
      const x = toNumber(newContent)
      if (!isNaN(x)) {
        coord.text(x)
        this.move(x*km)
      }
    })
    layer.add(coord)

    this.graphics = [rect, column, dot, name, coord]
  }

  move = (coord) => {
    for (g of this.graphics) {
      g.x(coord >= 0 ? coord : 0)
    }
  }

}

class Sprite {

  constructor(layer, x0, onMove, onMoveEnd) {
    this.layer = layer
    this.x = x0*km
    this.onMove = onMove
    this.onMoveEnd = onMoveEnd
    this.graphics = []
  }

  move = (sender, x) => {
    for (g of this.graphics) {
      if (g instanceof Sprite) {
        g.move(this, x)
      } else {
        g.x(x >= 0 ? x : 0)
      }
    }
    this.x = x
  }

}

class ImgSprite extends Sprite {

  constructor(layer, x0, onMove, onMoveEnd, options, src) {
    super(layer, x0, onMove, onMoveEnd)
    const domImg = new Image()
    domImg.onload = () => {
      const konvaImage = new Konva.Image(options);
      konvaImage.image(domImg)
      this.y = konvaImage.y()
      konvaImage.on("dragmove", () => {
        konvaImage.y(this.y)
        let x = konvaImage.x()
        if (x < 0) x = 0
        if (x > 60*km) x = 60*km
        konvaImage.x(x)
        this.x = x / km
        this.onMove(this, x)
      })
      konvaImage.on("dragend", () => {
        this.onMoveEnd(this, this.x)
      })
      layer.add(konvaImage)
      this.graphics.push(konvaImage)
      konvaImage.moveToBottom()
    };
    domImg.src = src
  }

  scaleX(s) {
    const img = this.graphics[0]
    if (img !== undefined) {
      img.scaleX(s)
    }
  }

}

class BlockPayload extends Sprite {

  constructor(layer, x0, onMove, onMoveEnd, direction) {
    super(layer, x0, onMove, onMoveEnd)
    this.direction = direction

    this.coord = new Konva.Text({
      x: x0*km,
      y: 205,
      text: x0,
      fontSize: 14,
      fontFamily: "Calibri",
      align: "left",
      offsetX: -25,
      index: 1
    })
    this.coord.on("click", () => {
      const newContent = prompt("Введите значение", this.coord.text())
      if (!newContent) return
      let x = toNumber(newContent)
      if (x < 0) x = 0
      if (x > 60*km) x = 60*km
      if (!isNaN(x)) {
        this.coord.text(x)
        this.move(this, x*km)
        this.onMoveEnd(this, x)
      }
    })
    layer.add(this.coord)
    this.graphics.push(this.coord)

    this.graphics.push(
      new ImgSprite(
        layer,
        x0,
        (_, x) => {
          this.move(this, x)
          this.coord.text(rounded(x / km))
        },
        (_, x) => this.onMoveEnd(this, x / km),
        {
          x: x0*km,
          y: 200,
          width: 150,
          height: 40,
          offsetX: 114,
          draggable: true,
          scaleX: direction === "Слева направо" ? 1 : -1
        },
        trainImagePath
      )
    )
  }

  setDirection(d) {
    this.graphics.map(g => {
      if (g instanceof ImgSprite) {
        g.scaleX(d === "Слева направо" ? 1 : -1)
      }
      if (g instanceof Konva.Text) {
        g.offsetX(d === "Слева направо" ? -25 : 55)
      }
    })
  }

}
