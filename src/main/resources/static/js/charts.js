var _slicedToArray = function () { function sliceIterator(arr, i) { var _arr = []; var _n = true; var _d = false; var _e = undefined; try { for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) { _arr.push(_s.value); if (i && _arr.length === i) break; } } catch (err) { _d = true; _e = err; } finally { try { if (!_n && _i["return"]) _i["return"](); } finally { if (_d) throw _e; } } return _arr; } return function (arr, i) { if (Array.isArray(arr)) { return arr; } else if (Symbol.iterator in Object(arr)) { return sliceIterator(arr, i); } else { throw new TypeError("Invalid attempt to destructure non-iterable instance"); } }; }();

function _toConsumableArray(arr) { if (Array.isArray(arr)) { for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) { arr2[i] = arr[i]; } return arr2; } else { return Array.from(arr); } }

function LocomotiveChart(_ref) {
    var data = _ref.data;

    var _useState = useState("F(v)"),
        _useState2 = _slicedToArray(_useState, 2),
        series = _useState2[0],
        setSeries = _useState2[1];

    useEffect(function () {
        google.charts.load('current', { 'packages': ['corechart'] });
        google.charts.setOnLoadCallback(drawChart);
    }, [data, series]);

    var getSeriesByActualState = function getSeriesByActualState(rawData) {
        var header = rawData.positions;
        var _series = [];
        switch (series) {
            case "F(v)":
                _series = rawData.forceOnSpeed;
                break;
            case "Iдв(v)":
                _series = rawData.motorAmperageOnSpeed;
                break;
            case "Iа(v)":
                _series = rawData.activeAmperageOnSpeed;
                break;
        }
        return [header].concat(_toConsumableArray(_series));
    };

    var drawChart = function drawChart() {
        var chartData = google.visualization.arrayToDataTable(getSeriesByActualState(data));
        var options = {
            curveType: 'function',
            legend: { position: 'bottom' },
            pointSize: 5,
            chartArea: { left: 40, top: 10, bottom: 40, right: 10 },
            explorer: {
                keepInBounds: true,
                maxZoomOut: 1,
                maxZoomIn: 100,
                axis: 'horizontal'
            },
            interpolateNulls: true
        };
        var chart = new google.visualization.LineChart(document.getElementById('locomotiveChart'));
        chart.draw(chartData, options);
    };

    function ChartRadioButton(_ref2) {
        var seriesName = _ref2.seriesName;

        return React.createElement(
            'div',
            { className: 'form-check form-check-inline', style: { marginLeft: 40 } },
            React.createElement('input', {
                className: 'form-check-input',
                type: 'radio',
                name: 'inlineRadioOptions',
                checked: series === seriesName,
                readOnly: true,
                onClick: function onClick() {
                    return setSeries(seriesName);
                }
            }),
            React.createElement(
                'label',
                { className: 'form-check-label', htmlFor: 'inlineRadio1' },
                seriesName
            )
        );
    }

    return React.createElement(
        'div',
        { style: { marginLeft: 10, minWidth: "100vh", border: "solid 1px", padding: 10 } },
        React.createElement(ChartRadioButton, { seriesName: 'F(v)' }),
        React.createElement(
            'div',
            { className: 'form-check form-check-inline' },
            React.createElement('input', {
                className: 'form-check-input',
                type: 'radio',
                name: 'inlineRadioOptions',
                checked: series === "Iдв(v)",
                readOnly: true,
                onClick: function onClick() {
                    return setSeries("Iдв(v)");
                }
            }),
            React.createElement(
                'label',
                { className: 'form-check-label', htmlFor: 'inlineRadio1' },
                'I\u0434\u0432(v)'
            )
        ),
        React.createElement(
            'div',
            { className: 'form-check form-check-inline' },
            React.createElement('input', {
                className: 'form-check-input',
                type: 'radio',
                name: 'inlineRadioOptions',
                checked: series === "Iа(v)",
                readOnly: true,
                onClick: function onClick() {
                    return setSeries("Iа(v)");
                }
            }),
            React.createElement(
                'label',
                { className: 'form-check-label', htmlFor: 'inlineRadio1' },
                'I\u0430(v)'
            )
        ),
        React.createElement('div', {
            id: 'locomotiveChart',
            style: { width: "100%", height: 500 }
        })
    );
}

function TractiveChart(_ref3) {
    var amperage = _ref3.amperage;


    useEffect(function () {
        google.charts.load('current', { 'packages': ['corechart'] });
        google.charts.setOnLoadCallback(drawChart);
    }, [amperage]);

    var drawChart = function drawChart() {
        var chartData = google.visualization.arrayToDataTable(amperage);
        var options = {
            legend: { position: 'bottom' },
            chartArea: { left: 40, top: 10, bottom: 40, right: 10 },
            explorer: {
                keepInBounds: true,
                maxZoomOut: 1,
                maxZoomIn: 100,
                axis: 'horizontal'
            }
        };
        var chart = new google.visualization.LineChart(document.getElementById('tractiveChart'));
        chart.draw(chartData, options);
    };

    return React.createElement(
        'div',
        { style: { marginBottom: 10, minWidth: "100vh", border: "solid 1px", padding: 10 } },
        React.createElement('div', {
            id: 'tractiveChart',
            style: { width: "100%", height: 300 }
        })
    );
}

function SpreadingChart(_ref4) {
    var amperage = _ref4.amperage;


    useEffect(function () {
        google.charts.load('current', { 'packages': ['corechart'] });
        google.charts.setOnLoadCallback(drawChart);
    }, [amperage]);

    var drawChart = function drawChart() {
        var chartData = google.visualization.arrayToDataTable(amperage);
        var options = {
            curveType: 'function',
            legend: { position: 'bottom' },
            chartArea: { left: 40, top: 10, bottom: 40, right: 10 },
            explorer: {
                keepInBounds: true,
                maxZoomOut: 1,
                maxZoomIn: 100,
                axis: 'horizontal'
            }
        };
        var chart = new google.visualization.LineChart(document.getElementById('spreadingChart'));
        chart.draw(chartData, options);
    };

    return React.createElement(
        'div',
        { style: { marginBottom: 10, minWidth: "100vh", border: "solid 1px", padding: 10 } },
        React.createElement('div', {
            id: 'spreadingChart',
            style: { width: "100%", height: 300 }
        })
    );
}