package klib

// For now we'll leave the question of how exactly it will be implemented in OWL elsewhere, but it's probably either going to be subclassof or o and r some q 
class SpecifierTerm extends Term {
  Term parentTerm 

  // Little bit of a clunky way of doing it
  SpecifierTerm(Term parentTerm, Term specifiedTerm) {
    super(specifiedTerm.iri, specifiedTerm.label)
    this.parentTerm = parentTerm
  }
}
