/*
 * This Groovy source file was generated by the Gradle 'init' task.
 */
package komenti

class App {
 static void main(String[] args) {
    def cliBuilder = new CliBuilder(
      usage: 'komenti <command> [<options>]',
      header: 'Options:'
    )

    cliBuilder.with {
      h longOpt: 'help', 'Print this help text and exit.'

      // query options
      o longOpt: 'ontology', 'Which ontology to query.', args: 1
      q longOpt: 'query', 'A Manchester OWL Syntax query, the result of which will be the set of NER labels.', args: '+'
      c longOpt: 'class-list', 'A list of classes to query. You can also pass a file, one label per line.', args: 1
      _ longOpt: 'object-properties', 'Query object properties (do not pass --query/-q or -c/--classes)', type: Boolean
      _ longOpt: 'query-type', 'Type of query to run. Either equivalent, subeq, superclass, subclass. Default is subeq', args: 1
      _ longOpt: 'override-group', 'Override group in labels output with given text', args: 1
      _ longOpt: 'priority', 'RegexNER priority in output. Default is 1.', args: 1
      _ longOpt: 'expand-synonyms', 'Expand synonyms using AberOWL', type: Boolean

      // annotation options
      t longOpt: 'text', 'A file or directory of files to annotate.', args: 1
      l longOpt: 'labels', 'Annotation labels file.', args: 1
      _ longOpt: 'file-list', 'A file containing a list of strings file names in the directory given by -t/--text must contain to be annotated (e.g. pmcids)', args: 1
      _ longOpt: 'per-line', 'Process each line of each file seperately (useful for field-based data e.g. downloaded with get_metadata)', type: Boolean
      _ longOpt: 'disable-modifiers', 'Don\'t evaluate negation and uncertainty. The reason for this is: it takes a lot of time!', type: Boolean
      _ longOpt: 'family-modifier', 'Evaluate sentences for whether or not they mention a family member.', type: Boolean
      _ longOpt: 'exclude', 'A list of phrases, which when matched in a sentence, will cause that sentence not to be annotated. One phrase per line.', args: 1

      // summary options
      a longOpt: 'annotation-file', 'Annotation file to summarise', args: 1

      // auto option
      r longOpt: 'roster', 'The file to get the automatic order list from.', args: 1

      // genroster
      _ longOpt: 'with-abstracts-download', 'Generate a roster that downloads abstracts from PubMed Central.', type: Boolean
      _ longOpt: 'with-metadata-download', 'Generate a roster that downloads metadata for classes.', type: Boolean
      _ longOpt: 'mine-relationship', 'Generate a roster that mines text for a relationship between entities.', type: Boolean

      // genroster suggestaxiom options
      _ longOpt: 'default-relation', 'Default relation to use for suggest_axiom', args: 1
      _ longOpt: 'default-entity', 'Default entity to use for suggest_axiom', args: 1

      _ longOpt: 'suggest-axiom', 'Generate a roster that suggests an axiom for a class'
      _ longOpt: 'entity', 'Entity to query for (class name).', args: 1
      _ longOpt: 'quality', 'Quality to query for (class name).', args: 1
      eo longOpt: 'entity-ontology', 'Ontology to query for entities (if not included will use same as class query)', args: 1
      qo longOpt: 'quality-ontology', 'Ontology to query for qualities (if not included will use the same as class query)', args: 1
      oo longOpt: 'object-property-ontology', 'Ontology to get object properties from (if not included will use the same as class query)', args: 1

      // get_articles option (some also apply to get_metadata)
      _ longOpt: 'limit', 'Limit articles download', args: 1, type: Integer
      _ longOpt: 'group-by-query', 'Group classes by query (rather than URI)', type: Boolean
      _ longOpt: 'conjunction', 'Use a conjunctive query to obtain articles', type: Boolean
      _ longOpt: 'exclude-groups', 'Comma delimited list of groups to ignore for download query', args: 1
      _ longOpt: 'id-list-only', 'Only download a file list of', type: Boolean
      _ longOpt: 'lemmatise', 'Lemmatise the downloaded information. Can be used on query, download_metadata, download_abstracts', type: Boolean
      _ longOpt: 'decompose-entities', 'If the label of an entity (group class in the label file) appears in a string, decompose it in a new label', type: Boolean
      _ longOpt: 'count-only', 'Only provide the hit counts for the articles. This does not suffer from the article limit that downloads do!', type: Boolean

      // diagnose options
      _ longOpt: 'by-group', 'Group items for diagnosis by the query group, rather than by term IRI', type: Boolean

      // all options
      _ longOpt: 'out', 'Where to write the annotation results.', args: 1
      _ longOpt: 'append', 'Append output file, instead of replacing it', type: Boolean
      _ longOpt: 'verbose', 'Verbose output, mostly progress', type: Boolean
      _ longOpt: 'threads', 'Number of threads to use for query/annotation processes', type: Integer, args: 1
    }

    Komenti.run(cliBuilder, args)
  }
}