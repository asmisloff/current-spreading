function Schema({ tractiveDirection, trainPosition, onTrainMoved }) {
  const [payload, setPayload] = useState(undefined)

  useEffect(() => {  
    const layer = new Konva.Layer()
    const cn = new Konva.Line({
      points: [-1e9, 200, 1e9, 200],
      stroke: "black",
      strokeWidth: 1
    })
    layer.add(cn)
    const earth = range(10).map((n) => {
        const line = new Konva.Line({
        points: [-1e9, 240 + 5 * n, 1e9, 240 + 5 * n],
        stroke: "black",
        strokeWidth: 1
      })
      layer.add(line)
    })

    new BlockSs(layer, 0, "ЭЧЭ-01")
    new BlockSs(layer, 25, "ЭЧЭ-02")
    new BlockSs(layer, 60, "ЭЧЭ-03")

    setPayload(new BlockPayload(layer, trainPosition, () => {}, onTrainMoved, tractiveDirection))

    const container = document.getElementById("stage-container")
    const stage = new Konva.Stage({
      container: 'stage-container',   // id of container <div>
      width: container.offsetWidth - 2,
      height: 300,
      draggable: true,
      zoomable: true,
      offsetX: -100
    })
    const scaleBy = 1.2;
    stage.on('wheel', (e) => {
      // stop default scrolling
      e.evt.preventDefault();

      var oldScale = stage.scaleX();
      var pointer = stage.getPointerPosition();

      var mousePointTo = {
        x: (pointer.x - stage.x()) / oldScale,
        y: (pointer.y - stage.y()) / oldScale,
      };

      // how to scale? Zoom in? Or zoom out?
      let direction = e.evt.deltaY > 0 ? 1 : -1;

      // when we zoom on trackpad, e.evt.ctrlKey is true
      // in that case lets revert direction
      if (e.evt.ctrlKey) {
        direction = -direction;
      }

      var newScale = direction > 0 ? oldScale * scaleBy : oldScale / scaleBy;

      stage.scale({ x: newScale, y: newScale });

      var newPos = {
        x: pointer.x - mousePointTo.x * newScale,
        y: pointer.y - mousePointTo.y * newScale,
      };
      stage.position(newPos);
    })
    stage.add(layer)
    layer.draw()
  }, [])

  useEffect(() => {
    if (payload !== undefined) {
      payload.setDirection(tractiveDirection)
    }
  }, [tractiveDirection])

  return <div id='stage-container' style={{ width: "100wv", border: "solid 1px" }}></div>
}