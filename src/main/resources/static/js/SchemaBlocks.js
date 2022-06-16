var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var km = 25;

function rounded(x) {
  return +x.toFixed(3);
}

var BlockSs = function BlockSs(layer, x0, label) {
  var _this = this;

  _classCallCheck(this, BlockSs);

  _initialiseProps.call(this);

  var rect = new Konva.Rect({
    x: x0 * km,
    y: 20,
    width: 100,
    height: 100,
    stroke: 'black',
    strokeWidth: 1,
    offsetX: 50,
    draggable: false
  });

  rect.on("dragmove", function () {
    rect.y(20);
    var x = rect.x();
    if (x < 0) x = 0;
    if (x > 60 * km) x = 60 * km;
    rect.x(x);
    _this.move(x);
    coord.text(rounded(x / km));
  });
  layer.add(rect);

  var column = new Konva.Line({
    points: [0, 120, 0, 200],
    stroke: "black",
    strokeWidth: 1,
    x: x0 * km
  });
  layer.add(column);

  var dot = new Konva.Circle({
    x: x0 * km,
    y: 200,
    radius: 3,
    fill: "black"
  });
  layer.add(dot);

  var name = new Konva.Text({
    x: x0 * km,
    y: 40,
    text: label,
    fontSize: 14,
    fontFamily: "Calibri",
    align: "left",
    offsetX: 40
  });
  name.on("click", function () {
    var newContent = prompt("Введите значение", name.text());
    if (newContent !== "") {
      name.text(newContent);
    }
  });
  layer.add(name);

  var coord = new Konva.Text({
    x: x0 * km,
    y: 185,
    text: x0,
    fontSize: 14,
    fontFamily: "Calibri",
    align: "left",
    offsetX: -5
  });
  coord.on("click", function () {
    var newContent = prompt("Введите значение", coord.text());
    if (!newContent) return;
    var x = toNumber(newContent);
    if (!isNaN(x)) {
      coord.text(x);
      _this.move(x * km);
    }
  });
  layer.add(coord);

  this.graphics = [rect, column, dot, name, coord];
};

var _initialiseProps = function _initialiseProps() {
  var _this5 = this;

  this.move = function (coord) {
    var _iteratorNormalCompletion2 = true;
    var _didIteratorError2 = false;
    var _iteratorError2 = undefined;

    try {
      for (var _iterator2 = _this5.graphics[Symbol.iterator](), _step2; !(_iteratorNormalCompletion2 = (_step2 = _iterator2.next()).done); _iteratorNormalCompletion2 = true) {
        g = _step2.value;

        g.x(coord >= 0 ? coord : 0);
      }
    } catch (err) {
      _didIteratorError2 = true;
      _iteratorError2 = err;
    } finally {
      try {
        if (!_iteratorNormalCompletion2 && _iterator2.return) {
          _iterator2.return();
        }
      } finally {
        if (_didIteratorError2) {
          throw _iteratorError2;
        }
      }
    }
  };
};

var Sprite = function Sprite(layer, x0, onMove, onMoveEnd) {
  var _this2 = this;

  _classCallCheck(this, Sprite);

  this.move = function (sender, x) {
    var _iteratorNormalCompletion = true;
    var _didIteratorError = false;
    var _iteratorError = undefined;

    try {
      for (var _iterator = _this2.graphics[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
        g = _step.value;

        if (g instanceof Sprite) {
          g.move(_this2, x);
        } else {
          g.x(x >= 0 ? x : 0);
        }
      }
    } catch (err) {
      _didIteratorError = true;
      _iteratorError = err;
    } finally {
      try {
        if (!_iteratorNormalCompletion && _iterator.return) {
          _iterator.return();
        }
      } finally {
        if (_didIteratorError) {
          throw _iteratorError;
        }
      }
    }

    _this2.x = x;
  };

  this.layer = layer;
  this.x = x0 * km;
  this.onMove = onMove;
  this.onMoveEnd = onMoveEnd;
  this.graphics = [];
};

var ImgSprite = function (_Sprite) {
  _inherits(ImgSprite, _Sprite);

  function ImgSprite(layer, x0, onMove, onMoveEnd, options, src) {
    _classCallCheck(this, ImgSprite);

    var _this3 = _possibleConstructorReturn(this, (ImgSprite.__proto__ || Object.getPrototypeOf(ImgSprite)).call(this, layer, x0, onMove, onMoveEnd));

    var domImg = new Image();
    domImg.onload = function () {
      var konvaImage = new Konva.Image(options);
      konvaImage.image(domImg);
      _this3.y = konvaImage.y();
      konvaImage.on("dragmove", function () {
        konvaImage.y(_this3.y);
        var x = konvaImage.x();
        if (x < 0) x = 0;
        if (x > 60 * km) x = 60 * km;
        konvaImage.x(x);
        _this3.x = x / km;
        _this3.onMove(_this3, x);
      });
      konvaImage.on("dragend", function () {
        _this3.onMoveEnd(_this3, _this3.x);
      });
      layer.add(konvaImage);
      _this3.graphics.push(konvaImage);
      konvaImage.moveToBottom();
    };
    domImg.src = src;
    return _this3;
  }

  _createClass(ImgSprite, [{
    key: "scaleX",
    value: function scaleX(s) {
      var img = this.graphics[0];
      if (img !== undefined) {
        img.scaleX(s);
      }
    }
  }]);

  return ImgSprite;
}(Sprite);

var BlockPayload = function (_Sprite2) {
  _inherits(BlockPayload, _Sprite2);

  function BlockPayload(layer, x0, onMove, onMoveEnd, direction) {
    _classCallCheck(this, BlockPayload);

    var _this4 = _possibleConstructorReturn(this, (BlockPayload.__proto__ || Object.getPrototypeOf(BlockPayload)).call(this, layer, x0, onMove, onMoveEnd));

    _this4.direction = direction;

    _this4.coord = new Konva.Text({
      x: x0 * km,
      y: 205,
      text: x0,
      fontSize: 14,
      fontFamily: "Calibri",
      align: "left",
      offsetX: -25,
      index: 1
    });
    _this4.coord.on("click", function () {
      var newContent = prompt("Введите значение", _this4.coord.text());
      if (!newContent) return;
      var x = toNumber(newContent);
      if (x < 0) x = 0;
      if (x > 60 * km) x = 60 * km;
      if (!isNaN(x)) {
        _this4.coord.text(x);
        _this4.move(_this4, x * km);
        _this4.onMoveEnd(_this4, x);
      }
    });
    layer.add(_this4.coord);
    _this4.graphics.push(_this4.coord);

    _this4.graphics.push(new ImgSprite(layer, x0, function (_, x) {
      _this4.move(_this4, x);
      _this4.coord.text(rounded(x / km));
    }, function (_, x) {
      return _this4.onMoveEnd(_this4, x / km);
    }, {
      x: x0 * km,
      y: 200,
      width: 150,
      height: 40,
      offsetX: 114,
      draggable: true,
      scaleX: direction === "Слева направо" ? 1 : -1
    }, trainImagePath));
    return _this4;
  }

  _createClass(BlockPayload, [{
    key: "setDirection",
    value: function setDirection(d) {
      this.graphics.map(function (g) {
        if (g instanceof ImgSprite) {
          g.scaleX(d === "Слева направо" ? 1 : -1);
        }
        if (g instanceof Konva.Text) {
          g.offsetX(d === "Слева направо" ? -25 : 55);
        }
      });
    }
  }]);

  return BlockPayload;
}(Sprite);