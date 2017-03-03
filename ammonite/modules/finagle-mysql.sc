interp.load.ivy("com.twitter" %% "finagle-mysql" % Libraries.V.finagle)

@

import com.twitter.conversions.time._
import com.twitter.finagle

implicit class MysqlResultExt(val result: finagle.mysql.Result) extends AnyVal {

  import finagle.mysql._
  // import com.twitter.util._
  import scala.util.control.NoStackTrace

  def extract[T: Manifest](field: String): Seq[Option[T]] = {
    result match {
      case rs: ResultSet =>
        rs.fields.find(_.name == field) match {
          case Some(field) =>
            result match {
              case rs: ResultSet =>
                rs.fields.find(_.name == field)
                rs.rows.map(getValue[T](_, field))
              case _ =>
                Nil
            }
          case None =>
            throw new IllegalArgumentException("No such field: " + field)
                with NoStackTrace
        }
      case _ =>
        throw new IllegalStateException(result.toString) with NoStackTrace
    }
  }

  def print(offset: Int = 0, limit: Int = 10, maxColumnWidth: Int = 80, fields: Seq[String] = Nil) {
    result match {
      case rs: ResultSet if offset < 0 || offset >= rs.rows.size =>
        println(s"Offset must be non-negative and less than the total number of rows (${rs.rows.size}).")
      case rs: ResultSet =>
        val _fields = if (fields.isEmpty) {
          rs.fields
        } else {
          fields.flatMap(name => rs.fields.find(_.name == name))
        }
        val columnWidth = _fields.map(field => (field, maxWidth(rs.rows, field, maxColumnWidth))).toMap
        printSeparator(_fields, columnWidth)
        printHead(_fields, columnWidth)
        printSeparator(_fields, columnWidth)
        rs.rows.foreach { row =>
          printRow(_fields, f => toString(row, f), columnWidth)
          printSeparator(_fields, columnWidth)
        }
      case _ =>
        println(result)
    }
  }

  def printRow(fields: Seq[Field], row: Field => String, columnWidth: Map[Field, Int]) = {
    val columns = fields.map { f =>
      (f, row(f).split("\n")
        .iterator
        .flatMap(_.grouped(columnWidth(f)))
        .toVector)
    }.toMap
    val nlines = columns.values.map(_.size).max
    val padded = columns.mapValues(_.padTo(nlines, ""))

    for (i <- 0 until nlines)
      println(
        fields.map { field => formatColumn(padded(field)(i), columnWidth(field)) }
          .mkString("|")
      )
  }

  def printHead(fields: Seq[Field], maxWidth: Map[Field, Int]) = {
    printRow(fields, _.name, maxWidth)
  }

  def printSeparator(fields: Seq[Field], columnWidth: Map[Field, Int]) {
    println(fields.map(columnWidth(_)).map("-" * _).mkString("+"))
  }

  def formatColumn(value: Any, width: Int, alignRight: Boolean = false) = {
    if (alignRight) value.toString.reverse.padTo(width, ' ').reverse
    else value.toString.padTo(width, ' ')
  }

  def maxWidth(rows: Seq[Row], field: Field, max: Int = 80) = {
    rows.flatMap(row => toString(row, field).split("\n"))
      .map(_.size)
      .padTo(1, 0)
      .max
      .max(field.name.size)
      .min(max)
  }

  def getValue[T](row: Row, field: Field) = {
    row(field.name).collect {
      case prdt: Product if prdt.productArity > 0 =>
        prdt.productElement(0).asInstanceOf[T]
    }
  }

  def toString(row: Row, field: Field, default: String = "") = {
    getValue[Any](row, field).map(_.toString).getOrElse(default)
  }
}

implicit class MysqlExt(
  val service: finagle.Service[finagle.mysql.Request, finagle.mysql.Result]) extends AnyVal {

  import com.twitter.util._
  import finagle.mysql._

  def query(sql: String, timeout: Duration = 1.second) = {
    Await.result(service(QueryRequest(sql)), timeout)
  }
  def printResult(sql: String, timeout: Duration = 1.second) = {
    query(sql, timeout).print()
  }
}
