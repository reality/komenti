package klib

import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary
import org.apache.commons.lang.SerializationUtils

class OntologyBuilder {
  static def build(TermTripleList triples, Vocabulary vocabulary, o) {
    def manager = OWLManager.createOWLOntologyManager()
    def factory = manager.getOWLDataFactory()

    def oIRI = 'http://reality.rehab/ontologies/LOO'
    def prefix = oIRI + '_'
    def tCount = 0

    def partOf = factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"))
    def hasPart = factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000051"))
    
    OWLOntology ontology = manager.createOntology(IRI.create(oIRI))

    manager.addAxiom(ontology, factory.getOWLTransitiveObjectPropertyAxiom(hasPart))
    manager.addAxiom(ontology, factory.getOWLTransitiveObjectPropertyAxiom(partOf))
    manager.addAxiom(ontology, factory.getOWLReflexiveObjectPropertyAxiom(hasPart))
    manager.addAxiom(ontology, factory.getOWLReflexiveObjectPropertyAxiom(partOf))

    def addedTerms = [
      cl: [:],
      rl: [:]
    ]
    def addClass = { iri, label, type, parent ->
      def cClass = factory.getOWLClass(IRI.create(iri))
      if(type == 'rl') {
        cClass = factory.getOWLObjectProperty(IRI.create(iri))
      }

      if(!addedTerms[type][label]) {
        //println "adding $label with $iri"

        def anno = factory.getOWLAnnotation(
             factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
             factory.getOWLLiteral(label))
        def axiom =  factory.getOWLAnnotationAssertionAxiom(cClass.getIRI(), anno)
        manager.addAxiom(ontology, axiom)

        if(parent) { // may have to check iff owlclass==parent or whatever
          if(type == 'cl') {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(
              cClass, parent))
          } else {
            manager.addAxiom(ontology, factory.getOWLSubObjectPropertyOf(
              cClass, parent))
          }
        }
      }

      return cClass
    }

    def lookupIRI = { label -> vocabulary.labelIri(label) ?: prefix + (++tCount) }
    def makeOrGetClass
    makeOrGetClass = { t, type ->
      def label = preProcessLabel(t.getLabel())
      def newIRI = addedTerms[type][label]
      if(!newIRI) { newIRI = lookupIRI(label) }

      def oClass 
      if(t.iri == 'UNMATCHED_CONCEPT') { // add completely new class
        oClass = addClass(newIRI, label, type, false)
      } else if(t.label != t.specificLabel) {
        def specParent = addClass(t.iri, t.specificLabel, type, false) // add this to adedTerms?
        oClass = addClass(newIRI, t.label, type, specParent)  
      } else {
        oClass = addClass(t.iri, t.label, type, false)  
      }
      addedTerms[type][label] = newIRI

      if(t.parentTerm) {
        def parentClass = makeOrGetClass(t.parentTerm, type)
        if(type == 'rl') {
          manager.addAxiom(ontology, factory.getOWLSubObjectPropertyOfAxiom(oClass, parentClass))
        } else {
          manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(oClass, parentClass))
        }
      }

      return oClass
    }

    def komentisto = new Komentisto(false, true, false, false, false, false, o['threads'] ?: 1)

    def getPID = { e ->
      def oAnn
      if(e.object.originalAnnotation) {
        oAnn = e.object.originalAnnotation
      }
      if(!oAnn && e.object.parentTerm && e.object.parentTerm.originalAnnotation) {
        oAnn = e.object.parentTerm.originalAnnotation
      }

      if(!oAnn && e.subject.originalAnnotation) {
        oAnn = e.subject.originalAnnotation
      }
      if(!oAnn && e.subject.parentTerm && e.subject.parentTerm.originalAnnotation) {
        oAnn = e.subject.parentTerm.originalAnnotation
      }

      if(!oAnn && e.relation.originalAnnotation) {
        oAnn = e.relation.originalAnnotation
      }
      if(!oAnn && e.relation.parentTerm && e.relation.parentTerm.originalAnnotation) {
        oAnn = e.relation.parentTerm.originalAnnotation
      }

      if(!oAnn) { println 'FUCK' } else {
        return oAnn.documentId.tokenize('.')[0]
      }
    }
      
    triples.each { 
      if(it.relation.iri == 'UNMATCHED_CONCEPT') {
        def vp = komentisto.reduceToVerbPrep(it.relation.label)
        if(vp.verb) {
          it.relation.label = vp.verb

          if(vp.prep) { // basically we add a parentTerm, so we can create a hierarchy like associate->with,in,against etc etc
            def parent = SerializationUtils.clone(it.relation)
            parent.label = vp.verb
            it.relation.parentTerm = parent

            it.relation.label = vp.prep
          }
        }
      }

      //println it.relation

      // so we can make RLs exclusive
      makeOrGetClass(it.relation, 'rl')
    } 
    triples.eachWithIndex { it, i ->  // relation is already there from last time. i know it's naughty to mutate the object like that but i'm a LaZy BaBy

      println "proctrip: $i/${triples.size()}"
      def pmid = getPID(it)
      if(!pmid) { return; }
      def subject = makeOrGetClass(it.subject, 'cl')
      def relation = makeOrGetClass(it.relation, 'rl')
      def object = makeOrGetClass(it.object, 'cl')

      if(subject && object && relation) { // this wouldn't occur if one of our classes has been booted for a relation
        println subject + "\t" + pmid
        println object+ "\t" + pmid

        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(
          subject, factory.getOWLObjectSomeValuesFrom(
            relation, object)
        ))
      }
    }

    manager.saveOntology(ontology, IRI.create(new File(o['out']).toURI()))
  }

  static def preProcessLabel(label) {
    label.replaceAll("^of\\W", "")
         .replaceAll("^in\$", "observe in")
         .replaceAll(".*role in.*", 'role in')
         .replaceAll('.*observe in.*', 'observed in')
         .replaceAll('.*accompany.*', 'accompany')
         .replaceAll('accumulation', 'accumulate')
         .replaceAll('^also\\W', '')
         .replaceAll('^apparently\\W', '')
         .replaceAll('means of', '')
         .replaceAll('essentially', '')
         .replaceAll('^can\\W', '')
         .replaceAll('^clearly\\W', '')
         .replaceAll('^closely\\W', '')
         .replaceAll('^completely\\W', '')
         .replaceAll('^could\\W', '')
         .replaceAll('^eventually\\W', '')
         .replaceAll('^exhibit\\W', '')
         .replaceAll('^first\\W', '')
         .replaceAll('^finally\\W', '')

         .replaceAll('largely\\W', '')
         .replaceAll('completely\\W', '')
         .replaceAll('show\\W', '')
         .replaceAll('also\\W', '')
         .replaceAll('significantly\\W', '')

         .replaceAll('inhibition\\W', 'inhibit')

         .replaceAll('^crucial\\W', 'necessary')
         .replaceAll('^critical\\W', 'necessary')
         .replaceAll('^essential\\W', 'necessary')
         .replaceAll('^need\\W', 'necessary')

         .replaceAll('^furthermore\\W', '')
         .replaceAll('^have\\W', '')
         .replaceAll('^however\\W', '')
         .replaceAll('^important\\W', '')
         .replaceAll('^independently\\W', '')
         .replaceAll('^indeed\\W', '')
         .replaceAll('^key\\W', '')
         .replaceAll('^may\\W', '')
         .replaceAll('^might\\W', '')
         .replaceAll('^play\\W', '')
         .replaceAll('^potential\\W', '')
         .replaceAll('^possible\\W', '')
         .replaceAll('^possibly\\W', '')
         .replaceAll('^probably\\W', '')
         .replaceAll('^rarely\\W', '')
         .replaceAll('^specific\\W', '')
         .replaceAll('^specifically\\W', '')
         .replaceAll('^strongly\\W', '')

         .replaceAll("^in\\W", "")
  }
}
