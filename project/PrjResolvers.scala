import sbt._
import sbt.librarymanagement.MavenRepository

object PrjResolvers {
  lazy val all: Seq[MavenRepository] = Seq(
    "Maven repo1" at "https://repo1.maven.org/",
    "Maven repo2" at "https://mvnrepository.com/artifact"
  )
}
