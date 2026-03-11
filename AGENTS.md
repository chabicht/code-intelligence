## Build
- Use `./mvn.sh` wrapper to build instead of `mvn`.
- Use the `-q` flag to reduce the build output to a minimum (e.g. if you're running a build to verify something works) except you need more verbose information.
- Partial builds using `-pl` and `-am` don't work with Tycho.

## Tests
- Tycho tests are run with `./mvn.sh package tycho-surefire:test` or `./mvn.sh verify`.

### Test coverage
- Test coverage recording is done with the `-Pcoverage` profile: `./mvn.sh -Pcoverage clean verify`.
- Coverage reports are compiled to `target/site/jacoco-aggregate`:
  - `jacoco.csv` contains a coarse overview over the whole project.
  - Subfolders contain HTML files with more detail.
  - `path/to/[ClassName].html` contains an overview over the class, you also can convert this to ascii using `html2text path/to/[ClassName].html`.
  - `path/to/[ClassName].java.html` contains a line-by-line breakdown of the coverage. Caution: this is usually very detailed.
