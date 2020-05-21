package klib

import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.*
import edu.stanford.nlp.semgraph.*

public class Komentisto {
  def REP_TOKEN = 'biscuit'
  def UNC_WORDS_FILE = getClass().getResourceAsStream('/words/uncertain.txt')
  def FAM_WORDS_FILE = getClass().getResourceAsStream('/words/family.txt')
  def ALLERGY_PATTERN = "allerg" // should be fine

  def advancedCoreNLP
  def basicPipeline
  def uncertainTerms
  def familyTerms
  def excludeTerms = []
  def disableModifiers
  def familyModifier
  def allergyModifier
  def vocabulary

  def Komentisto(vocabulary, disableModifiers, familyModifier, allergyModifier, excludeFile, threads) {
    this.vocabulary = vocabulary
 
    uncertainTerms = UNC_WORDS_FILE.getText().split('\n')
    familyTerms = FAM_WORDS_FILE.getText().split('\n')
    if(excludeFile) {
      excludeTerms = new File(excludeFile).text.split('\n').collect { it.toLowerCase() }  
    }
    
    def props = new Properties()

    if(!disableModifiers) {
      props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, entitymentions, depparse")
    } else {
      props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, entitymentions")
    }

    props.put("ner.useSUTime", "false")
    props.put("parse.maxtime", "5000")
    addRegexNERProps(props)

    props.put("regexner.ignorecase", "true")
    props.put("depparse.nthreads", threads)
    props.put("ner.nthreads", threads)
    props.put("parse.nthreads", threads)
    advancedCoreNLP = new StanfordCoreNLP(props)

    props = new Properties()
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner, entitymentions")
    addRegexNERProps(props)

    def basicCoreNLP = new StanfordCoreNLP(props)
    basicPipeline = new AnnotationPipeline()
    basicPipeline.addAnnotator(basicCoreNLP)

    this.disableModifiers = disableModifiers
    this.familyModifier = familyModifier
    this.allergyModifier = allergyModifier
  }

  def addRegexNERProps(props) {
    props.put("regexner.mapping", new File(vocabulary.labelPath).getAbsolutePath())
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
    // Due to clash with klib.Annotation. Not sure what the best way to solve it is
    //  (although realistically I suppose the Stanford folk probably have a bit more
    //  of a claim to the Annotation name than I)
    def aDocument = new edu.stanford.nlp.pipeline.Annotation(text.toLowerCase())
    basicPipeline.annotate(aDocument)

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
            matchedText: entityMention,
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

  // Evaluate for negation and uncertainty
  def evaluateSentenceConcept(sentence, concept) {
    def text = sentence.toString()
    def klSentence = new Sentence(text, 0) // placeholder zero, no purpose
    klSentence.genTypeDeps(advancedCoreNLP, vocabulary.entityLabels(concept), REP_TOKEN) 

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

      s.collect { m.stem(it) ?: it }.join(' ')
    }.findAll { it.size() > 3 }
  }
}
