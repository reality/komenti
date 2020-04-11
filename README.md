# Komenti

Komenti is a tool for semantic query, annotation, and analysis of text using ontologies. 

It enables querying multiple ontologies with complex class descriptions using AberOWL. These can be used to build a vocabulary for text annotation, including new methods for synonym and label expansion. Annotation is performed using Stanford CoreNLP, and include novel methods for the detection and disambiguation of concept negation and uncertainty. Annotations of text corpora can be used for analysis, within or without Komenti. These components are in development, but currently include summarisation of the co-ocurrence of groups of concepts across text, and use of annotations to suggest description logic axioms for classes. These more complex uses can be described by series of parameters to be passed to the tool in the form of a serialised 'roster,' defining a natural language processing pipeline.

The following software is required: 
* Groovy
  * http://groovy-lang.org/
  * Commands tested on Groovy Version: 2.5.8 JVM: 1.8.0_232 Vendor: Oracle Corporation OS: Linux

All other libraries will be automatically installed. The first time you run it, it may seem to hang for a long time. This is because it is downloading the libraries.

Commands should be able to run in any command line interface, including on the Windows terminal emulator. However, it is tested in a Bash console on Linux.

## Query

### Semantic

Get classes and labels satisfying a complex class description using Manchester OWL Syntax.

```bash
./Komenti query -q "'part of' some 'apoptotic process'" -o GO --out labels.txt
```

### Class list

```bash
./Komenti query -c toxicity,asbestos -o ENM --out labels.txt
```

### Parameters

* The labels can be extended by the power of lemmatisation, by passing --lemmatise
* Synonyms can be expanded used name and semantic matching over AberOWL by passing --expand-synonyms
* --query-type allows you to run either subclass, equivalent, subeq, or superclass queries (the default is subeq)

## Get Abstracts

Get abstracts from EBI PMCSearch matching any class label (using, as input, the output of the 'query' sub-command).

```bash
./Komenti get_abstracts -l labels.txt --out abstracts/
```

### Parameters

Queries can be grouped by the query used to receive them (the third column in the labels file):

```bash
./Komenti get_abstracts -l labels.txt --group-by-query --out abstracts/
```

Queries can be conjunctivised (articles must match at least one of every query group):

```bash
./Komenti get_abstracts -l labels.txt --group-by-query --conjunction --out abstracts/
```

## Annotate

Annotate text using labels using Stanford CoreNLP.

```bash
./Komenti annotate -l labels.txt -t text/ --out annotations.txt
```

### Parameters

* -t/--text can be a file or a directory. The files can be text files, or PDF files (whose text will automatically be extracted)

## Summarise Entity Pairs

Summarise the co-occurence of two groups of concepts that have been annotated in a text corpus.

```bash
./Komenti summarise_entity_pairs -l labels.txt -a annotation.txt -c asbestos,toxicity
```

## Diagnose Documents

This analysis tool takes an annotation file as input. It assumes that each
annotation file describes one entity, and then for each distinct concept
annotated in that file, it decides its overall status with respect to that
concept. For example, is hypertension, overall, negated in this document? Or
uncertain? If there are separate family flags, then these will have their own
separate decision, e.g. a patient may have family history of HCM, but not HCM
themselves.

```bash
./Komenti diagnose_documents -l labels.txt -a annotations.txt --out diagnoses.txt
```

## Generating and Running Rosters

Rosters are files that determine the parameters for series of commands in
Komenti. Using them, we can create a specification for a pipeline that runs many
Komenti commands, using any outputs in subsequent commands. Here are two
examples, the first a general annotation pipeline, the second specifically for
examining concept co-occurence:

```bash
./Komenti gen_roster --with-abstracts-download --query "toxicity" --ontology ENM --out roster.json
```

```bash
./Komenti gen_roster --mine-relationship -c asbestos,toxicity -o ENM --out relationship_roster.json
```

```bash
./Komenti gen_roster --suggest-axiom --with-metadata-download -c 'nanoparticle' --ontology ENM --entity nanoparticle,nanocage,nanocell,nanosphere,nanohorn,nanorod,nanotube,nanoshell,'quantum dot' --default-entity nanoparticle --quality 'chemical substance','environmental material' --default-relation has_component_part --out enm_roster.json
```

The rosters can be executed with the following command:

```bash
./Komenti auto -r roster.json
```
