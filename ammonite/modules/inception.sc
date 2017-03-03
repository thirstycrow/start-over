object Inception {

  import com.twitter.finagle.Mysql

  case class Dest(host: String, port: Int, user: String, password: String) {
    def newClient(db: Option[String] = None) = {
      val c = Mysql.client.withCredentials(user, password)
      db.foreach(c.withDatabase(_))
      c.newService(s"$host:$port")
    }
  }
  def apply(host: String, port: Int) = new Inception(host, port)
}

class Inception(host: String, port: Int) {

  import com.twitter.finagle.Mysql

  private val client = Mysql.client.withCredentials("root", "").newService(s"$host:$port")

  def query(sql: String, dest: Inception.Dest, props: String = "") = {
    val propString = Seq(
      s"--host=${dest.host}",
      s"--port=${dest.port}",
      s"--user=${dest.user}",
      s"--password=${dest.password}",
      props
    ).mkString("/* ", "; ", " */")

    new MysqlExt(client).query(
      Seq(
        propString,
        "inception_magic_start;",
        sql.trim.replaceAll("(?<!;)$", ";"),
        "inception_magic_commit;"
      ).mkString("\n")
    )
  }

  def check(sql: String, dest: Inception.Dest) {
    query(sql, dest, "--enable-check;")
      .print(fields = Seq(
        "ID", "stage", "errlevel", "stagestatus",
        "errormessage", "SQL", "Affected_rows"))
  }

  def execute(sql: String, dest: Inception.Dest) = {
    query(sql, dest, "--enable-execute;")
      .print(fields = Seq(
        "ID", "stage", "errlevel", "stagestatus",
        "errormessage", "SQL", "Affected_rows",
        "sequence", "backup_dbname", "execute_time",
        "sqlsha1"))
  }

  def split(sql: String, dest: Inception.Dest) = {
    query(sql, dest, "--enable-split;")
  }
}
