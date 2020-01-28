package klib

import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.*
import edu.stanford.nlp.semgraph.*

public class Komentisto {
  def REP_TOKEN = 'biscuit'
  def UNC_WORDS_FILE = './words/uncertain.txt'

  def advancedCoreNLP
  def basicPipeline
  def entities
  def uncertainTerms
  def disableModifiers

  def Komentisto(labelFilePath, disableModifiers) {
    def labelFile = new File(labelFilePath)

    entities = [:]
    labelFile.text.split('\n').each { 
      it = it.split('\t')
      if(!entities.containsKey(it[1])) { entities[it[1]] = [] }
      entities[it[1]] << it[0].toLowerCase()
    }

    uncertainTerms = new File(UNC_WORDS_FILE).text.split('\n')
    
    def props = new Properties()


    if(!disableModifiers) {
      props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, entitymentions, depparse")
    } else {
      props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, entitymentions")
    }

    props.put("parse.maxtime", "20000")
    addRegexNERProps(props, labelFile)
    props.put("regexner.ignorecase", "true")
    props.put("depparse.nthreads", 8)
    props.put("ner.nthreads", 8)
    props.put("parse.nthreads", 8)
    advancedCoreNLP = new StanfordCoreNLP(props)

    props = new Properties()
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, entitymentions")
    addRegexNERProps(props, labelFile)

    def basicCoreNLP = new StanfordCoreNLP(props)
    basicPipeline = new AnnotationPipeline()
    basicPipeline.addAnnotator(basicCoreNLP)

    this.disableModifiers = disableModifiers
  }

  def addRegexNERProps(props, labelFile) { // i feel like it should be easier than this to make custom rows. some kind of 'ignore, or N/A' header, perhaps
    props.put("regexner.mapping", labelFile.getAbsolutePath())
    props.put("regexner.mapping.header", "pattern,ner,q,ontology,priority") // wtf
    props.put("regexner.mapping.field.q", 'edu.stanford.nlp.ling.CoreAnnotations$NormalizedNamedEntityTagAnnotation') // wtf
    props.put("regexner.mapping.field.ontology", 
      'edu.stanford.nlp.ling.CoreAnnotations$NormalizedNamedEntityTagAnnotation') // wtf
    props.put("regexner.ignorecase", "true")
  }

  def annotate(id, text) {
    annotate(id, text, 0)
  }

  def annotate(id, text, sentenceCount) {
    def aDocument = new Annotation(text.toLowerCase())
    basicPipeline.annotate(aDocument)

    def results = []
    aDocument.get(CoreAnnotations.SentencesAnnotation.class).each { sentence ->
      sentenceCount++

      for(entityMention in sentence.get(CoreAnnotations.MentionsAnnotation.class)) {
        def ner = entityMention.get(CoreAnnotations.NamedEntityTagAnnotation.class)
        if(entities.containsKey(ner)) {
          def a = [ f: id, c: ner, tags: [], text: sentence.toString(), sid: sentenceCount ]

          if(!disableModifiers) {
            def tags = evaluateSentenceConcept(sentence.toString(), ner)
            if(tags.negated) { a.tags << 'negated' }
            if(tags.uncertain) { a.tags << 'uncertain' }
          }

          results << a
        }
      }
    }

    results
  }

  // Evaluate for negation and uncertainty
  def evaluateSentenceConcept(text, concept) {
    def klSentence = new Sentence(text, 0) // placeholder zero, no purpose
    klSentence.genTypeDeps(advancedCoreNLP, entities[concept], REP_TOKEN) 

    [
      negated: klSentence.isNegated([REP_TOKEN]),
      uncertain: klSentence.isUncertain([REP_TOKEN], uncertainTerms)
    ]
  }

  def lemmatise(text) {
    def aDocument = new Annotation(text.toLowerCase())
    basicPipeline.annotate(aDocument)

    def newText = ''
    aDocument.get(CoreAnnotations.SentencesAnnotation.class).each { sentence ->
      newText += sentence.get(CoreAnnotations.TokensAnnotation.class).collect {
        it.lemma()
      }.join(' ') + ' '
    }

    newText = newText.replaceAll(' ,', ',')
    newText = newText.replaceAll(' \\.', '.')

    newText
  }

  static def getLemmas(labels) {
    def m = new edu.stanford.nlp.process.Morphology()
    labels.collect { l ->
      def s = l.split(' ')
      if(s[0] == 'toxicity') {
        s[0] = 'toxic'
      } 

      s.collect { m.stem(it) }.join(' ')
    }
  }
}
