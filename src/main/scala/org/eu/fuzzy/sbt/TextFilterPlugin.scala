package org.eu.fuzzy.sbt

import sbt.Keys._
import sbt._
import Def.Initialize

import scala.util.matching.Regex

/**
 * This plugin substitutes variables in resource files by values from the environment/system or project properties.
 *
 * === Settings ===
 * The plugin injects a main task '''textFilter''' into the system task '''products''' and provides the following settings:
 *  - '''textFilterExtensions''' — a list of file's extensions that will be filtered, e.g.: `.xml`, `.properties`
 *  - '''textFilterPattern''' — a regular expression to replace variables in the resource file.
 *  An expression must contains one capturing group with a name of variable, e.g. `\\$\{(.+?)\}`
 *  - '''textFilterEscape''' — a printf-style format string to escape an variable.
 *  An expression must contains one format specifier '''%s''' which will be replaced by a pattern of variable,
 *  e.g. `\\?%s`
 *
 * === Properties ===
 * The plugin provides the following predefined properties:
 *  - Environment variables can be referenced using the '''env.*''' prefix, e.g. `\${env.HOME}`.
 *  - Java system properties can be referenced using the '''sys.*''' prefix, e.g. `\${sys.java.class.path}`.
 *  - Project properties can be references without any prefixes, e.g. `\${organization}`.
 *
 * @see [[http://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html Maven's resource filtering]]
 *
 * @note SPDX-License-Identifier: MIT
 */
object TextFilterPlugin extends AutoPlugin {

  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements

  /**
   * Provides a main task of plugin with settings.
   */
  object autoImport {
    val textFilterExtensions = settingKey[Seq[String]]("A list of file's extensions which will be filtered.")
    val textFilterPattern = settingKey[String]("A regular expression to replace variables in the resource file.")
    val textFilterEscape = settingKey[String]("A printf-style format string to escape an variable.")
    val textFilter = taskKey[Seq[(File, File)]]("Replace variables in resource files.")
  }

  import autoImport._

  /**
   * A set of default settings of plugin.
   */
  lazy val baseSettings: Seq[Def.Setting[_]] = Seq(
    textFilter := task(textFilter).value,
    textFilterExtensions in textFilter := List(".xml", ".properties"),
    textFilterPattern in textFilter := """\$\{(.+?)\}""",
    textFilterEscape in textFilter := """\\?%s"""
  )

  override val projectSettings = inConfig(Compile)(baseSettings) ++ inConfig(Test)(baseSettings) ++
    Seq(products in Compile := (products in Compile).dependsOn(textFilter in Compile).value) ++
    Seq(products in Test := (products in Test).dependsOn(textFilter in Test).value)

  /**
   * Returns a task that replaces variables in resources files.
   */
  def task(key: TaskKey[Seq[(File, File)]]): Initialize[Task[Seq[(File, File)]]] = Def.task {
    implicit val logger = streams.value.log

    val resourceFiles = copyResources.value.filter {
      case (source, _) => hasFilteredExtension(source, (textFilterExtensions in key).value)
    }

    if (resourceFiles.nonEmpty) {
      val pattern = new Regex(
        (textFilterEscape in key).value.format("(" + (textFilterPattern in key).value) + ")"
      )
      val properties = getAllProperties(buildStructure.value.data.data)

      printBanner(resourceFiles)
      resourceFiles.foreach { case (source, destination) =>
        replaceVariables(source, destination, pattern, properties)
      }
    }

    resourceFiles
  }

  /**
   * Prints a banner of main task.
   */
  private def printBanner(files: Seq[(File, File)])(implicit logger: Logger): Unit = {
    val count = plural(files.length, "resource file")
    val commonTargetFolder = files
      .map { case (_, destination) => destination.toString }
      .reduceLeftOption { (path1, path2) => findCommonPath(path1, path2) }

    if (commonTargetFolder.isDefined)
      logger.info(s"Filtering ${count} to ${commonTargetFolder.get}")
    else
      logger.info(s"Filtering ${count}")
  }

  /**
   * Replaces variables in the specified resource file.
   */
  private def replaceVariables(source: File, destination: File, pattern: Regex, properties: Map[String, String])
                              (implicit logger: Logger): Unit = {
    import sys.error
    import Regex.quoteReplacement

    val replacer = (m: Regex.Match) => {
      val variable = m.group(2)
      val matched = m.matched
      if (matched.length == m.group(1).length)
        quoteReplacement(properties.getOrElse(variable, error("Unknown variable: " + variable)))
      else
        quoteReplacement(matched.substring(1))
    }

    logger.debug("Filtering " + source + " to " + destination)
    val content = IO.read(source)
    val filteredContent = pattern.replaceAllIn(content, replacer)
    IO.write(destination, filteredContent)
  }

  /**
   * Returns a set of project/environments properties which will be used to substitute variables.
   */
  private def getAllProperties(settings: Map[Scope, AttributeMap]): Map[String, String] = {
    val envProperties = sys.env.map { case (key, value) => ("env." + key, value) }
    val sysProperties = sys.props.map { case (key, value) => ("sys." + key, value) }
    val projectProperties = getProjectProperties(settings)
    envProperties ++ sysProperties ++ projectProperties
  }

  /**
   * Returns a set of scalar properties where project properties from the Global scope will have a lower priority.
   */
  private def getProjectProperties(settings: Map[Scope, AttributeMap]): Map[String, String] = settings
    .filter { case (scope, _) => scope.config == Global && scope.task == Global }
    .foldLeft(Map.empty[String, String]) { (unique, current) =>
      current match {
        case (scope, settings) => unique ++ settings.entries
          .filter { setting => isScalar(setting.value) }
          .filter { setting => !unique.contains(setting.key.label) || scope.project != Global }
          .map { setting => (setting.key.label, setting.value.toString) }
      }
    }

  /**
   * Returns a plural or singular form of word which depends on specified number.
   */
  private def plural(number: Integer, singular: String, plural: String = "s"): String =
    if (number == 1)
      number + " " + singular
    else
      number + " " + singular + plural

  /**
   * Checks whether the specified value is a scalar value, i.e. string, number, boolean or character.
   */
  private def isScalar(value: Any): Boolean =
    value.isInstanceOf[Number] || value.isInstanceOf[String] ||
      value.isInstanceOf[Boolean] || value.isInstanceOf[Char]

  /**
   * Checks whether the file has extension from the specified list.
   */
  private def hasFilteredExtension(file: File, extensions: Seq[String]): Boolean = {
    val fileName = file.toString.toLowerCase
    extensions.exists { fileName.endsWith(_) }
  }

  /**
   * Returns a common directory path for the specified file names.
   */
  private def findCommonPath(filename1: String, filename2: String): String = {
    import scala.math.min
    import java.io.File

    val commonSubstring = for {
      i <- (min(filename1.length(), filename2.length()) to 0 by -1).view
      if filename2.startsWith(filename1.substring(0, i))
      if filename2.charAt(i) == File.separatorChar
    } yield filename1.substring(0, i)

    commonSubstring.headOption.getOrElse("")
  }

}
