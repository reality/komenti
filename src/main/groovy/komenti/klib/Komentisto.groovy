package klib

import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.*
import edu.stanford.nlp.semgraph.*
import edu.stanford.nlp.ie.util.RelationTriple 
import edu.stanford.nlp.util.*
import edu.stanford.nlp.naturalli.*

public class Komentisto {
  def REP_TOKEN = 'biscuit'
  def UNC_WORDS_FILE = getClass().getResourceAsStream('/words/uncertain.txt')
  def FAM_WORDS_FILE = getClass().getResourceAsStream('/words/family.txt')
  def ALLERGY_PATTERN = "allerg" // should be fine

  def coreNLP
  def uncertainTerms
  def familyTerms
  def excludeTerms = []
  def disableModifiers
  def familyModifier
  def allergyModifier
  def enableIE
  def vocabulary
  def threads

  def Komentisto(vocabulary, disableModifiers, familyModifier, allergyModifier, enableIE, excludeFile, threads) {
    this.vocabulary = vocabulary
 
    uncertainTerms = UNC_WORDS_FILE.getText().split('\n')
    familyTerms = FAM_WORDS_FILE.getText().split('\n')
    if(excludeFile) {
      excludeTerms = new File(excludeFile).text.split('\n').collect { it.toLowerCase() }  
    }

    // TODO replace with MapConstructor
    this.disableModifiers = disableModifiers
    this.familyModifier = familyModifier
    this.allergyModifier = allergyModifier
    this.enableIE = enableIE
    this.threads = threads

    initialiseCoreNLP()
  }

  def initialiseCoreNLP() {
    def props = new Properties()

    def aList = ["tokenize", "ssplit", "pos", "lemma", "ner", "regexner", "entitymentions", "depparse"]
    if(disableModifiers) {
      aList.remove("depparse")
    }
    if(enableIE) { aList += ["natlog", "openie"] }
    println aList
    props.put("annotators", aList.join(', '))

    props.put("ner.useSUTime", "false")
    props.put("parse.maxtime", "5000")

    props.put("regexner.mapping", new File(vocabulary.labelPath).getAbsolutePath())
    props.put("regexner.mapping.header", "pattern,ner,q,ontology,priority") // wtf
    props.put("regexner.mapping.field.q", 'edu.stanford.nlp.ling.CoreAnnotations$NormalizedNamedEntityTagAnnotation') // wtf
    props.put("regexner.mapping.field.ontology", 
      'edu.stanford.nlp.ling.CoreAnnotations$NormalizedNamedEntityTagAnnotation') // wtf
    props.put("regexner.ignorecase", "true")

    props.put("regexner.ignorecase", "true")
    props.put("depparse.nthreads", threads)
    props.put("ner.nthreads", threads)
    props.put("parse.nthreads", threads)
    coreNLP = new StanfordCoreNLP(props)
  }

  def annotate(id, text) {
    annotate(id, text, 0)
  }

  def annotate(id, text, sentenceCount) {
    def aDocument = new edu.stanford.nlp.pipeline.Annotation(text.toLowerCase())
    coreNLP.annotate(aDocument)

    def results = []
    aDocument.get(CoreAnnotations.SentencesAnnotation.class).each { sentence ->
      sentenceCount++

      for(entityMention in sentence.get(CoreAnnotations.MentionsAnnotation.class)) {
        def ner = entityMention.get(CoreAnnotations.NamedEntityTagAnnotation.class)
        if(!vocabulary.entities.containsKey(ner)) { // Fix for inability to overwrite internal tags ... WTF
          ner = vocabulary.labelIri(entityMention.toString())
        }
        if(ner && vocabulary.entities.containsKey(ner)) {
          def a = new Annotation(
            documentId: id,
            termIri: ner,
            conceptLabel: vocabulary.termLabel(ner),
            matchedText: entityMention.toString(),
            group: vocabulary.termGroup(ner),
            tags: [],
            sentenceId: sentenceCount,
            text: sentence.toString()
          ) 

          if(excludeTerms.any { a.text =~ it }) { continue; }

          if(!disableModifiers) {
            def tags = evaluateSentenceConcept(sentence, ner) // add all tags that returned true
            a.tags = tags.findAll { it.getValue() }.collect { it.getKey() }
          }

          results << a
        }
      }
    }

    results
  }

  def extractTriples(id, text) { extractTriples(id, text, 0) }

  def extractTriples(id, text, sentenceCount) {
    def aDocument = new edu.stanford.nlp.pipeline.Annotation(text.toLowerCase())
    coreNLP.annotate(aDocument)

    def allTriples = []

    
    // Loop over sentences in the document
    aDocument.get(CoreAnnotations.SentencesAnnotation.class).each { sentence ->
      sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class).each{ triple ->
        allTriples << triple
        System.out.println(triple.confidence + "\t" +
            triple.subjectLemmaGloss() + "\t" +
            triple.relationLemmaGloss() + "\t" +
            triple.objectLemmaGloss());

        def subject = triple.subjectLemmaGloss().toString()
        def relation = triple.relationLemmaGloss().toString()
        def object = triple.objectLemmaGloss().toString()

        def subjectAnn = annotate(id, subject).findAll {
          it.group == 'terms' && it.matchedText.size() == subject.size()
        }

        def relationAnn = annotate(id, relation).findAll { 
          it.group == 'object-properties' && it.matchedText.size() == relation.size()
        }
        
        def objectAnn = annotate(id, object).findAll { 
          it.group == 'terms' && it.matchedText.size() == object.size()
        }

        println subjectAnn
        println relationAnn
        println objectAnn

        if(subjectAnn && relationAnn && objectAnn) {
          println 'YEAH!'
        }

        println ''

      }
    }

    allTriples
  }

  // Evaluate for negation and uncertainty
  def evaluateSentenceConcept(sentence, concept) {
    def text = sentence.toString()
    def klSentence = new Sentence(text, 0) // placeholder zero, no purpose
    klSentence.genTypeDeps(coreNLP, vocabulary.entityLabels(concept), REP_TOKEN) 

    def out = [
      negated: klSentence.isNegated([REP_TOKEN]),
      uncertain: klSentence.isUncertain([REP_TOKEN], uncertainTerms)
    ]

    if(familyModifier) {
      out.family = sentence.get(CoreAnnotations.TokensAnnotation.class).collect {
        [it.toString(), it.lemma()]
      }.flatten().any {
        familyTerms.contains(it) 
      }
    }

    if(allergyModifier) {
      out.allergy = text =~ ALLERGY_PATTERN
    }

    out
  }

  def lemmatise(text) {
    def aDocument = new Annotation(text.toLowerCase())
    coreNLP.annotate(aDocument)

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

      s.collect { m.stem(it) ?: it }.join(' ')
    }.findAll { it.size() > 3 }.unique(false)
  }
}
