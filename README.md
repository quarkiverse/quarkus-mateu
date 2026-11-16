# Quarkus Mateu

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.mateu/quarkus-mateu?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.mateu/quarkus-mateu-parent)

[Mateu](https://mateu.io) is a model-driven UI system: declare a screen once as an annotated Java class and
Mateu renders forms, CRUDs, wizards, dashboards and full app shells from it, with no frontend code.
Mateu is Apache-2.0 and published on Maven Central as `io.mateu:*`
([source](https://github.com/miguelperezcolom/mateu)).

This extension brings Mateu to Quarkus: the Mateu runtime for Quarkus, Quarkus REST with Jackson and the
default web renderer, plus build steps that keep the beans Mateu injects into its view models and fail the
build when the Mateu annotation processor did not run.

```xml
<dependency>
    <groupId>io.quarkiverse.mateu</groupId>
    <artifactId>quarkus-mateu</artifactId>
    <version>${quarkus-mateu.version}</version>
</dependency>
```

plus `io.mateu:mateu-annotation-processor-quarkus` in the compiler's `annotationProcessorPaths`.
See the [documentation](https://docs.quarkiverse.io/quarkus-mateu/dev/).

## Documentation

The documentation for this extension should be maintained as part of this repository and it is stored in the `docs/` directory.

The layout should follow the [Antora's Standard File and Directory Set](https://docs.antora.org/antora/2.3/standard-directories/).

Once the docs are ready to be published, please open a PR including this repository in the [Quarkiverse Docs Antora playbook](https://github.com/quarkiverse/quarkiverse-docs/blob/main/antora-playbook.yml#L7). See an example [here](https://github.com/quarkiverse/quarkiverse-docs/pull/1)

Your documentation will then be published to the <https://docs.quarkiverse.io/> website.
