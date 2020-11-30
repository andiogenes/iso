import java.io.File
import java.lang.IllegalStateException
import kotlin.math.abs
import kotlin.math.sign

/**
 * Неравенство системы ЗЛП.
 *
 * - [sign] - знак неравенства (0 <=> ==, -1 <=> <=, 1 <=> >=)
 * - [left] - коэффициенты переменных в левой части
 * - [b] - свободный член
 */
data class Inequality(var sign: Int, val left: MutableMap<String, Double>, var b: Double)

/**
 * Список строк выходных данных для формирования LaTeX-отчёта.
 */
val texOutput = arrayListOf<String>()

/**
 * Печать в TeX-"буфер".
 */
fun texPrint(string: String) {
    texOutput.add(string)
}

/**
 * Печать в TeX-"буфер" с переносом строки.
 */
fun texPrintln(string: String) {
    texOutput.add("$string\\newline\n")
}

/**
 * Генерация строки из TeX-"буфера".
 */
fun texMaterialize(): String {
    return texOutput.reduce { acc, s -> acc + s }
}

fun main(args: Array<String>) {
    /**
     * Форматирование строки коэфициентов в виде арифметического выражения.
     */
    fun Map<String, Double>.toExpression(): String {
        fun format(name: String, value: Double, showSign: Boolean = false): String {
            if (abs(value.sign) < Double.MIN_VALUE) return "0$name"
            val sign = if (showSign) { if (value.sign < 0) "-" else "" } else ""
            return "$sign${abs(value)}$name"
        }
        return this.map { (k, v) -> "${k[0]}_${k.drop(1)}" to v }.let {
            val (firstName, firstValue) = it[0]
            it.drop(1).fold(format(firstName, firstValue, true), { acc: String, (name, value) ->
                "$acc ${if (value.sign < 0) "-" else "+"} ${abs(value)}$name"
            })
        }
    }

    /**
     * Печать условий (целевой функции, ограничений).
     */
    fun printConditions(mode: String, f: Map<String, Double>, bounds: Array<Inequality>) {
        texPrint("\\begin{equation*}\n")
        texPrint("F = ${f.minus("b").toExpression()} \\rightarrow \\$mode\n")
        texPrint("\\end{equation*}\n")

        texPrint("\\begin{equation*}\n")
        texPrint("\\begin{cases}\n")
        for ((sign, left, b) in bounds) {
            val texSign = when(sign) {
                -1 -> "\\le"
                0 -> "="
                1 -> "\\ge"
                else -> error("should not reach here")
            }
            texPrint("${left.toExpression()} & $texSign $b \\\\\n")
        }
        texPrint("\\end{cases}\n")
        texPrint("\\end{equation*}\n")
    }

    val fileName = args[0]
    val (mode, f, bounds) = read(fileName)
    val m = bounds.size
    try {
        val oldF = f.map { (k, v) -> k to v }.toMap().toMutableMap()

        texPrintln("\\textbf{Тест \\textit{$fileName}.}")
        texPrintln("\\textbf{Условия задачи:}")
        printConditions(mode, f, bounds)

        val (canonF, canonBounds) = canonization(f, mode, bounds)

        texPrintln("\n\\textbf{Задача, после приведения к каноническому виду:}")
        printConditions("max", canonF, canonBounds)

        val (simpleF, simpleBounds, simpleBasis) = getSimpleBasis(canonF, canonBounds)

        texPrintln("\n\\textbf{Задача, после выбора базиса:}")
        texPrintln("Базис: \$(${simpleBasis.joinToString(", ") { "${it[0]}_${it.drop(1)}" }})\$")
        printConditions("max", simpleF, simpleBounds)

        val table = getFirstSimplexTable(simpleF, simpleBounds, simpleBasis)
        val afterTable = calculateSimplex(table)
        val (fMax, commonSolution, dualSolution) = restoreResult(oldF.size - 1, m, oldF, afterTable)
        val (solution, zSolution) = commonSolution

        texPrintln("\\textbf{Оптимальное решение прямой ЗЛП:}")
        texPrintln("\$F_{$mode}\$ = $fMax;")
        texPrintln(
            "\$X\$ = \$( ${solution.keys.joinToString(", ") { "${it[0]}_${it.drop(1)}" }} )\$ = \$( ${
                solution.values.joinToString(
                    ", "
                )
            } )\$;"
        )
        texPrintln(
            "\$Z\$ = \$( ${zSolution.keys.joinToString(", ") { "${it[0]}_${it.drop(1)}" }} )\$ = \$( ${
                zSolution.values.joinToString(
                    ", "
                )
            } )\$."
        )
        texPrintln("\\textbf{Оптимальное решение двойственной ЗЛП:}")
        texPrintln("\$L\$ = $fMax;")
        texPrintln(
            "\$Y\$ = \$( ${dualSolution.keys.joinToString(", ") { "${it[0]}_${it.drop(1)}" }} )\$ = \$( ${
                dualSolution.values.joinToString(
                    ", "
                )
            } )\$."
        )

        File("$fileName.output").printWriter().use { it.println(texMaterialize()) }
    } catch (e: IllegalStateException) {
        File("$fileName.output").printWriter().use {
            it.println(texMaterialize())
            it.println("\\underline{${e.message}}")
        }
        throw e
    }
}

/**
 * Чтение исходных данных из файла [fileName].
 */
fun read(fileName: String): Triple<String, MutableMap<String, Double>, Array<Inequality>> {
    with(File(fileName)) {
        val lines = readLines()
        // Чтение стремления функции
        val mode = lines[0]
        // Чтение функции
        val f = lines[1]
            .split(' ')
            .map { it.toDouble() }
            .withIndex().map { (i, v) -> "x${i+1}" to v }
            .plus("b" to 0.0)
            .toMap().toMutableMap()

        // Чтение ограничений
        val boundaries = lines
            .drop(2)
            .map {
                val sign = when (true) {
                    it.contains("<=") -> -1
                    it.contains(">=") -> 1
                    it.contains("==") -> 0
                    else -> error("incorrect input")
                }
                val elements = it.split("(<=)|(>=)|(==)".toRegex())
                    .flatMap { v -> v.split(" ") }
                    .filter { v -> v.isNotBlank() }
                    .map { v -> v.toDouble() }

                val left = elements.dropLast(1).withIndex()
                    .map { (i, v) -> "x${i+1}" to v }.toMap().toMutableMap()

                val b = elements.last()

                Inequality(sign, left, b)
            }.toTypedArray()

        return Triple(mode, f, boundaries)
    }
}

/**
 * Восстанавливает результат по симплекс-таблице.
 */
fun restoreResult(n: Int, m: Int, f: Map<String, Double>, table: Map<String, Map<String, Double>>): Triple<Double, Pair<Map<String, Double>, Map<String, Double>>, Map<String, Double>> {
    // Карта решения задачи
    var solution: MutableMap<String, Double> = mutableMapOf()

    // Получаем решение
    for ((rowKey, row) in table) {
        if (rowKey != "F") {
            solution[rowKey] = row["b"] ?: 0.0
        }
    }

    // Выполняем обратную замену xi = mi - ni
    for ((k, v) in solution) {
        if (k[0] == 'm') {
            var nK = ""
            var nVal = 0.0
            for ((nKey, nValue) in solution) {
                if (nKey[0] == 'n' && nKey.drop(1) == k.drop(1)) {
                    nK = nKey
                    nVal = nValue
                    break
                }
            }
            if (nK == "") {
                nVal = 0.0
            }
            solution["x${k.drop(1)}"] = v - nVal
            solution.remove(k)
            solution.remove(nK)
        } else if (k[0] == 'n') {
            var mK = ""
            var mVal = 0.0
            for ((mKey, mValue) in solution) {
                if (mKey[0] == 'm' && mKey.drop(1) == k.drop(1)) {
                    mK = mKey
                    mVal = mValue
                    break
                }
            }
            if (mK == "") {
                mVal = 0.0
            }
            solution["x${k.drop(1)}"] = v - mVal
            solution.remove(k)
            solution.remove(mK)
        }
    }
    // Столбец решений Z
    val zSolution = solution.filter { (vrbl, _) -> vrbl[0] == 'z' }.toMutableMap()
    // Убираем искусственные переменные
    solution = solution.filterNot { (vrbl, _) -> vrbl[0] != 'x' }.toMutableMap()

    // Получаем значения функции
    var fVal = f["b"] ?: 0.0
    for ((vrbl, c) in solution) {
        fVal += (f[vrbl] ?: 0.0) * c
    }

    // Дописываем нулевые переменные
    for (i in 1..n) {
        val vrbl = "x$i"
        if (!solution.contains(vrbl)) {
            solution[vrbl] = 0.0
        }
    }

    // Дописываем нулевые переменные для Z
    for (i in 1..m) {
        val vrbl = "z$i"
        if (!zSolution.contains(vrbl)) {
            zSolution[vrbl] = 0.0
        }
    }

    // Получаем решение двойственной задачи
    val dualSolution = mutableMapOf<String, Double>()
    for ((vrbl, v) in table["F"] ?: error("F is empty")) {
        if (vrbl[0] != 'x' && vrbl != "b") {
            dualSolution["y${vrbl.drop(1)}"] = v
        }
    }

    return Triple(fVal, (solution.toSortedMap() to zSolution.toSortedMap()), dualSolution.toSortedMap())
}

/**
 * Вычислительный ход симплекс-метода.
 */
fun calculateSimplex(tbl: MutableMap<String, MutableMap<String, Double>>): Map<String, Map<String, Double>> {
    var table = tbl
    // Если есть М-строка, сначала применяем М-метод
    if (table["M"] != null) {
        var count = 0
        // Пока не достигнута оптимальность М-методом
        while (!crtCheck(table["M"] ?: error("M not found"))) {
            // Разрешающий столбец
            val s = getS(table["M"] ?: error("M not found"))
            // Разрешающая строка
            val q = getQ(table, s)
            // Обновляем симплекс-таблицу
            table = editSimplexTable(table, s, q)
            printSimplexTable("Итерация М-метода №${count+1}", table)
            count++
        }
        // М-строка удаляется за ненадобностью
        table.remove("M")
        for ((rowKey, _) in table) {
            // Удаляем искусственные переменные из таблицы
            table[rowKey] = table[rowKey]!!
                .filterNot { (colKey, _) -> colKey[0] == 'a' }
                .toMutableMap()
        }
    }
    // Непосредственно ход симплекс-метода
    var count = 0
    // Пока не достигнут оптимум
    while (!crtCheck(table["F"] ?: error("F not found"))) {
        // Разрешающий столбец
        val s = getS(table["F"] ?: error("F not found"))
        // Разрешающая строка
        val q = getQ(table, s)
        // Обновляем таблицу
        table = editSimplexTable(table, s, q)
        printSimplexTable("Итерация симплекс-метода №${count+1}", table)
        count++
    }
    return table
}

/**
 * Обновление симплекс-таблицы.
 */
fun editSimplexTable(table: Map<String, Map<String, Double>>, s: String, q: String): MutableMap<String, MutableMap<String, Double>> {
    // Формирование новой таблицы
    val newTable = mutableMapOf<String, MutableMap<String, Double>>()
    // Введение новой базисной переменной
    newTable[s] = mutableMapOf()
    for ((rowKey, _) in table) {
        if (rowKey != q) {
            // Вставка остальных строк
            newTable[rowKey] = mutableMapOf()
        }
    }
    // Разрешающий элемент
    val qs = (table[q] ?: error("q not found"))[s] ?: 0.0
    // Заполняем новую базисную строку
    for ((sKey, sValue) in table[q] ?: error("q not found")) {
        // Делением старой на разрешающий элемент
        newTable[s]!![sKey] = sValue / qs
    }
    // Заполняем остальные строки таблицы
    for ((rowKey, _) in table) {
        if (rowKey != q) {
            for ((colKey, value) in table[rowKey] ?: error("row key not found")) {
                newTable[rowKey]!![colKey] = value - (((table[rowKey] ?: error("row key not found"))[s] ?: 0.0) * ((table[q] ?: error("q key notFound"))[colKey] ?: 0.0)) / qs
            }
        }
    }
    return newTable
}

/**
 * Получение разрешающей строки.
 */
fun getQ(table: Map<String, Map<String, Double>>, s: String): String {
    var minKey = ""
    var minElem = Double.POSITIVE_INFINITY
    for ((rowKey, row) in table) {
        if (rowKey != "M" && rowKey != "F") {
            val o = getO(row, s)
            if (minElem.isInfinite() || (!o.isInfinite() && o < minElem)) {
                minKey = rowKey
                minElem = o
            }
        }
    }
    if (minElem.isInfinite()) {
        error("Функция не ограничена на множестве допустимых решений - задача не имеет конечного решения")
    }
    return minKey
}

/**
 * Расчёт оценочного отношения для строки.
 */
fun getO(row: Map<String, Double>, s: String): Double {
    return if ((row[s] ?: 0.0) == 0.0 || (row[s] ?: 0.0) == -0.0) {
        Double.POSITIVE_INFINITY
    } else if ((row["b"] ?: 0.0) == 0.0 || (row["b"] ?: 0.0) == -0.0) {
        if ((row[s] ?: 0.0) < 0) {
            Double.POSITIVE_INFINITY
        } else {
            0.0
        }
    } else if (((row[s] ?: 0.0) < 0 && (row["b"] ?: 0.0) > 0) || ((row[s] ?: 0.0) > 0 && (row["b"] ?: 0.0) < 0)) {
        Double.POSITIVE_INFINITY
    } else {
        abs((row["b"] ?: 0.0) / (row[s] ?: 0.0))
    }
}

/**
 * Получение разрешающего столбца.
 */
fun getS(row: Map<String, Double>): String {
    var minKey = ""
    var minElem = 0.0
    for ((key, elem) in row) {
        if (key != "b" && elem < minElem) {
            minKey = key
            minElem = elem
        }
    }
    if (minKey == "") {
        error("Некорректная M/F-строка для поиска разрешающего столбца")
    }
    return minKey
}

/**
 * Проверка критерия оптимальности
 */
fun crtCheck(row: Map<String, Double>): Boolean {
    for ((key, elem) in row) {
        if (key != "b" && elem < 0 && elem != -0.0) {
            // Существует элемент < 0 => план неоптимален
            return false
        }
    }
    return true
}

/**
 * Получение симплекс-таблицы для ЗЛП, приведенной к каноническому виду.
 */
fun getFirstSimplexTable(f: Map<String, Double>, lim: Array<Inequality>, basis: Array<String>): MutableMap<String, MutableMap<String, Double>> {
    val table = mutableMapOf<String, MutableMap<String, Double>>()
    // Заполнение строк равенст ограничений
    for ((index, ineq) in lim.withIndex()) {
        val basisVrbl = basis[index]
        table[basisVrbl] = mutableMapOf()
        for ((vrbl, c) in ineq.left) {
            // Коэффициенты переменных
            table[basisVrbl]!![vrbl] = c
        }
        // Свободный член
        table[basisVrbl]!!["b"] = ineq.b
    }
    // Заполнение строки функции
    table["F"] = mutableMapOf()
    for ((vrbl, c) in f) {
        // Коэффициенты целевой функции заносятся с отрицательным знаком
        table["F"]!![vrbl] = -c
    }
    // Проверка на наличие искусственных переменных
    var hasA = false
    for ((_, basisVrbl) in basis.withIndex()) {
        if (basisVrbl[0] == 'a') {
            hasA = true
            break
        }
    }
    // Если искусственные переменные существуют, подготавливаем М-строку для метода искусственного базиса
    if (hasA) {
        // Добавляем М-строку в таблицу
        table["M"] = mutableMapOf()
        // Заполняем нулями
        for ((vrbl, _) in f) {
            table["M"]!![vrbl] = 0.0
        }
        // Восстанавливаем коэффициенты строки
        for ((index, ineq) in lim.withIndex()) {
            // Берем искусственные переменные
            if (basis[index][0] == 'a') {
                for ((vrbl, c) in ineq.left) {
                    if (vrbl[0] != 'a') {
                        // Отнимаем коэф. неискусственных переменных
                        table["M"]!![vrbl] = table["M"]!![vrbl]!!.minus(c)
                    }
                }
                // Добавляем "Св."
                table["M"]!!["b"] = table["M"]!!["b"]!!.plus(ineq.b)
            }
        }
    }
    return table
}

/**
 * Получение начального базиса.
 */
fun getSimpleBasis(f: MutableMap<String, Double>, lim: Array<Inequality>): Triple<Map<String, Double>, Array<Inequality>, Array<String>> {
    var basis = arrayOf<String>()
    // Для каждого неравенства ищем базисную переменную
    for ((index, ineq) in lim.withIndex()) {
        // Флаг существования базисной переменной
        var isExist = false
        if (ineq.b >= 0) {
            for ((vrbl, c) in ineq.left) {
                // Если c == 1 и b >= 0, попробовать внести в базис
                if (c == 1.0) {
                    var available = true
                    for ((checkIndex, checkIneq) in lim.withIndex()) {
                        if (checkIndex != index && (checkIneq.left[vrbl] ?: 0.0) != 0.0 && (checkIneq.left[vrbl] ?: 0.0) != -0.0) {
                            available = false
                            break
                        }
                    }
                    // Если переменная доступна
                    if (available) {
                        // Ввести ее в базис
                        basis += vrbl
                        isExist = true
                        break
                    }
                }
            }
        }
        // Базисной переменной нет => вводим искусственную
        if (!isExist) {
            // Обозначим как ai (a - artifical)
            val a = "a${index + 1}"
            // Вводим переменную в функцию
            f[a] = 0.0
            // Вводим переменную в ограничения
            for ((i, elem) in lim.withIndex()) {
                if (i == index) {
                    elem.left[a] = 1.0
                } else {
                    elem.left[a] = 0.0
                }
            }
            basis += a
        }
    }
    return Triple(f, lim, basis)
}

/**
 * Приведение ЗЛП к каноническому виду.
 */
fun canonization(f: MutableMap<String, Double>, mode: String, lim: Array<Inequality>): Pair<MutableMap<String, Double>, Array<Inequality>> {
    // 0. Инвертируем ограничения с отрицательным свободным членом
    for ((i, ineq) in lim.withIndex()) {
        if (ineq.b < 0) {
            lim[i].b *= -1
            for ((vrbl, _) in ineq.left) {
                ineq.left[vrbl] = ineq.left[vrbl]!! * -1
            }
            lim[i].sign *= -1
        }
    }
    // 1. Переводим min-задачу к max
    if (mode == "min") {
        for ((vrbl, _) in f) {
            f[vrbl] = f[vrbl]!! * -1
        }
        f["b"] = f["b"]!! * -1
    }
    // 2. Приводим неравенства к равенствам
    // Кол-во новых переменных
    var newVarblNumber = 0
    for ((i, ineq) in lim.withIndex()) {
        // Возможная новая переменная
        val possibleNewX = "z${newVarblNumber + 1}"
        var possibleNewC = 0.0
        if (ineq.sign == -1) {
            possibleNewC = 1.0
        } else if (ineq.sign == 1) {
            possibleNewC = -1.0
        }
        // Если нужно добавить дообавить новую переменную
        if (possibleNewC != 0.0) {
            // Добавляем в ограничение
            ineq.left[possibleNewX] = possibleNewC
            // Добавляем в остальные ограничения
            for ((j, other) in lim.withIndex()) {
                if (i != j) {
                    other.left[possibleNewX] = 0.0
                }
            }
            // Добавляем 0*x в целевую функци.
            f[possibleNewX] = 0.0
            newVarblNumber++
            lim[i].sign = 0
        }
    }
    return f to lim
}

/**
 * Вывод симплекс-таблицы на экран.
 */
fun printSimplexTable(head: String, table: Map<String, Map<String, Double>>) {
    texPrint("\\begin{table}[h]\\begin{center}\\begin{tabular}{ |${(0..(table["F"]?.size ?: 0)).map { 'c' }.joinToString("|")}| }\n\\hline\n")
    var headers = arrayOf("b")
    texPrint(" & Св.")
    val variables = (table["F"] ?: error("F not found")).minus("b")
    for ((header, _) in variables) {
        texPrint(" & $header")
        headers += header
    }
    texPrint(" \\\\ \n\\hline\n")
    for ((rowKey, _) in table) {
        texPrint(rowKey)
        for ((_, colKey) in headers.withIndex()) {
            texPrint(" & ${"%.2f".format((table[rowKey] ?: error("rowKey not found"))[colKey]).replace(',', '.')}")
        }
        texPrint(" \\\\ \n\\hline\n")
    }
    texPrint("\\end{tabular}\\end{center}\\caption{$head}\\end{table}\n\n")
}