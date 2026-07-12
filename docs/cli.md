# flai CLI

`flai-cli` is a non-interactive command-line runner for flai pipelines. It executes a
`*.flai.yaml` pipeline file with provided inputs and exits with a machine-readable status,
making it suitable for CI.

## Get it

Download `flai-cli-<version>.jar` from the project's GitHub releases page, or build from source:

```bash
./gradlew :cli:fatJar
# -> cli/build/libs/flai-cli-<version>.jar
```

Requires Java 21+.

## Usage

```
java -jar flai-cli.jar run <pipeline-file> [options]

Options:
  --input key=value     Pipeline input; repeatable, wins over --inputs-json
  --inputs-json <file>  JSON object file with pipeline inputs
  --workdir <dir>       Working directory for bash/file gates (default: pipeline project root)
  --format text|json    Final outputs format on stdout (default: text)
  --quiet               Suppress execution event log on stderr
```

Examples:

```bash
java -jar flai-cli.jar run .flai/review.flai.yaml --input file=src/Main.kt
java -jar flai-cli.jar run .flai/review.flai.yaml --inputs-json inputs.json --format json --quiet
```

### Inputs

- `--input key=value` can be repeated. Values are strings.
- `--inputs-json file.json` must contain a single JSON object; its entries seed the pipeline
  context (numbers/booleans/objects preserved).
- On key conflict, explicit `--input` wins over `--inputs-json`.

### Output streams

- **stdout** — final pipeline outputs only. `text` prints `key = value` lines; `json` prints
  one JSON object. Safe to pipe.
- **stderr** — execution event log (gate start/completion, durations) and errors.
  Suppress events with `--quiet`.

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | Pipeline completed successfully |
| 1 | Pipeline load/validation/execution failure |
| 2 | Usage error (bad arguments) |

## Credentials

The IDE plugin stores LLM API keys in IntelliJ PasswordSafe. The CLI has no PasswordSafe, so a
gate's `endpoint.credentialId` is resolved from an environment variable instead:

```
FLAI_CREDENTIAL_<ID>
```

where `<ID>` is the `credentialId` uppercased with every non-alphanumeric character replaced by `_`.

Example: `credentialId: openai-key` → `FLAI_CREDENTIAL_OPENAI_KEY`.

`endpoint.apiKeyVar` (reading the key from a pipeline context variable) works the same as in the IDE.

## Working directory

Bash, read-file, write-file gates and relative tool paths resolve against the working directory:

1. `--workdir` if given;
2. otherwise the pipeline file's parent directory — or, when the file lives in a `.flai/`
   directory, that directory's parent (the project root).

## Tools

Available tool-gate tools: `ide.readFile`, `ide.runCommand`. The PSI symbol search tool is
IDE-only; a pipeline referencing it fails in the CLI with an unknown-tool error.

## GitHub Actions example

```yaml
jobs:
  run-pipeline:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 21
      - name: Download flai-cli
        run: gh release download --repo CrazyApple888/flai --pattern 'flai-cli-*.jar' --output flai-cli.jar
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      - name: Run pipeline
        env:
          FLAI_CREDENTIAL_OPENAI_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: java -jar flai-cli.jar run .flai/review.flai.yaml --input file=src/Main.kt --format json
```
