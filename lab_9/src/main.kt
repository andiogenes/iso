import java.util.*

fun main() {
    with(TransportationProblem()) {
        // Находим допустимый план методом северо-западного угла
        northWestCorner()
        printPlan("Первый допустимый план")
        // Запускаем метод разности потенциалов
        calculatePotentials()
        printPlan("Оптимальный план (Итерация №$currentIteration)")
    }
}

class TransportationProblem {
    // Вектор объёмов производства
    private val a: IntArray
    // Вектор объемов потребления
    private val b: IntArray
    // Матрица цен
    private val c: Array<DoubleArray>

    // Матрица путей из A в B
    private val x: Array<Array<Pipe>>

    // Начальный вектор объёмов производства
    private val aSave: List<Int>
    // Начальный вектор объемов потребления
    private val bSave: List<Int>

    // Путь из A[i] в B[j], объема [x]*[c]
    data class Pipe(var x: Double, val c: Double, val i: Int, val j: Int)
    companion object {
        // Нулевая ячейка (без "малой" прибавки)
        private val NIL = Pipe(0.0, 0.0, -1, -1)
    }

    init {
        print("Введите bb: ")
        val bb = readLine()!!.split(" ").map { it.toInt() }.toMutableList()
        print("Введите aa: ")
        val aa = readLine()!!.split(" ").map { it.toInt() }.toMutableList()

        val ac = aa.size

        // Балансировка плана
        val aas = aa.sum()
        val bbs = bb.sum()
        if (aas != bbs) println("Задача не сбалансирована")
        if (aas > bbs) {
            bb.add(aas - bbs)
            println("Добавили фиктивного потребителя объёма ${bb.last()}")
        } else if (bbs > aas) {
            aa.add(bbs - aas)
            println("Добавили фиктивного поставщика объёма ${aa.last()}")
        }
        a = aa.toIntArray()
        b = bb.toIntArray()

        aSave = a.map { it }.toList()
        bSave = b.map { it }.toList()

        c = Array(a.size) { DoubleArray(b.size) }
        x = Array(a.size) { Array(b.size) { NIL } }
        println("Введите матрицу C:")
        for (i in 0 until ac) {
            val elements = readLine()!!.split(" ").map { it.toDouble() }.withIndex()
            for ((j, v) in elements) {
                c[i][j] = v
            }
        }
    }

    /**
     * Построение первоначального плана методом северо-западного угла
     */
    fun northWestCorner() {
        var k: Int
        var l: Int
        do {
            // Выбор k, l
            k = (a.withIndex().find { it.value > 0 } ?: break).index
            l = (b.withIndex().find { it.value > 0 } ?: break).index

            // Выписывание маршрута и обновление вектора
            val s = minOf(a[k], b[l])
            x[k][l] = Pipe(s.toDouble(), this.c[k][l], k, l)
            a[k] -= s
            b[l] -= s

            // Выполнять до тех пор, пока векторы ненулевые
        } while (b.any { it > 0 } && a.any { it > 0 })
    }

    /**
     * Счётчик итераций.
     */
    var currentIteration = 1

    /**
     * Шаг метода разности потенциалов
     */
    fun calculatePotentials() {
        // Максимальная прибавка
        var maxDistance = 0.0
        // Контур, по которому следует прибавить q
        var contour: Array<Pipe>? = null
        // Минимальный чётный элемент
        var minimalEvenElement = NIL
        regenerate()

        for (r in a.indices) {
            for (c in b.indices) {
                if (x[r][c] != NIL) continue
                val from = Pipe(0.0, this.c[r][c], r, c)
                val path = getCycle(from)
                var dist = 0.0
                var leastX = Int.MAX_VALUE.toDouble()
                var minimalEvenCandidate = NIL
                var positive = true
                for (s in path) {
                    if (positive) {
                        dist += s.c
                    } else {
                        dist -= s.c
                        if (s.x < leastX) {
                            minimalEvenCandidate = s
                            leastX = s.x
                        }
                    }
                    positive = !positive
                }
                if (dist < maxDistance) {
                    contour = path
                    minimalEvenElement = minimalEvenCandidate
                    maxDistance = dist
                }
            }
        }

        // Если контур существует
        if (contour != null) {
            println("Оптимальный план не получен, продолжаем оптимизацию")
            println("(k, l) = (${contour.first().i+1}, ${contour.first().j+1})")
            println("Цикл: ${contour.joinToString(", ") { "(${it.i + 1}, ${it.j + 1})" }}")
            printPlan("Итерация ${currentIteration++}")
            val q = minimalEvenElement.x
            var plus = true
            for (s in contour) {
                s.x += if (plus) q else -q
                x[s.i][s.j] = if (s.x == 0.0) NIL else s
                plus = !plus
            }
            calculatePotentials()
        }
    }

    private fun matrixToList() =
        LinkedList<Pipe>(x.flatten().filter { it != NIL })

    /**
     * Чётный цикл, начинающийся с [s].
     */
    private fun getCycle(s: Pipe): Array<Pipe> {
        // Все ненулевые элементы
        val path = matrixToList()
        path.addFirst(s)

        // Пока не останутся только попарно соседние элементы
        while (path.removeIf {
                val adjacent = getAdjacent(it, path)
                adjacent[0] == NIL || adjacent[1] == NIL
            });

        // Построение чётного цикла
        val nodes = Array(path.size) { NIL }
        var prev = s
        for (i in nodes.indices) {
            nodes[i] = prev
            prev = getAdjacent(prev, path)[i % 2]
        }
        return nodes
    }

    /**
     * Получение соседних элементов [s].
     */
    private fun getAdjacent(s: Pipe, lst: LinkedList<Pipe>): Array<Pipe> {
        val adjacent = Array(2) { NIL }
        for (o in lst) {
            if (o != s) {
                if (o.i == s.i && adjacent[0] == NIL)
                    adjacent[0] = o
                else if (o.j == s.j && adjacent[1] == NIL)
                    adjacent[1] = o
                if (adjacent[0] != NIL && adjacent[1] != NIL) break
            }
        }
        return adjacent
    }

    /**
     * Приведение плана к невырожденному путём увеличения нулевой ячейки на бесконечно малую положительную величину.
     */
    private fun regenerate() {
        val eps = Double.MIN_VALUE
        val basisSize = matrixToList().size
        if (a.size + b.size - 1 != basisSize) {
            println("(m = ${a.size}) + (n = ${b.size}) != $basisSize, план вырожденный, необходимо ввести \"нулевой\" элемент в базис")
            for (r in a.indices) {
                for (c in b.indices) {
                    if (x[r][c] == NIL) {
                        val dummy = Pipe(eps, this.c[r][c], r, c)
                        if (getCycle(dummy).isEmpty()) {
                            println("Ввели (${r + 1}, ${c + 1}) = 0")
                            x[r][c] = dummy
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * Печать плана в консоль.
     */
    fun printPlan(title: String) {
        println(title)
        var totalCosts = 0.0
        for (i in 1..b.size) {
            print("\t$i")
        }
        println("\tA")
        for (i in 1..a.size) {
            print(i)
            for (j in x[i - 1]) {
                print("\t${if (j != NIL) (if (j.x.toInt() == 0) "0 + ε" else "${j.x.toInt()}") else "0"}")
                totalCosts += j.x * j.c
            }
            println("\t${aSave[i - 1]}")
        }
        print("B")
        for (v in bSave) {
            print("\t$v")
        }
        println("\nОбщая стоимость: $totalCosts\n")
    }

}
