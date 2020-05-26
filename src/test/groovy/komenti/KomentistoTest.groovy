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
      def aFile = getClass().getResource('/pubmed20n0688_abstract_12336.txt').toURI()
      def aText = new File(aFile).text
    when:
      def out = komentisto.extractTriples("test", aText, true)
    then:
      out.each {
        println it
      }
  }
}
