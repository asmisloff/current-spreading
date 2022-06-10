var _slicedToArray = function () { function sliceIterator(arr, i) { var _arr = []; var _n = true; var _d = false; var _e = undefined; try { for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) { _arr.push(_s.value); if (i && _arr.length === i) break; } } catch (err) { _d = true; _e = err; } finally { try { if (!_n && _i["return"]) _i["return"](); } finally { if (_d) throw _e; } } return _arr; } return function (arr, i) { if (Array.isArray(arr)) { return arr; } else if (Symbol.iterator in Object(arr)) { return sliceIterator(arr, i); } else { throw new TypeError("Invalid attempt to destructure non-iterable instance"); } }; }();

function _toConsumableArray(arr) { if (Array.isArray(arr)) { for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) { arr2[i] = arr[i]; } return arr2; } else { return Array.from(arr); } }

var useState = React.useState;
var useEffect = React.useEffect;
var useRef = React.useRef;

var apiUrl = "http://localhost:8080/api";

function toNumber(s) {
  switch (s) {
    case '.':case ',':case '-':case '-.':
      return 0;
    default:
      return +s;
  }
}

function range(end, start) {
  var res = [];
  for (var i = start === undefined ? 0 : start; i < end; ++i) {
    res.push(i);
  }
  return res;
}

function mockTractiveData() {
  return [['x', "Ток поезда"]].concat(_toConsumableArray(range(120 / 0.25).map(function (x) {
    return [x * 0.25, Math.abs(Math.random()) * 100];
  })));
}

function mockSpreadingData(coord) {
  var disp = Math.pow(Math.random() * Math.log(coord) * 10, 2);
  return [['x', "Изменение потенциала рельсов относительно земли. Поперечное сечение."]].concat(_toConsumableArray(range(100, -100).map(function (x) {
    return [x / 100, Math.exp(-(x * x) / disp)];
  })));
}

function Worksheet() {
  var _useState = useState("DIRECT_CURRENT"),
      _useState2 = _slicedToArray(_useState, 2),
      locomotiveCurrent = _useState2[0],
      setLocomotiveCurrent = _useState2[1];

  var _useState3 = useState({
    positions: [], forceOnSpeed: []
  }),
      _useState4 = _slicedToArray(_useState3, 2),
      locomotiveChartData = _useState4[0],
      setLocomotiveChartData = _useState4[1];

  var _useState5 = useState("Слева направо"),
      _useState6 = _slicedToArray(_useState5, 2),
      direction = _useState6[0],
      setDirection = _useState6[1];

  var _useState7 = useState(mockTractiveData()),
      _useState8 = _slicedToArray(_useState7, 2),
      tractiveAmperageArray = _useState8[0],
      setTractiveAmperageArrray = _useState8[1];

  var _useState9 = useState(100),
      _useState10 = _slicedToArray(_useState9, 2),
      trainPosition = _useState10[0],
      setTrainPosition = _useState10[1];

  useEffect(function () {
    fetch(apiUrl + '/locomotive/chartData/' + locomotiveCurrent, { method: "GET", mode: "no-cors" }).then(function (resp) {
      return resp.json();
    }).then(function (data) {
      return setLocomotiveChartData(data);
    });
  }, [locomotiveCurrent]);

  var flipDirection = function flipDirection() {
    switch (direction) {
      case "Слева направо":
        setDirection("Справа налево");
        break;
      case "Справа налево":
        setDirection("Слева направо");
        break;
      default:
        throw new Error('\u041D\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043D\u043E\u0435 \u043D\u0430\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435: ' + direction);
    }
    setTractiveAmperageArrray(mockTractiveData());
  };

  return React.createElement(
    'div',
    { style: { padding: "15px 20px 20px 20px" } },
    React.createElement(
      'div',
      { style: { display: "flex", marginBottom: 10 } },
      React.createElement(
        'div',
        { style: { flex: "0 1 300px", border: "solid 1px", padding: 10 } },
        React.createElement(
          'div',
          { className: 'panel' },
          React.createElement(
            'label',
            { htmlFor: 'schemaType' },
            '\u0422\u0438\u043F \u0441\u0445\u0435\u043C\u044B'
          ),
          React.createElement(
            'select',
            {
              name: 'schemaType',
              className: 'form-select', size: '1',
              onChange: function onChange(e) {
                var v = e.target.value;
                setLocomotiveCurrent(v === "DIRECT_CURRENT" ? "DIRECT_CURRENT" : "ALTERNATING_CURRENT");
              },
              style: { minWidth: "100%" }
            },
            React.createElement(
              'option',
              { value: 'DIRECT_CURRENT' },
              '\u041F\u043E\u0441\u0442\u043E\u044F\u043D\u043D\u044B\u0439 \u0442\u043E\u043A 3,3 \u043A\u0412'
            ),
            React.createElement(
              'option',
              { value: 'ALTERNATING_CURRENT' },
              '\u041F\u0435\u0440\u0435\u043C\u0435\u043D\u043D\u044B\u0439 \u0442\u043E\u043A 27,5 \u043A\u0412'
            ),
            React.createElement(
              'option',
              { value: 'DOUBLE_ALTERNATING_CURRENT' },
              '\u041F\u0435\u0440\u0435\u043C\u0435\u043D\u043D\u044B\u0439 \u0442\u043E\u043A 2\u044527,5 \u043A\u0412'
            )
          )
        ),
        React.createElement(
          'div',
          { className: 'panel' },
          React.createElement(
            'label',
            { htmlFor: 'trackType' },
            '\u0425\u0430\u0440\u0430\u043A\u0442\u0435\u0440 \u0443\u0447\u0430\u0441\u0442\u043A\u0430'
          ),
          React.createElement(
            'select',
            { name: 'trackType', className: 'form-select', size: '1' },
            React.createElement(
              'option',
              { value: '5' },
              '\u041F\u044F\u0442\u0438\u043F\u0443\u0442\u043D\u044B\u0439'
            ),
            React.createElement(
              'option',
              { value: '?' },
              '\u0415\u0449\u0451 \u043A\u0430\u043A\u043E\u0439-\u0442\u043E'
            )
          ),
          React.createElement(
            'label',
            { htmlFor: 'ballastType' },
            '\u0422\u0438\u043F \u0431\u0430\u043B\u043B\u0430\u0441\u0442\u0430'
          ),
          React.createElement(
            'select',
            { name: 'ballastType', className: 'form-select', size: '1' },
            React.createElement(
              'option',
              { value: 'old' },
              '\u0421\u0442\u0430\u0440\u044B\u0439 \u0431\u0430\u043B\u043B\u0430\u0441\u0442'
            ),
            React.createElement(
              'option',
              { value: 'geotextile+ps' },
              '\u0413\u0435\u043E\u0442\u0435\u043A\u0441\u0442\u0438\u043B\u044C + \u043F\u0435\u043D\u043E\u043F\u043B\u0435\u043A\u0441'
            ),
            React.createElement(
              'option',
              { value: 'geotextile' },
              '\u0413\u0435\u043E\u0442\u0435\u043A\u0441\u0442\u0438\u043B\u044C'
            ),
            React.createElement(
              'option',
              { value: 'ps' },
              '\u041F\u0435\u043D\u043E\u043F\u043B\u0435\u043A\u0441'
            )
          ),
          React.createElement(
            'label',
            { htmlFor: 'trackNumber' },
            '\u041F\u0443\u0442\u044C'
          ),
          React.createElement('input', { type: 'number', min: 1, max: 6, name: 'trackNumber', className: 'form-control', defaultValue: 1 })
        ),
        React.createElement(
          'div',
          { style: { marginTop: 100 } },
          React.createElement(
            'h5',
            null,
            ' \u041A\u043E\u043B\u0438\u0447\u0435\u0441\u0442\u0432\u043E \u0432\u0430\u0433\u043E\u043D\u043E\u0432 '
          ),
          React.createElement('input', {
            type: 'number',
            min: 0,
            max: 50,
            defaultValue: 10,
            style: { minWidth: "100%" }
          })
        ),
        React.createElement(
          'div',
          { style: { marginTop: 30 } },
          React.createElement(
            'h5',
            null,
            ' \u041D\u0430\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u0434\u0432\u0438\u0436\u0435\u043D\u0438\u044F '
          ),
          React.createElement(
            'select',
            {
              className: 'form-select', size: '1',
              onChange: function onChange(e) {
                setDirection(e.target.value);
                setTractiveAmperageArrray(mockTractiveData());
              },
              style: { minWidth: "100%" }
            },
            React.createElement(
              'option',
              { value: '\u0421\u043B\u0435\u0432\u0430 \u043D\u0430\u043F\u0440\u0430\u0432\u043E' },
              '\u0421\u043B\u0435\u0432\u0430 \u043D\u0430\u043F\u0440\u0430\u0432\u043E'
            ),
            React.createElement(
              'option',
              { value: '\u0421\u043F\u0440\u0430\u0432\u0430 \u043D\u0430\u043B\u0435\u0432\u043E' },
              '\u0421\u043F\u0440\u0430\u0432\u0430 \u043D\u0430\u043B\u0435\u0432\u043E'
            )
          )
        )
      ),
      React.createElement(
        'div',
        { style: { flex: 1 } },
        React.createElement(LocomotiveChart, {
          data: locomotiveChartData
        })
      )
    ),
    React.createElement(TractiveChart, { amperage: tractiveAmperageArray }),
    React.createElement(Schema, { tractiveDirection: direction, trainPosition: trainPosition, onTrainMoved: function onTrainMoved(_, x) {
        return setTrainPosition(x);
      } }),
    React.createElement(
      'div',
      { className: 'chartContainer' },
      React.createElement(SpreadingChart, { amperage: mockSpreadingData(trainPosition) })
    )
  );
}