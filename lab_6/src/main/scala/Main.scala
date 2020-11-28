import scala.io.Source
import scala.annotation.tailrec

object Main {
  
  def main(args: Array[String]): Unit = {
    // Чтение имени файла из списка аргументов командной строки
    val fileName = args.headOption.getOrElse {
      assert(false, "[filename] shouldn't be empty")
    }.asInstanceOf[String]

    // Чтение таблицы из файла
    val table = read(fileName)

    /**
     * Совершает шаг симплекс-метода, если решение не получено,
     * иначе возвращает оптимальный план.
     */
    @tailrec
    def iterate(table: SimplexTable): SimplexTable = {
      resolvingColumnIndex(table) match {
        case Some(ci) =>
          val ri = resolvingRowIndex(table, ci)
          val nextTable = updateSimplexTable(table, ri, ci)
          iterate(nextTable)

        case None => table
      }
    }

    // Печать форматированных входных данных
    printConditions(table)
    println()
    
    // Запуск поиска оптимального решения ЗЛП симплекс-методом
    val result = iterate(table)
    // Печать результата
    printResults(result)
  }

  /**
   * Стремление функции - к минимуму или к максимуму
   */
  enum Tendency {
    case Min, Max

    override def toString(): String = this match {
      case Min => "min"
      case Max => "max"
    }
  }

  /**
   * Получение объекта, идентифицирующего стремление, из строки.
   */
  object Tendency {
    def toLimit(v: String) = if (v == "min") Tendency.Min else Tendency.Max
  }

  /**
   * Симплекс-таблица и сопутствующие данные.
   */
  case class SimplexTable(
    // Направленность функции, для которой ищется оптимум
    tendency: Tendency, 
    // Ячейки симплекс-таблицы
    matrix: Array[Array[Double]],
    // Заголовки строк таблицы
    rh: Array[String],
    // Заголовки столбцов таблицы
    ch: Array[String]
  ) {
    /**
     * Осуществляет "глубокое" копирование объекта симплекс-таблицы.
     */
    override def clone(): SimplexTable = SimplexTable(
      tendency,
      matrix.map(_.clone),
      rh.clone,
      ch.clone
    )
  }

  /**
   * Читает исходные данные из файла `filename` и создаёт симплекс-таблицу по полученным параметрам.
   */
  def read(fileName: String): SimplexTable = {
    val content = Source.fromFile(fileName).getLines.toSeq

    // Чтение стремления функции
    val tendency = Tendency.toLimit(content.head)

    // Чтение ячеек таблицы и формированием матрицы значений
    val matrix = content.tail.map(_.split(" ").map(_.toDouble)).toArray
    // В строке, соответствующей целевой функции `F` инвертируем коэффициенты при свободных элемента
    // для простоты вычислений
    matrix(0) = matrix(0).head +: matrix(0).tail.map(-1*_)

    // формируем заголовки строк и столбцов
    val rh = "F" +: matrix.zipWithIndex.tail.map((_, i) => s"z$i")
    val ch = "св." +: matrix(0).zipWithIndex.tail.map((_, i) => s"x$i")

    SimplexTable(tendency, matrix, rh, ch)
  }

  /**
   * Поиск разешающего столбца.
   * Если столбец существует, возвращает индекс столбца,
   * иначе - возвращает значение `None`.
   */
  def resolvingColumnIndex(table: SimplexTable): Option[Int] = {
    // Функция, проверяющая, есть ли в столбце `i` положительный элемент.
    def existsPositiveInColumn(i: Int) = table.matrix.map(_(i)).exists(_ > 0)

    // Критерий, по которому выбирается разрешающий столбец
    val criteria: (Double => Boolean) = table.tendency match {
      // Если функция стремится к максимуму - первый отрицательный элемент
      case Tendency.Max => _ < 0
      // Если к минимуму - первый положительный элемент
      case Tendency.Min => _ > 0
    }

    // Выбор разрешающего столбца
    val resolvingColumn = table.matrix.head.zipWithIndex.tail
      .find((e: Double, i: Int) => criteria(e) && existsPositiveInColumn(i))

    resolvingColumn.map(_(1))
  }

  /**
   * Поиск разрешающей строки.

   * `columnIndex` - индекс разрешающего столбца.
   */
  def resolvingRowIndex(table: SimplexTable, columnIndex: Int): Int = {
    table.matrix.zipWithIndex.tail.map { (row, i) =>
      // Разделим все элементы в очередной строке на значение в пересечении разрешающего столбца и строки
      val result = row(0) / row(columnIndex)
      // Отбросим отрицательные значения
      (if (result < 0) Double.MaxValue else result, i)
    }.minBy(_(0)).apply(1) // Найдем индекс минимального положительного значения
  }

  /**
   * Шаг изменения симплекс-таблицы.
   * 
   * `ri` - индекс разрешающей строки.
   * `ci` - индекс разрешающего столбца.
   *
   * Возвращает новую симплекс-таблицу.
   */
  def updateSimplexTable(table: SimplexTable, ri: Int, ci: Int): SimplexTable = {
    // "Глубокое" копирование таблицы
    val nextTable = table.clone()

    // Меняем заголовки базисной и свободной переменной
    nextTable.rh(ri) = table.ch(ci)
    nextTable.ch(ci) = table.rh(ri)

    // Обновляем разрешающий элемент
    nextTable.matrix(ri)(ci) = 1 / table.matrix(ri)(ci)

    // Обновляем разрешающую строку
    for (j <- table.matrix.head.indices if j != ci) {
      nextTable.matrix(ri)(j) = table.matrix(ri)(j) * nextTable.matrix(ri)(ci)
    }

    // Обновляем разрешающий столбец
    for (i <- table.matrix.indices if i != ri) {
      nextTable.matrix(i)(ci) = -1 * table.matrix(i)(ci) * nextTable.matrix(ri)(ci)
    }

    // Обновляем остальные элементы по правилу прямоугольника
    for (i <- table.matrix.indices if i != ri) {
      for (j <- table.matrix(i).indices if j != ci) {
        nextTable.matrix(i)(j) = table.matrix(i)(j) + table.matrix(ri)(j) * nextTable.matrix(i)(ci)
      }
    }

    nextTable
  }

  /**
   * Печать начальных условий
   */
  def printConditions(table: SimplexTable): Unit = {
    //Формирование данных целевой функции
    val function = {
      val row = table.matrix.head

      val variables = row.tail.map(-_.round).zip(table.ch.tail).map((x, y) => s"$x$y") 

      val body = variables.mkString(" + ")
      s"F = $body ⇒ ${table.tendency}"
    }

    // Формирование данных ограничений
    val boundaries = table.matrix.tail.map { row =>
      val variables = row.tail.map(_.round).zip(table.ch.tail).map((x, y) => s"$x$y")
      val freeTerm = row.head.round

      val body = variables.mkString(" + ")
      s"$body ≤ $freeTerm"
    }

    println(s"Функция:\n$function")
    println("Ограничения:")
    boundaries.foreach(println)
  }

  /**
   * Печать результатов в соответствии с указаниями к работе.
   */
  def printResults(table: SimplexTable): Unit = {
    val x = table.matrix.head.tail.map(_ => 0.0)
    val z = table.matrix.tail.map(_ => 0.0)

    for ((elem, i) <- table.rh.zipWithIndex if elem.size > 1) {
      val (letter, index) = {
         val e = elem.split("")
         (e(0), e(1).toInt)
      }

      letter match {
        case "z" =>
          z(index - 1) = table.matrix(i)(0)
        case "x" =>
          x(index - 1) = table.matrix(i)(0)
        case _ =>
      }
    }
    println(s"${table.tendency} F = ${table.matrix.head.head.round}, X = (${x.map(_.round).mkString(",")}), Z = (${z.map(_.round).mkString(",")})")
  }

}
