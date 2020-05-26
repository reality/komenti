package klib

class TermTriple {
  Term subject
  Term relation
  Term object

  String toString() {
    subject.toString() + " -> " + relation.toString() + " -> " + object.toString()
  }
}
