package komenti

import spock.lang.Specification
import spock.lang.Shared

import groovy.json.*

import klib.*

class AppTest extends Specification {
  @Shared buffer
  @Shared outFile

  def setupSpec() {
    buffer = new ByteArrayOutputStream()
    System.out = new PrintStream(buffer)
    outFile = 'annotation_file.txt'
  }

  def toArg(q) { // thanks i hate it
    q.toArray(new String[0])
  }

  def resourceToPass(r) {
    getClass().getResource(r).toURI().toString().replace('file:','')
  }

  def "startup"() {
    when:
      App.main("--help")
    then: 
      buffer.toString().indexOf("usage: komenti <command> [<options>]") != -1
  }

  def "query"() {
    when:
      def labelFile = "labels.txt"
      def q = ["query", "-q", "'part of' some 'apoptotic process'", "-o", "GO", "--out", labelFile]
      App.main(toArg(q))
    then:
      buffer.toString() =~ "Saved \\d+ labels for \\d+ terms to $labelFile"
    then:
      new File(labelFile).exists()
  }

  def "check_query_output"() {
    true
  }

  def "annotate"() {
    given: 
      def labelFile = resourceToPass('/go_labels_test.txt')
      def textFile = resourceToPass('/annotate_this.txt')
    when:
      def q = ["annotate", "-l", labelFile, "-t", textFile, "--out", outFile, "--threads", "1"]
      App.main(toArg(q))
    then:
      buffer.toString() =~ "Annotation complete"
    then:
      new File(outFile).exists()
  }

  // TODO check for another sentence with a different matchText, tags
  def "check_annotate_output"() {
    given:
      def anns = AnnotationList.loadFile(outFile)
    expect:
      anns.size() == 6
      anns[0].documentId == "annotate_this.txt"
      anns[0].termIri == "http://purl.obolibrary.org/obo/GO_0006309"
      anns[0].conceptLabel == "apoptotic dna fragmentation"
      anns[0].matchedText == "apoptotic dna fragmentation"
      anns[0].group == "'part of' some 'apoptotic process'"
      anns[0].tags.size() == 0
      anns[0].sentenceId == "1"
      anns[0].text == "apoptotic dna fragmentation is a key feature of apoptosis, a type of programmed cell death."
      anns.findAll { it.tags.contains('negated') }.size() == 1
  }

  def "extract_triples"() {
    given: 
      def labelFile = resourceToPass('/go_triple_vocab.txt')
      def textFile = resourceToPass('/annotate_this.txt')
    when:
      def q = ["annotate", "-l", labelFile, "-t", textFile, "--out", outFile, "--threads", "1", "--extract-triples", "--allow-unmatched-relations"]
      App.main(toArg(q))
    then:
      buffer.toString() =~ "Annotation complete"
    then:
      new File(outFile).exists()
  }

  def "check_extract_triples_output"() {
    given:
      def anns = new JsonSlurper().parse(new File(outFile))
    expect:
      anns.size() == 1

      anns[0].subject.originalAnnotation.documentId == 'annotate_this.txt_1'
      anns[0].subject.originalAnnotation.sentenceId == 1
      anns[0].subject.originalAnnotation.matchedText == 'apoptotic dna fragmentation'
      anns[0].subject.originalAnnotation.text == 'apoptotic dna fragmentation is a key feature of apoptosis, a type of programmed cell death.'

      anns[0].relation.label == 'key feature of'
      anns[0].relation.iri == 'UNMATCHED_CONCEPT'
      !anns[0].relation.originalAnnotation
  }

  def "create-triple-ontology"() {
    given:
      def tFile = resourceToPass('/triples.json')
      def outPath = 'ontology.owl'
    when:
      def q = ["ontologise", "--triples", tFile, "--out", outPath ]
      App.main(toArg(q))
    then:
      new File(outPath).exists()
  }
}
