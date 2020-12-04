import kotlin.math.abs

// Выбор очередной стратегии А
fun a(matrix: Array<Array<Int>>): Int =
    matrix.map { it.sum() }.withIndex().maxBy { it.value }!!.index

// Выбор очередной антагонисти
fun b(matrix: Array<Array<Int>>, i: Int): Int =
    matrix[i].withIndex().minBy { it.value }!!.index

// Получение i столбца матрицы
fun col(matrix: Array<Array<Int>>, i: Int): List<Int> =
    matrix.map { it[i] }

fun main() {
    // Платежная матрица задачи
    val matrix = arrayOf(
        arrayOf(40, 20, 0),
        arrayOf(30, 50, 20),
        arrayOf(10, 40, 70)
    )
    // Точность вычислений
    val eps = 0.0001

    // Номер текущей итерации
    var currentIteration = 1

    // Номер стратегии Ai
    var i = a(matrix)
    // Номер стратегии B
    var j = b(matrix, i)

    // Текущий вектор B
    var b = matrix[i].toList()
    // Текущий вектор A
    var a = col(matrix, j)

    // Нижняя граница v
    var vBottom = b.min()!!.toDouble()
    // Верхняя граница v
    var vTop = a.max()!!.toDouble()

    // Стохастические веса стратегий
    val p = IntArray(matrix.first().size).also { it[i]++ }
    val q = IntArray(matrix.size).also { it[j]++ }

    // Печать хода алгоритма
    fun printMove() {
        println("$currentIteration\t${i + 1}\t${j + 1}\t\t${b.joinToString(",")}\t\t${a.joinToString(",")}\t$vBottom\t$vTop\t${(vBottom + vTop) / 2.0}")
    }

    printMove()

    // Ход алгоритма
    // Пока расстояние между границами больше eps
    while (abs(vTop - vBottom) > eps) {
        // Пересчитываем шаг
        currentIteration++
        // Выбор новых A, B
        i = a.withIndex().maxBy { it.value }!!.index
        b = b.zip(matrix[i]).map { it.first + it.second }
        j = b.withIndex().minBy { it.value }!!.index
        a = a.zip(col(matrix, j)).map { it.first + it.second }
        // Вычисление vBottom, vTop
        vBottom = b.min()!! / currentIteration.toDouble()
        vTop = a.max()!! / currentIteration.toDouble()

        // Прибавление числа выбора определенной стратегии
        p[i]++
        q[j]++

        // Печать шага
        printMove()
    }

    // Печать ответа
    println("\nv = ${(vTop + vBottom) / 2}, Sa = ${p.map { it / (currentIteration - 1).toDouble() }
        .joinToString(",")}, Sb = ${q.map { it / (currentIteration + 1).toDouble() }.joinToString(",")}")

}