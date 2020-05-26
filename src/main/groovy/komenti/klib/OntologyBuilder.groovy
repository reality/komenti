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
    def addClass = { iri, label ->
      def cClass = factory.getOWLClass(IRI.create(iri))
      def anno = factory.getOWLAnnotation(
           factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
           factory.getOWLLiteral(label))
      def axiom =  factory.getOWLAnnotationAssertionAxiom(cClass.getIRI(), anno)
      manager.addAxiom(ontology, axiom)
      return cClass
    }
    def makeOrGetClass
    makeOrGetClass = { t ->
      def label = t.getLabel()
      if(t.iri == 'UNMATCHED_CONCEPT') {
        if(addedTerms.containsKey(label)) {
          t.iri = addedTerms[label]
        } else {
          t.iri = prefix + (++tCount)
        }
      }

      def oClass = factory.getOWLClass(IRI.create(t.iri))
      if(!addedTerms.containsKey(t.iri)) {
        addClass(t.iri, label)
        addedTerms[label] = t.iri         
      }

      if(t.parentTerm) {
        // we also have to create specifier
        def specifierClass = addClass(prefix + (++tCount), t.label)

        manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(
          oClass, factory.getOWLObjectIntersectionOf(
            makeOrGetClass(t.parentTerm),
            factory.getOWLObjectSomeValuesFrom(
              hasSpecifier,
              specifierClass
            )
          )))
      }

      oClass
    }
    triples.each { 
      def subjectIri = makeOrGetClass(it.subject)
      def relationIri = makeOrGetClass(it.relation)
      def objectIri = makeOrGetClass(it.object)

      /*manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(
        subjectIri, factory.getOWLObjectSomeValuesFrom(
          relationIri, objectIri) 
      )) */ 
    }

    manager.saveOntology(ontology, IRI.create(new File(o['out']).toURI()))
  }
}
