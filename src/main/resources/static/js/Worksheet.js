var _slicedToArray = function () { function sliceIterator(arr, i) { var _arr = []; var _n = true; var _d = false; var _e = undefined; try { for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) { _arr.push(_s.value); if (i && _arr.length === i) break; } } catch (err) { _d = true; _e = err; } finally { try { if (!_n && _i["return"]) _i["return"](); } finally { if (_d) throw _e; } } return _arr; } return function (arr, i) { if (Array.isArray(arr)) { return arr; } else if (Symbol.iterator in Object(arr)) { return sliceIterator(arr, i); } else { throw new TypeError("Invalid attempt to destructure non-iterable instance"); } }; }();

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

function _toConsumableArray(arr) { if (Array.isArray(arr)) { for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) { arr2[i] = arr[i]; } return arr2; } else { return Array.from(arr); } }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var useState = React.useState;
var useEffect = React.useEffect;
var useRef = React.useRef;

var apiUrl = location.origin + '/api';

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

var Complex = function () {
  function Complex(re, im) {
    _classCallCheck(this, Complex);

    this.re = re;
    this.im = im;
  }

  _createClass(Complex, [{
    key: 'magn',
    value: function magn() {
      return Math.sqrt(this.re * this.re + this.im * this.im);
    }
  }, {
    key: 'plus',
    value: function plus(other) {
      return new Complex(this.re + other.re, this.im + other.im);
    }
  }], [{
    key: 'magn',
    value: function magn(re, im) {
      return Math.sqrt(re * re + im * im);
    }
  }]);

  return Complex;
}();

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

  var _useState7 = useState([]),
      _useState8 = _slicedToArray(_useState7, 2),
      tractiveData = _useState8[0],
      setTractiveData = _useState8[1];

  var _useState9 = useState(0),
      _useState10 = _slicedToArray(_useState9, 2),
      trainPosition = _useState10[0],
      setTrainPosition = _useState10[1];

  var _useState11 = useState(0),
      _useState12 = _slicedToArray(_useState11, 2),
      trainAmperage = _useState12[0],
      setTrainAmperage = _useState12[1];

  var _useState13 = useState([]),
      _useState14 = _slicedToArray(_useState13, 2),
      spreading = _useState14[0],
      setSpreading = _useState14[1];

  useEffect(function () {
    fetch(apiUrl + '/locomotive/chartData/' + locomotiveCurrent, { method: "GET" }).then(function (resp) {
      return resp.json();
    }).then(function (data) {
      return setLocomotiveChartData(data);
    });
  }, [locomotiveCurrent]);

  useEffect(function () {
    fetch(apiUrl + '/tractive/perform', {
      method: "POST",
      mode: "cors",
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        locomotiveCurrent: locomotiveCurrent,
        direction: direction === "Слева направо" ? "LeftToRight" : "RightToLeft",
        brakeType: "IRON",
        carQty: 10,
        tractionRate: 1.0
      })
    }).then(function (resp) {
      return resp.json();
    }).then(function (data) {
      return setTractiveData(data);
    });
  }, [direction, locomotiveCurrent]);

  useEffect(function () {
    var amp = getTrainAmperageByCoordinate(trainPosition, tractiveData);
    setTrainAmperage(amp);
    var url = void 0;
    if (locomotiveCurrent == "DIRECT_CURRENT") {
      url = apiUrl + '/ic/dc/solve?coord=' + trainPosition + '&amp=' + -amp.aa;
    } else {
      url = apiUrl + '/ic/ac/solve?coord=' + trainPosition + '&activeAmp=' + amp.aa + '&fullAmp=' + amp.fa;
    }
    fetch(url, { method: "GET" }).then(function (resp) {
      return resp.json();
    }).then(function (solutions) {
      if (solutions.msg !== undefined) {
        alert(solutions.msg);
        return;
      }
      setSpreading([['x', "Изменение потенциала рельсов относительно земли. Поперечное сечение."]].concat(_toConsumableArray(solutions.map(function (s) {
        return [s.coordinate, total(s.amperages)];
      }))));
    });
  }, [trainPosition, locomotiveCurrent, tractiveData]);

  var total = function total(ar) {
    if (ar.length === 0) return 0;

    var sum = locomotiveCurrent === "DIRECT_CURRENT" ? 0 : new Complex(0, 0);
    var plus = locomotiveCurrent === "DIRECT_CURRENT" ? function (x, y) {
      return x + y;
    } : function (x, y) {
      return x.plus(y);
    };

    if (ar.length > 1) ar = ar.slice(0, -1);
    var _iteratorNormalCompletion = true;
    var _didIteratorError = false;
    var _iteratorError = undefined;

    try {
      for (var _iterator = ar[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
        elt = _step.value;

        sum = plus(sum, elt);
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

    if (locomotiveCurrent === "DIRECT_CURRENT") {
      return sum;
    } else {
      return sum.magn();
    }
  };

  var getTrainAmperageByCoordinate = function getTrainAmperageByCoordinate(x, td) {
    if (td.length === 0) {
      return { aa: 0, fa: 0 };
    }
    var comparator = void 0;
    if (direction === "Слева направо") {
      comparator = function comparator(coord, elt) {
        return coord - elt.c;
      };
    } else {
      comparator = function comparator(coord, elt) {
        return elt.c - coord;
      };
    }
    var i = binarySearch(td, x, comparator);
    if (i >= 0) {
      return { aa: td[i].aa, fa: td[i].a };
    } else {
      var j = -i - 1;
      if (j >= td.length) j = td.length - 1;
      return { aa: td[j].aa, fa: td[j].a };
    }
  };

  var binarySearch = function binarySearch(ar, el, cmp) {
    var m = 0;
    var n = ar.length - 1;
    while (m <= n) {
      var k = n + m >> 1;
      var cmpRes = cmp(el, ar[k]);
      if (cmpRes > 0) {
        m = k + 1;
      } else if (cmpRes < 0) {
        n = k - 1;
      } else {
        return k;
      }
    }
    return -m - 1;
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
    React.createElement(TractiveChart, { amperage: tractiveData }),
    React.createElement(Schema, { tractiveDirection: direction, trainPosition: trainPosition, onTrainMoved: function onTrainMoved(_, x) {
        return setTrainPosition(x);
      } }),
    React.createElement(
      'div',
      { className: 'chartContainer' },
      React.createElement(SpreadingChart, { amperage: spreading })
    )
  );
}