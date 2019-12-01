package klib

import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.*
import edu.stanford.nlp.semgraph.*

public class Komentisto {
  def REP_TOKEN = 'biscuit'
  def UNC_WORDS_FILE = './words/uncertain.txt'

  def pipeline
  def entities
  def coreNLP
  def uncertainTerms

  def Komentisto(labelFilePath) {
    def labelFile = new File(labelFilePath)

    entities = [:]
    labelFile.text.split('\n').each { 
      it = it.split('\t')
      if(!entities.containsKey(it[1])) { entities[it[1]] = [] }
      entities[it[1]] << it[0].toLowerCase()
    }

    uncertainTerms = new File(UNC_WORDS_FILE).text.split('\n')
    
    def props = new Properties()
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, entitymentions, depparse")
    props.put("parse.maxtime", "20000")
    props.put("regexner.mapping", labelFile.getAbsolutePath())
    props.put("regexner.ignorecase", "true")
    props.put("depparse.nthreads", 8)
    props.put("ner.nthreads", 8)
    props.put("parse.nthreads", 8)

    coreNLP = new StanfordCoreNLP(props)
    pipeline = new AnnotationPipeline()

    pipeline.addAnnotator(coreNLP)
  }

  def annotate(id, text) {
    def aDocument = new Annotation(text.toLowerCase())
    pipeline.annotate(aDocument)

    def results = []
    def sentenceCount = 0
    aDocument.get(CoreAnnotations.SentencesAnnotation.class).each { sentence ->
      def foundInThisSentence = []
      sentenceCount++

      for(entityMention in sentence.get(CoreAnnotations.MentionsAnnotation.class)) {
        def ner = entityMention.get(CoreAnnotations.NamedEntityTagAnnotation.class)
        if(entities.containsKey(ner) && !foundInThisSentence.contains(ner)) {
          def a = [ f: id, c: ner, tags: [], text: sentence.toString(), sid: sentenceCount ]

          def klSentence = new Sentence(sentence.toString(), id)
          klSentence.genTypeDeps(coreNLP, entities[ner], REP_TOKEN) 

          if(klSentence.isNegated([REP_TOKEN])) { a.tags << 'negated' }
          if(klSentence.isUncertain([REP_TOKEN], uncertainTerms)) { a.tags << 'uncertain' }

          results << a
        }
      }
    }

    results
  }

  static def getLemmas(labels) {
    def m = new edu.stanford.nlp.process.Morphology()
    labels.collect { l ->
      def s = l.split(' ')
      if(s[0] == 'toxicity') {
        s[0] = 'toxic'
      } 
      s = s.filter { it != 'nanoparticle' }

      s.collect { m.stem(it) }.join(' ')
    }
  }
}
