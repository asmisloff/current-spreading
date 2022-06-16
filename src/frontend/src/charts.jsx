function LocomotiveChart({ data }) {
    const [series, setSeries] = useState("F(v)")

    useEffect(
        () => {
            google.charts.load('current', { 'packages': ['corechart'] });
            google.charts.setOnLoadCallback(drawChart)
        },
        [data, series]
    )

    const getSeriesByActualState = rawData => {
        const header = rawData.positions
        let _series = []
        switch (series) {
            case "F(v)":
                _series = rawData.forceOnSpeed
                break
            case "Iдв(v)":
                _series = rawData.motorAmperageOnSpeed
                break
            case "Iа(v)":
                _series = rawData.activeAmperageOnSpeed
                break
        }
        return [header, ..._series]
    }

    const drawChart = () => {
        const chartData = google.visualization.arrayToDataTable(getSeriesByActualState(data))
        const options = {
            curveType: 'function',
            legend: { position: 'bottom' },
            pointSize: 5,
            chartArea: { left: 40, top: 10, bottom: 40, right: 10 },
            explorer: {
                keepInBounds: true,
                maxZoomOut: 1,
                maxZoomIn: 100,
                axis: 'horizontal',
            },
            interpolateNulls: true
        }
        var chart = new google.visualization.LineChart(document.getElementById('locomotiveChart'))
        chart.draw(chartData, options)
    }

    function ChartRadioButton({ seriesName }) {
        return (
            <div className="form-check form-check-inline" style={{ marginLeft: 40 }}>
                <input
                    className="form-check-input"
                    type="radio"
                    name="inlineRadioOptions"
                    checked={series === seriesName}
                    readOnly={true}
                    onClick={() => setSeries(seriesName)}
                />
                <label className="form-check-label" htmlFor="inlineRadio1">{seriesName}</label>
            </div>
        )
    }

    return (
        <div style={{ marginLeft: 10, minWidth: "100vh", border: "solid 1px", padding: 10 }}>
            {/* <div className="form-check form-check-inline" style={{marginLeft: 40}}>
                <input
                    className="form-check-input"
                    type="radio"
                    name="inlineRadioOptions"
                    checked={series === "F(v)"}
                    readOnly={true}
                    onClick={() => setSeries("F(v)")}
                />
                <label className="form-check-label" htmlFor="inlineRadio1">F(v)</label>
            </div> */}
            <ChartRadioButton seriesName="F(v)" />
            <div className="form-check form-check-inline">
                <input
                    className="form-check-input"
                    type="radio"
                    name="inlineRadioOptions"
                    checked={series === "Iдв(v)"}
                    readOnly={true}
                    onClick={() => setSeries("Iдв(v)")}
                />
                <label className="form-check-label" htmlFor="inlineRadio1">Iдв(v)</label>
            </div>
            <div className="form-check form-check-inline">
                <input
                    className="form-check-input"
                    type="radio"
                    name="inlineRadioOptions"
                    checked={series === "Iа(v)"}
                    readOnly={true}
                    onClick={() => setSeries("Iа(v)")}
                />
                <label className="form-check-label" htmlFor="inlineRadio1">Iа(v)</label>
            </div>
            <div
                id="locomotiveChart"
                style={{ width: "100%", height: 500 }}
            >
                {/* Chart */}
            </div>
        </div>
    )
}

function TractiveChart({ amperage }) {

    const drawChart = () => {
        const header = ["x", "Ток поезда", "Профиль пути"]
        const preparedData = amperage.map(entry => [entry.c, entry.a, entry.p])
        const chartData = google.visualization.arrayToDataTable([header, ...preparedData])
        const options = {
            legend: { position: 'bottom' },
            chartArea: { left: 80, top: 10, bottom: 40, right: 80 },
            explorer: {
                keepInBounds: true,
                maxZoomOut: 1,
                maxZoomIn: 100,
                axis: 'horizontal',
            },
            vAxes: {
                0: {
                    title: "Ток поезда, А",
                    textStyle: { color: 'blue' }
                },
                1: {
                    title: "Профиль пути",
                    textStyle: { color: 'red' }
                }
            },
            series: {
                0: { targetAxisIndex: 0 },
                1: { targetAxisIndex: 1 },
            }
        }
        var chart = new google.visualization.LineChart(document.getElementById('tractiveChart'))
        chart.draw(chartData, options)
    }

    google.charts.load('current', { 'packages': ['corechart'] });
    google.charts.setOnLoadCallback(drawChart)

    return (
        <div style={{ marginBottom: 10, minWidth: "100vh", border: "solid 1px", padding: 10 }}>
            <div
                id="tractiveChart"
                style={{ width: "100%", height: 300 }}
            >
                {/* Chart */}
            </div>
        </div>
    )

}

function SpreadingChart({ amperage }) {

    const drawChart = () => {
        const chartData = google.visualization.arrayToDataTable(amperage)
        const options = {
            curveType: 'function',
            legend: { position: 'bottom' },
            chartArea: { left: 40, top: 10, bottom: 40, right: 10 },
            explorer: {
                keepInBounds: true,
                maxZoomOut: 1,
                maxZoomIn: 100,
                axis: 'horizontal',
            }
        }
        var chart = new google.visualization.LineChart(document.getElementById('spreadingChart'))
        chart.draw(chartData, options)
    }

    google.charts.load('current', { 'packages': ['corechart'] });
    google.charts.setOnLoadCallback(drawChart)

    return (
        <div style={{ marginBottom: 10, minWidth: "100vh", border: "solid 1px", padding: 10 }}>
            <div
                id="spreadingChart"
                style={{ width: "100%", height: 300 }}
            >
                {/* Chart */}
            </div>
        </div>
    )

}