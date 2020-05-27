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
  static def build(TermTripleList triples, o) {
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

    def makeOrGetClass
    makeOrGetClass = { t, type ->
      println "processing $t"

      def label = t.getLabel()
      def oClass
      if(t.iri == 'UNMATCHED_CONCEPT') {
        if(addedTerms.containsKey(label)) {
          t.iri = addedTerms[type][label]
        } else {
          t.iri = prefix + (++tCount)
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
          if(!addedTerms[type][t.getSpecificLabel()]) {
            addedTerms[type][t.getSpecificLabel()] = prefix + (++tCount)
            addClass(addedTerms[type][t.getSpecificLabel()], t.getSpecificLabel(), 
              type == 'rl', specifierParent)
          }
          def specifierClass = factory.getOWLClass(IRI.create(addedTerms[type][t.getSpecificLabel()]))

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
        if(!addedTerms[type].containsKey(t.getSpecificLabel())) {
          addClass(t.iri, t.getSpecificLabel(), type == 'rl', observedParent)
          addedTerms[type][t.getSpecificLabel()] = t.iri         
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
    triples.each { 
      def subject = makeOrGetClass(it.subject, 'cl')
      def relation = makeOrGetClass(it.relation, 'rl')
      def object = makeOrGetClass(it.object, 'cl')

      manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(
        subject, factory.getOWLObjectSomeValuesFrom(
          relation, object)
      ))
    }

    manager.saveOntology(ontology, IRI.create(new File(o['out']).toURI()))
  }
}
