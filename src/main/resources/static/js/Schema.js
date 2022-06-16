var _slicedToArray = function () { function sliceIterator(arr, i) { var _arr = []; var _n = true; var _d = false; var _e = undefined; try { for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) { _arr.push(_s.value); if (i && _arr.length === i) break; } } catch (err) { _d = true; _e = err; } finally { try { if (!_n && _i["return"]) _i["return"](); } finally { if (_d) throw _e; } } return _arr; } return function (arr, i) { if (Array.isArray(arr)) { return arr; } else if (Symbol.iterator in Object(arr)) { return sliceIterator(arr, i); } else { throw new TypeError("Invalid attempt to destructure non-iterable instance"); } }; }();

function Schema(_ref) {
  var tractiveDirection = _ref.tractiveDirection,
      trainPosition = _ref.trainPosition,
      onTrainMoved = _ref.onTrainMoved;

  var _useState = useState(undefined),
      _useState2 = _slicedToArray(_useState, 2),
      payload = _useState2[0],
      setPayload = _useState2[1];

  useEffect(function () {
    var layer = new Konva.Layer();
    var cn = new Konva.Line({
      points: [-1e9, 200, 1e9, 200],
      stroke: "black",
      strokeWidth: 1
    });
    layer.add(cn);
    var earth = range(10).map(function (n) {
      var line = new Konva.Line({
        points: [-1e9, 240 + 5 * n, 1e9, 240 + 5 * n],
        stroke: "black",
        strokeWidth: 1
      });
      layer.add(line);
    });

    new BlockSs(layer, 0, "ЭЧЭ-01");
    new BlockSs(layer, 25, "ЭЧЭ-02");
    new BlockSs(layer, 60, "ЭЧЭ-03");

    setPayload(new BlockPayload(layer, trainPosition, function () {}, onTrainMoved, tractiveDirection));

    var container = document.getElementById("stage-container");
    var stage = new Konva.Stage({
      container: 'stage-container', // id of container <div>
      width: container.offsetWidth - 2,
      height: 300,
      draggable: true,
      zoomable: true,
      offsetX: -100
    });
    var scaleBy = 1.2;
    stage.on('wheel', function (e) {
      // stop default scrolling
      e.evt.preventDefault();

      var oldScale = stage.scaleX();
      var pointer = stage.getPointerPosition();

      var mousePointTo = {
        x: (pointer.x - stage.x()) / oldScale,
        y: (pointer.y - stage.y()) / oldScale
      };

      // how to scale? Zoom in? Or zoom out?
      var direction = e.evt.deltaY > 0 ? 1 : -1;

      // when we zoom on trackpad, e.evt.ctrlKey is true
      // in that case lets revert direction
      if (e.evt.ctrlKey) {
        direction = -direction;
      }

      var newScale = direction > 0 ? oldScale * scaleBy : oldScale / scaleBy;

      stage.scale({ x: newScale, y: newScale });

      var newPos = {
        x: pointer.x - mousePointTo.x * newScale,
        y: pointer.y - mousePointTo.y * newScale
      };
      stage.position(newPos);
    });
    stage.add(layer);
    layer.draw();
  }, []);

  useEffect(function () {
    if (payload !== undefined) {
      payload.setDirection(tractiveDirection);
    }
  }, [tractiveDirection]);

  return React.createElement("div", { id: "stage-container", style: { width: "100wv", border: "solid 1px" } });
}