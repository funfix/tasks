import sbt.*
import sbt.Keys.*

object Boilerplate {
  /**
   * For working with Scala version-specific source files, allowing us to
   * use 2.13 or 3.x specific APIs.
   */
  lazy val crossVersionSharedSources: Seq[Setting[?]] = {
    def scalaPartV = Def setting (CrossVersion partialVersion scalaVersion.value)
    Seq(Compile, Test).map { sc =>
      (sc / unmanagedSourceDirectories) ++= {
        (sc / unmanagedSourceDirectories).value
          .filterNot(_.getPath.matches("^.*\\d+$"))
          .flatMap { dir =>
            Seq(
              scalaPartV.value match {
                case Some((2, _)) => Seq(new File(dir.getPath + "-2"))
                case Some((3, _)) => Seq(new File(dir.getPath + "-3"))
                case _ => Nil
              },
            ).flatten
          }
      }
    }
  }
}
