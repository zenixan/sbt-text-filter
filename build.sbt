name := "Text Filter"
organization := "org.eu.fuzzy"
description := "SBT plugin to replace variables in resource files."
version := "0.0.1"

//
// License details
//
licenses := Seq(
  ("MIT License", url("https://spdx.org/licenses/MIT.html"))
)

developers := List(
  Developer("zenixan", "Yevhen Vatulin", "zenixan@gmail.com", url("https://fuzzy.eu.org"))
)

//
// Other project settings
//
normalizedName := "sbt-text-filter"
homepage := Some(url("https://github.com/zenixan/sbt-text-filter"))
startYear := Some(2017)

//
// Build/Publish settings
//
sbtPlugin := true
publishMavenStyle := false