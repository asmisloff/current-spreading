const useState = React.useState
const useEffect = React.useEffect
const useRef = React.useRef

const apiUrl = "http://localhost:8080/api"

function toNumber(s) {
  switch (s) {
    case '.': case ',': case '-': case '-.':
      return 0
    default:
      return +s
  }
}

function range(end, start) {
  const res = []
  for (let i = start === undefined ? 0 : start; i < end; ++i) {
    res.push(i)
  }
  return res
}

class Complex {

  constructor(re, im) {
    this.re = re
    this.im = im
  }

  magn() {
    return Math.sqrt(this.re * this.re + this.im * this.im)
  }

  plus(other) {
    return new Complex(this.re + other.re, this.im + other.im)
  }

  static magn(re, im) {
    return Math.sqrt(re * re + im * im)
  }

}

function Worksheet() {
  const [locomotiveCurrent, setLocomotiveCurrent] = useState("DIRECT_CURRENT")
  const [locomotiveChartData, setLocomotiveChartData] = useState({
    positions: [], forceOnSpeed: []
  })
  const [direction, setDirection] = useState("Слева направо")
  const [tractiveData, setTractiveData] = useState([])
  const [trainPosition, setTrainPosition] = useState(0)
  const [trainAmperage, setTrainAmperage] = useState(0)
  const [spreading, setSpreading] = useState([])

  useEffect(() => {
    fetch(`${apiUrl}/locomotive/chartData/${locomotiveCurrent}`, { method: "GET" })
      .then((resp) => resp.json())
      .then(data => setLocomotiveChartData(data))
  }, [locomotiveCurrent])

  useEffect(() => {
    fetch(`${apiUrl}/tractive/perform`, {
      method: "POST",
      mode: "cors",
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(
        {
          locomotiveCurrent: locomotiveCurrent,
          direction: direction === "Слева направо" ? "LeftToRight" : "RightToLeft",
          brakeType: "IRON",
          carQty: 10,
          tractionRate: 1.0
        }
      )
    })
      .then((resp) => resp.json())
      .then(data => setTractiveData(data))
  }, [direction, locomotiveCurrent])

  useEffect(() => {
    const amp = getTrainAmperageByCoordinate(trainPosition, tractiveData)
    setTrainAmperage(amp)
    let url
    if (locomotiveCurrent == "DIRECT_CURRENT") {
      url = `${apiUrl}/ic/dc/solve?coord=${trainPosition}&amp=${-amp.aa}`
    } else {
      url = `${apiUrl}/ic/ac/solve?coord=${trainPosition}&activeAmp=${amp.aa}&fullAmp=${amp.fa}`
    }
    fetch(url, { method: "GET" })
      .then(resp => resp.json())
      .then(solutions => {
        if (solutions.msg !== undefined) {
          alert(solutions.msg)
          return
        }
        setSpreading(
          [
            ['x', "Изменение потенциала рельсов относительно земли. Поперечное сечение."],
            ...solutions.map(s => [s.coordinate, total(s.amperages)])
          ]
        )
      })
  }, [trainPosition, locomotiveCurrent, tractiveData])

  const total = (ar) => {
    if (ar.length === 0) return 0

    let sum = locomotiveCurrent === "DIRECT_CURRENT" ? 0 : new Complex(0, 0)
    const plus = locomotiveCurrent === "DIRECT_CURRENT" 
      ? (x, y) => x + y
      : (x, y) => x.plus(y)

    if (ar.length > 1) ar = ar.slice(0, -1)
    for (elt of ar) {
      sum = plus(sum, elt)
    }
    if (locomotiveCurrent === "DIRECT_CURRENT") {
      return sum
    } else {
      return sum.magn()
    }
  }

  const getTrainAmperageByCoordinate = (x, td) => {
    if (td.length === 0) {
      return { aa: 0, fa: 0 }
    }
    let comparator
    if (direction === "Слева направо") {
      comparator = (coord, elt) => coord - elt.c
    } else {
      comparator = (coord, elt) => elt.c - coord
    }
    const i = binarySearch(td, x, comparator)
    if (i >= 0) {
      return { aa: td[i].aa, fa: td[i].a }
    } else {
      let j = -i - 1
      if (j >= td.length) j = td.length - 1
      return { aa: td[j].aa, fa: td[j].a }
    }
  }

  const binarySearch = (ar, el, cmp) => {
    let m = 0
    let n = ar.length - 1
    while (m <= n) {
      const k = (n + m) >> 1
      const cmpRes = cmp(el, ar[k])
      if (cmpRes > 0) {
        m = k + 1
      } else if (cmpRes < 0) {
        n = k - 1
      } else {
        return k
      }
    }
    return -m - 1
  }

  return (
    <div style={{ padding: "15px 20px 20px 20px" }}>
      <div style={{ display: "flex", marginBottom: 10 }}>
        <div style={{ flex: "0 1 300px", border: "solid 1px", padding: 10 }}>
          <div className="panel">
            <label htmlFor="schemaType">Тип схемы</label>
            <select
              name="schemaType"
              className="form-select" size="1"
              onChange={e => {
                const v = e.target.value
                setLocomotiveCurrent(v === "DIRECT_CURRENT" ? "DIRECT_CURRENT" : "ALTERNATING_CURRENT")
              }}
              style={{ minWidth: "100%" }}
            >
              <option value="DIRECT_CURRENT">Постоянный ток 3,3 кВ</option>
              <option value="ALTERNATING_CURRENT">Переменный ток 27,5 кВ</option>
              <option value="DOUBLE_ALTERNATING_CURRENT">Переменный ток 2х27,5 кВ</option>
            </select>
          </div>
          <div className="panel">
            <label htmlFor="trackType">Характер участка</label>
            <select name="trackType" className="form-select" size="1">
              <option value="5">Пятипутный</option>
              <option value="?">Ещё какой-то</option>
            </select>

            <label htmlFor="ballastType">Тип балласта</label>
            <select name="ballastType" className="form-select" size="1">
              <option value="old">Старый балласт</option>
              <option value="geotextile+ps">Геотекстиль + пеноплекс</option>
              <option value="geotextile">Геотекстиль</option>
              <option value="ps">Пеноплекс</option>
            </select>

            <label htmlFor="trackNumber">Путь</label>
            <input type="number" min={1} max={6} name="trackNumber" className="form-control" defaultValue={1} />
          </div>
          <div style={{ marginTop: 100 }}>
            <h5> Количество вагонов </h5>
            <input
              type="number"
              min={0}
              max={50}
              defaultValue={10}
              style={{ minWidth: "100%" }}
            />
          </div>
          <div style={{ marginTop: 30 }}>
            <h5> Направление движения </h5>
            <select
              className="form-select" size="1"
              onChange={e => {
                setDirection(e.target.value)
              }}
              style={{ minWidth: "100%" }}
            >
              <option value="Слева направо">Слева направо</option>
              <option value="Справа налево">Справа налево</option>
            </select>
          </div>
        </div>
        <div style={{ flex: 1 }}>
          <LocomotiveChart
            data={locomotiveChartData}
          />
        </div>
      </div>
      <TractiveChart amperage={tractiveData} />
      <Schema tractiveDirection={direction} trainPosition={trainPosition} onTrainMoved={(_, x) => setTrainPosition(x)} />
      <div className="chartContainer">
        <SpreadingChart amperage={spreading} />
      </div>
    </div>
  )
}
