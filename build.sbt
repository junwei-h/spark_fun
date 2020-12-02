lazy val root = (project in file(".")).
	settings(
    name := "mltest",
    version := "1.0",
    scalaVersion := "2.12.12",
    organization := "com",

    mainClass in (Compile, packageBin) := Some("com.mltest.Main"),
		assemblyJarName in assembly := "mltest.jar",
		libraryDependencies ++= Seq(
			"org.apache.hadoop" % "hadoop-common" % "2.7.1" % "provided" excludeAll ExclusionRule(organization = "javax.servlet"),
			"org.apache.spark" %% "spark-core" % "2.4.7" % "provided",
			"org.apache.spark" %% "spark-mllib" % "2.4.7" % "provided",
			"org.apache.spark" %% "spark-sql" % "2.4.7" % "provided",

			// testing
			"org.scalatest" %% "scalatest" % "3.0.5" % "test",

			// logging
			"org.apache.logging.log4j" % "log4j-api" % "2.4.1",
			"org.apache.logging.log4j" % "log4j-core" % "2.4.1"
		),

    assemblyExcludedJars in assembly := {
		val cp = (fullClasspath in assembly).value
		val excludes = Seq("scala-library-2.12.12.jar")
		cp filter {a=> excludes contains a.data.getName}
	}
)
