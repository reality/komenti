package komenti

import spock.lang.Specification
import spock.lang.Shared

import klib.*

class KomentistoTest extends Specification {
  @Shared komentisto

  def setupSpec() {
    def testFile = getClass().getResource('/go_triple_vocab.txt').toURI()
    def vocabulary = Vocabulary.loadFile(testFile)

    komentisto = new Komentisto(vocabulary, 
      false,
      false,
      false,
      true,
      false,
      1)
  }

  def "extract_triples"() {
    given:
      def aFile = getClass().getResource('/annotate_this.txt').toURI()
      def aText = new File(aFile).text
    when:
      def out = komentisto.extractTriples(0, aText)
    then:
      println out
  }
}
