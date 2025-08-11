# MDRMine-bio-sources

Repo to host [MDRMine](https://github.com/ecrin-github/mdrmine) sources

## Usage

- `update_jars_local.sh` needs to be run if using Docker, to generate sources JARs and move them to the `mdrmine` folder for Docker to use
- Otherwise, `./gradlew install` to generate sources JARs

## Data sources
The data sources and their corresponding data files are defined in the `project.xml` file.

See the [sources wiki](https://github.com/ecrin-github/mdrmine-bio-sources/wiki) for more details regarding the parsing and merging of the various sources.

## Versioning
TODO