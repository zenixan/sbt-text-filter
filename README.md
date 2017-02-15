**sbt-text-filter** is a SBT plugin which substitutes variables in resource files
by values from the environment/system or project properties.

## Requirements

* sbt 0.13.5 or later

## Setup

Add the following plugin configuration (e.g. file `project/plugins.sbt`):
```
addSbtPlugin("eu.org.fuzzy" % "sbt-text-filter" % "0.0.1")
```

## Properties

The plugin provides the following predefined properties:

- Environment variables can be referenced using the `env.*` prefix, e.g. `${env.HOME}`.
- Java system properties can be referenced using the `sys.*` prefix, e.g. `${sys.java.class.path}`.
- Project properties can be referenced without any prefixes, e.g. `${organization}`.

## Settings

The plugin injects a main task **textFilter** into the system task **products** 
and provides the following settings:

- **textFilterExtensions** — a list of file's extensions that will be filtered, 
  e.g.: `.xml`, `.properties`
- **textFilterPattern** — a regular expression to replace variables in the resource file.
  An expression must contains one capturing group with a name of variable, 
  e.g. `\\$\{(.+?)\}`
- **textFilterEscape** — a printf-style format string to escape an variable.
  An expression must contains one format specifier **%s** which will be 
  replaced by a pattern of variable, e.g. `\\?%s`

## License

This project is licensed under the MIT License.

## See also

[Maven's resource filtering](http://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html)
