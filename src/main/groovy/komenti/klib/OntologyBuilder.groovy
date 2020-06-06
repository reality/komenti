package klib

import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary

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
    def addClass = { iri, label, relation, parent ->
      def cClass = factory.getOWLClass(IRI.create(iri))
      if(relation) {
        cClass = factory.getOWLObjectProperty(IRI.create(iri))
      }
      println "adding $label with $iri"

      def anno = factory.getOWLAnnotation(
           factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
           factory.getOWLLiteral(label))
      def axiom =  factory.getOWLAnnotationAssertionAxiom(cClass.getIRI(), anno)
      manager.addAxiom(ontology, axiom)

      if(!relation && parent) {
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(
          cClass, parent))
      }

      return cClass
    }

    def hasSpecifier = addClass(prefix + (++tCount), 'has_specifier', true, factory.getOWLThing())
    def specifierParent = addClass(prefix + (++tCount), 'observation specifier', false, factory.getOWLThing())
    def observedParent = addClass(prefix + (++tCount), 'observed entity', false, factory.getOWLThing())

    def lookupIRI = { label -> vocabulary.labelIri(label) ?: prefix + (++tCount) }

    def makeOrGetClass
    makeOrGetClass = { t, type ->
      println "processing $t"
      def label = preProcessLabel(t.getLabel())
      println 'preprocessed label: ' + label

      // don't overwrite relations with classes
      if(type == 'cl' && addedTerms['rl'][label]) { return; }

      def oClass
      if(t.iri == 'UNMATCHED_CONCEPT') {
        if(addedTerms[type].containsKey(label)) {
          t.iri = addedTerms[type][label]
        } else {
          t.iri = lookupIRI(label)
        }

        if(!t.parentTerm) {
          addClass(t.iri, label, type == 'rl', observedParent)
        } else {
          addClass(t.iri, label, type == 'rl', false)
        }
        addedTerms[type][label] = t.iri         

        if(type == 'rl') {
          oClass = factory.getOWLObjectProperty(IRI.create(t.iri))
        } else {
          oClass = factory.getOWLClass(IRI.create(t.iri))
        }

        if(t.parentTerm) {
          // we also have to create specifier

          // TODO if a relation exists, we can just add an axiom with taht!!! i.e. x and role in some y

          def specificLabel = preProcessLabel(t.getSpecificLabel())
          println "Processed $specificLabel"
          if(!addedTerms[type][specificLabel]) {
            addedTerms[type][specificLabel] = lookupIRI(specificLabel)
            addClass(addedTerms[type][specificLabel], specificLabel, 
              type == 'rl', specifierParent)
          }
          def specifierClass = factory.getOWLClass(IRI.create(addedTerms[type][specificLabel]))

          if(type != 'rl') { // TODO add subrelationof
            def parent = makeOrGetClass(t.parentTerm, type)
            manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(
              oClass, factory.getOWLObjectIntersectionOf(
                parent,
                factory.getOWLObjectSomeValuesFrom(
                  hasSpecifier,
                  specifierClass
                )
              )))
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(
              oClass, parent))
          }
        }
      } else {
        def specificLabel = preProcessLabel(t.getSpecificLabel())

        if(!addedTerms[type].containsKey(specificLabel)) {
          addClass(t.iri, specificLabel, type == 'rl', observedParent)
          addedTerms[type][specificLabel] = t.iri         
        }

        if(type == 'rl') {
          oClass = factory.getOWLObjectProperty(IRI.create(t.iri))
        } else {
          oClass = factory.getOWLClass(IRI.create(t.iri))
        }

        if(t.parentTerm) { makeOrGetClass(t.parentTerm, type) }
      }

      
      println ''

      oClass
    }
      
    triples.each { def relation = makeOrGetClass(it.relation, 'rl') } // so we can make RLs exclusive
    triples.each { 
      def subject = makeOrGetClass(it.subject, 'cl')
      def relation = makeOrGetClass(it.relation, 'rl')
      def object = makeOrGetClass(it.object, 'cl')

      if(subject && object && relation) { // this wouldn't occur if one of our classes has been booted for a relation
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
