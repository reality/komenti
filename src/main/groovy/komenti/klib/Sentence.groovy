package klib

import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.*
import java.util.*
import edu.stanford.nlp.semgraph.*
import org.apache.commons.lang3.RandomStringUtils

class Sentence {
  def id
  def text
  def graph
  def fileName
  def date
  def assertions = [:]
  def contains
  def tText
  def rText
  def nWords
  def negated = null
  def NEG_MODS = [ 'negative', 'exclude', 'without', 'no', 'excluded', 'not', 'denies', 'free', 'deny', 'denied', 'stop', 'stopped' ]

  def Sentence(text, fileName) {
    this.text = text
    this.fileName = fileName
    this.date = date
    this.contains = contains

    def charset = (('a'..'z') + ('A'..'Z') + ('0'..'9')).join()
    id = RandomStringUtils.random(5, charset.toCharArray())
  }

  def isTyped() {
    graph != null
  }

  def burnTypeDeps() {
    graph = null
  }

  def genTypeDeps(tParser) {
    genTypeDeps(tParser, null, null);
  }

  def genTypeDeps(tParser, replaceWords, replaceWith) {
    tText = text

    if(replaceWords && replaceWith) {
      replaceWords.each { replaceWord ->
        tText = tText.replaceAll(replaceWord, replaceWith)
      }
    }

    NEG_MODS.each { n ->
      if(tText.indexOf(replaceWith + ': ' + n) != -1) {
        tText = replaceWith + ' is excluded. '
      }
      if(tText.indexOf(n + ' ' + replaceWith) != -1) {
        tText = replaceWith + ' is excluded. '
      }
    } 

    def aDocument = new edu.stanford.nlp.pipeline.Annotation(tText)
    tParser.annotate(aDocument)

    def sentences = aDocument.get(CoreAnnotations.SentencesAnnotation.class)
    if(sentences && sentences.size() >= 1) {
      rText = sentences[0]
      graph = sentences[0].get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class)
    }
  }

  def mentions(words) {
    words.any { text.indexOf(it) != -1 }
  }

  /**
   * Are any of the words negated in this sentence?
   */
  def isNegated(words) {
    this.nWords = words

    try {
      graph.vertexSet().any {
        def rPath = graph.getPathToRoot(it)
        def rDistance = 0
        def nDistance = 0
        def wR = graph.childPairs(it).collect { z -> z.first().getShortName() }

        def foundNeg = false

        this.negated = (words.contains(it.word())) && (wR.contains('neg') ||
          (graph.relns(it).any { rln -> rln.getSpecific() == 'negcc' || rln.getShortName() == 'neg' } ||
           rPath.any { p ->
              rDistance++
              //if(p.tag().indexOf('N') != -1) { nDistance++ }

              def cP = graph.childPairs(p)
              def cR = cP.collect { z -> 
                z.first().getShortName() 
              }

              def nModFriend = (graph.getChildList(p).collect { c -> c.lemma() } + p.lemma()).any { n -> NEG_MODS.contains(n) }

              rDistance < 4 && (nModFriend || cR.contains('neg'))
           }))

        //println negated
        return negated
      }
    } catch(e) { e.printStackTrace() ; println 'WTF' ; return false }
  }

  def isUncertain(words, uncertainTerms) {
    graph.vertexSet().any {
      def wR = graph.childPairs(it).collect { z -> z.second().word() }

      words.contains(it.word()) && (wR.any { z -> uncertainTerms.contains(z) } || 
        (graph.getPathToRoot(it)).any { p ->
          def rDistance = 0

          (graph.getChildList(p).collect { c -> c.word() } + p.word()).flatten().any { n -> 
            rDistance++

            uncertainTerms.contains(n) && rDistance < 3
          }
      })
    } || words.any { z -> text.indexOf(z + '?') != -1 || text.indexOf(z + ' ?') != -1 }
  }

  def normalise(name, synonyms) {
    synonyms.each {
      text = text.replaceAll(it, name)
    }
  }
}
