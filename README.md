# Komenti

[![Build Status](https://travis-ci.com/reality/komenti.svg?branch=master)](https://travis-ci.com/reality/komenti)
![LINE](https://img.shields.io/badge/line--coverage-49%25-orange.svg)
![BRANCH](https://img.shields.io/badge/branch--coverage-21%25-red.svg)
![COMPLEXITY](https://img.shields.io/badge/complexity-5.50-brightgreen.svg)

Komenti is a tool for semantic query, annotation, and analysis of text using ontologies. 

It enables querying multiple ontologies with complex class descriptions using AberOWL. These can be used to build a vocabulary for text annotation, including new methods for synonym and label expansion. Annotation is performed using Stanford CoreNLP, and include novel methods for the detection and disambiguation of concept negation and uncertainty. Annotations of text corpora can be used for analysis, within or without Komenti. These components are in development, but currently include summarisation of the co-ocurrence of groups of concepts across text, and use of annotations to suggest description logic axioms for classes. These more complex uses can be described by series of parameters to be passed to the tool in the form of a serialised 'roster,' defining a natural language processing pipeline.

We are working on papers discussing the novel components. I will post them here:

* [A fast, accurate, and generalisable heuristic-based negation detection algorithm for clinical text](https://www.biorxiv.org/content/10.1101/2020.07.03.187054v1)
  * When you use *komenti annotate*, annotations will automatically be marked for negation and uncertainty using the algorithm described.
* [Improved characterisation of clinical text through ontology-based vocabulary expansion](https://www.biorxiv.org/content/10.1101/2020.07.10.197541v1)
  * You can use this feature by passing the *--expand-synonyms* flag to the query command.

## Installation

You can find the latest stable-ish release here: https://github.com/reality/komenti/releases/tag/0.1.1

You can add the bin/ directory to your PATH, to be able to use it easily from anywhere. It should also work on Windows, but I haven't tested that.

## Query

### Semantic

Get classes and labels satisfying a complex class description using Manchester OWL Syntax.

```bash
komenti query -q "'part of' some 'apoptotic process'" -o GO --out labels.txt
```

### Class list

```bash
komenti query -c toxicity,asbestos -o ENM --out labels.txt
```

### All classes

You can get all classes in an ontology by running a subclass query on owl#Thing:

```bash
komenti query -q "<http://www.w3.org/2002/07/owl#Thing>" --ontology HP
```

### Parameters

* The labels can be extended by the power of lemmatisation, by passing --lemmatise
* Synonyms can be expanded used name and semantic matching over AberOWL by passing --expand-synonyms
* --query-type allows you to run either subclass, equivalent, subeq, or superclass queries (the default is subeq)

## Get Abstracts

Get abstracts from EBI PMCSearch matching any class label (using, as input, the output of the 'query' sub-command).

```bash
komenti get_abstracts -l labels.txt --out abstracts/
```

### Parameters

Queries can be grouped by the query used to receive them (the third column in the labels file):

```bash
komenti get_abstracts -l labels.txt --group-by-query --out abstracts/
```

Queries can be conjunctivised (articles must match at least one of every query group):

```bash
komenti get_abstracts -l labels.txt --group-by-query --conjunction --out abstracts/
```

## Annotate

Annotate text using labels using Stanford CoreNLP.

```bash
komenti annotate -l labels.txt -t text/ --out annotations.txt
```

### Parameters

* -t/--text can be a file or a directory. The files can be text files, or PDF files (whose text will automatically be extracted)
* --family-modifier will add an additional modifier tag for each sentence, indicating whether the sentence mentions a family member (that is, it includes one of the words in the family word list).
* --per-line Annotate each line of each file seperately. This is useful for field-based data, which doesn't have clear line boundaries.
* --disable-modifiers Don't evaluate the annotations for modifiers. These can be added to an annotation file later, using the add_modifiers command.
* --file-list Instead of using --text, you can pass a text file that contains a list of files and directories to annotate, one on each line.

## Summarise Entity Pairs

Summarise the co-occurence of two groups of concepts that have been annotated in a text corpus.

```bash
komenti summarise_entity_pairs -l labels.txt -a annotation.txt -c asbestos,toxicity
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
komenti diagnose -a annotations.txt --out diagnoses.txt
```

The output will be tabular, describing the status for each 'target.' The only
targets currently implemented are 'self' and 'family'. The data describes 
triples, but is formatted in this way to be
easier to read (for example, including both the IRI and first label of the
concept, and not creating a new predicate to assign the target to an
assertion). If the concept is not mentioned, it will not be included.

```
id	concept iri	concept label	target	status
0001.txt	I50	hypertension	self	negated
0001.txt	I50	hypertension	family	affirmed
```

### Parameters

* --by-group Instead of doing a diagnosis per IRI, a diagnosis per-group will be done. The 'label' column of the output, in this case, will be null.

## Generating and Running Rosters

Rosters are files that determine the parameters for series of commands in
Komenti. Using them, we can create a specification for a pipeline that runs many
Komenti commands, using any outputs in subsequent commands. Here are two
examples, the first a general annotation pipeline, the second specifically for
examining concept co-occurence:

```bash
komenti gen_roster --with-abstracts-download --query "toxicity" --ontology ENM --out roster.json
```

```bash
komenti gen_roster --mine-relationship -c asbestos,toxicity -o ENM --out relationship_roster.json
```

```bash
komenti gen_roster --suggest-axiom --with-metadata-download -c 'nanoparticle' --ontology ENM --entity nanoparticle,nanocage,nanocell,nanosphere,nanohorn,nanorod,nanotube,nanoshell,'quantum dot' --default-entity nanoparticle --quality 'chemical substance','environmental material' --default-relation has_component_part --out enm_roster.json
```

The rosters can be executed with the following command:

```bash
komenti auto -r roster.json
```
