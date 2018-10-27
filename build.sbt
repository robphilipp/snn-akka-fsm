resolvers += Resolver.bintrayRepo("cakesolutions", "maven")

// language features
scalacOptions ++= Seq(
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps"
)

//
// common settings
//
lazy val commonSettings = Seq(
  version := "0.0.2-snapshot",
  scalaVersion := "2.12.6"
)

//
// app configuration
//
lazy val runClass = "sample.neuron.MonoStableIntegrator"
lazy val app = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "fsm-neuron",

    //
    // assembly
    //
    // set the main class in the JAR (for the executable JAR) in the assembly
    mainClass in assembly := Some(runClass),

    // turn off tests for the assembly task
    test in assembly := {},

    // the name of the assembled JAR
    assemblyJarName in assembly := name.value + ".jar",

    // set the main class for 'sbt run'
    mainClass in(Compile, run) := Some(runClass),
    // set the main class for packaging the main jar
    mainClass in(Compile, packageBin) := Some(runClass),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.16"
    )
  )
