# Komenti

Komenti is a tool for semantic query and annotation of text using ontologies. 

The first time you run it, it may seem to hang for a long time. This is because it is downloading the libraries.

## Query

### Semantic

Get labels for a semantic subclass query.

```bash
./Komenti query -q "part of some 'apoptotic process'" -o GO --out labels.txt
```

### Class list

```bash
./Komenti query -c toxicity,asbestos -o ENM --out labels.txt
```

### Parameters

* The labels can be extended by the power of lemmatisation, by passing --lemmatise
* Synonyms can be expanded used name and semantic matching over AberOWL by passing --expand-synonyms

## Get Abstracts

Get abstracts from EBI PMCSearch matching any class label.

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
