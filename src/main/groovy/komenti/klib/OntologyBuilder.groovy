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
     
    def hasSpecifier = factory.getOWLObjectProperty(IRI.create(oIRI + (++tCount)))
    
    OWLOntology ontology = manager.createOntology(IRI.create("oIRI"))

    manager.addAxiom(ontology, factory.getOWLTransitiveObjectPropertyAxiom(hasPart))
    manager.addAxiom(ontology, factory.getOWLTransitiveObjectPropertyAxiom(partOf))
    manager.addAxiom(ontology, factory.getOWLReflexiveObjectPropertyAxiom(hasPart))
    manager.addAxiom(ontology, factory.getOWLReflexiveObjectPropertyAxiom(partOf))

    def addedTerms = [:]
    def addClass = { iri, label, relation ->
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

      return cClass
    }
    def makeOrGetClass
    makeOrGetClass = { t, relation ->
      println "processing $t"

      def label = t.getLabel()
      def oClass
      if(t.iri == 'UNMATCHED_CONCEPT') {
        if(addedTerms.containsKey(label)) {
          t.iri = addedTerms[label]
        } else {
          t.iri = prefix + (++tCount)
        }

        addClass(t.iri, label, relation)
        addedTerms[label] = t.iri         

        if(relation) {
          oClass = factory.getOWLObjectProperty(IRI.create(t.iri))
        } else {
          oClass = factory.getOWLClass(IRI.create(t.iri))
        }

        if(t.parentTerm) {
          // we also have to create specifier
          if(!addedTerms[t.getSpecificLabel()]) {
            addedTerms[t.getSpecificLabel()] = prefix + (++tCount)
            addClass(addedTerms[t.getSpecificLabel()], t.getSpecificLabel(), relation)
          }
          def specifierClass = factory.getOWLClass(IRI.create(addedTerms[t.getSpecificLabel()]))

          if(!relation) { // TODO add subrelationof
            manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(
              oClass, factory.getOWLObjectIntersectionOf(
                makeOrGetClass(t.parentTerm, relation),
                factory.getOWLObjectSomeValuesFrom(
                  hasSpecifier,
                  specifierClass
                )
              )))
          }
        }
      } else {
        if(!addedTerms.containsKey(t.getSpecificLabel())) {
          addClass(t.iri, t.getSpecificLabel(), relation)
          addedTerms[t.getSpecificLabel()] = t.iri         
        }

        if(relation) {
          oClass = factory.getOWLObjectProperty(IRI.create(t.iri))
        } else {
          oClass = factory.getOWLClass(IRI.create(t.iri))
        }

        if(t.parentTerm) { makeOrGetClass(t.parentTerm, relation) }
      }

      
      println ''

      oClass
    }
    triples.each { 
      def subject = makeOrGetClass(it.subject, false)
      def relation = makeOrGetClass(it.relation, true)
      def object = makeOrGetClass(it.object, false)

      manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(
        subject, factory.getOWLObjectSomeValuesFrom(
          relation, object) 
      ))
    }

    manager.saveOntology(ontology, IRI.create(new File(o['out']).toURI()))
  }
}
