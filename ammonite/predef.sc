import Resolvers._

val aliyun = Resolver.Http(
  "maven.aliyun.com",
  "http://maven.aliyun.com/nexus/content/groups/public/",
  MavenPattern,
  true
)

interp.resolvers() = List(aliyun)

@

interp.load.ivy("com.lihaoyi" %% "ammonite-shell" % ammonite.Constants.version)

@

val shellSession = ammonite.shell.ShellSession()
import shellSession._
import ammonite.shell.PPrints._
import ammonite.ops._
import ammonite.shell._
ammonite.shell.Configure(repl, wd)

val _dot_ammonite = home/".ammonite"

object modules {

  lazy val common = Module("common")
  lazy val finagleMysql = Module("finagle-mysql", Seq(common))
  lazy val http = Module("http")
  lazy val inception = Module("inception", Seq(finagleMysql))

  def loaded = Module.loaded

  private object Module {

    val modules = _dot_ammonite/'modules
    var loaded = Set[String]()
  }

  case class Module(
    name: String,
    dependsOn: Seq[Module] = Nil
  ) {

    def load() {
      if (Module.loaded.contains(name)) {
        println(s"$name is already loaded.")
      } else {
        dependsOn.foreach(_.load())
        println(s"Loading $name ... ")
        interp.load.exec(Module.modules/s"$name.sc")
        Module.loaded += name
        println("OK")
      }
    }

    def help() {
      read.lines(Module.modules/s"$name.usage")
        .foreach(println)
    }
  }
}

object Libraries {

  object V {
    def util = "6.40.0"
    def finagle = "6.41.0"
    def scalajHttp = "2.3.0"
  }
}
