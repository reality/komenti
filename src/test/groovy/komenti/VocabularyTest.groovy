package komenti

import spock.lang.Specification
import spock.lang.Shared

import klib.*

class VocabularyTest extends Specification {
  @Shared vocabulary

  def "load_vocabulary"() {
    given:
      def testFile = getClass().getResource('/go_labels_test.txt').toURI()
    when:
      vocabulary = Vocabulary.loadFile(testFile)
    then:
      vocabulary instanceof Vocabulary
  }

  def "get label"() {
    given:
      def iri = "http://purl.obolibrary.org/obo/GO_0036483"
      def expectedLabel = "endoplasmic reticulum stress-induced neuron apoptosis"
      def unexpectedLabel = "biscuit juice"
    when:
      def eLabels = vocabulary.entityLabels(iri)
    then:
      eLabels.size() == 5
    then:
      eLabels.contains(expectedLabel)
    then:
      !eLabels.contains(unexpectedLabel)
  }
}
