fun main() {
    // Ввод векторов
    print("Введите bb: ")
    val bb = readLine()!!.split(" ").map { it.toInt() }.toIntArray()
    print("Введите aa: ")
    val aa = readLine()!!.split(" ").map { it.toInt() }.toIntArray()

    // Сохранение векторов для печати плана
    val bbs = bb.clone()
    val aas = aa.clone()

    // Матрица X
    val x = Array(aa.size) { IntArray(bb.size) }

    // Текущие значения k, l
    var k: Int
    var l: Int

    do {
        // Выбор k, l
        k = (aa.withIndex().find { it.value > 0 } ?: break).index
        l = (bb.withIndex().find { it.value > 0 } ?: break).index

        // Выписывание маршрута и обновление вектора
        val s = minOf(aa[k], bb[l])
        x[k][l] = s
        aa[k] -= s
        bb[l] -= s

        // Выполнять до тех пор, пока векторы ненулевые
    } while (bb.any { it > 0 } && aa.any { it > 0 })

    // Печать плана
    println("Опорный план:")
    for (i in 1..bb.size) {
        print("\t$i")
    }
    println("\tA")
    for (i in 1..aa.size) {
        print(i)
        for (j in x[i - 1]) {
            print("\t$j")
        }
        println("\t${aas[i - 1]}")
    }
    print("B")
    for (v in bbs) {
        print("\t$v")
    }
    println()

    // Подсчёт количества ненулевых элементов в X
    val actualNonZeroesCount = x.map { it.count { it > 0 } }.sum()
    // n + m - 1
    val requiredNonZeroesCount = bb.size + aa.size - 1
    // Невырожденный ли план
    val isNotSingular = actualNonZeroesCount == requiredNonZeroesCount

    // Печать информации о вырожденности/невырожденности плана:
    println("\n(m = ${aa.size}) + (n = ${bb.size}) - 1 = $requiredNonZeroesCount " +
            "${if (isNotSingular) "==" else "!="} $actualNonZeroesCount " +
            "-> план ${if (isNotSingular) "не" else ""}вырожденный")
}